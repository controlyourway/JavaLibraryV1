package com.controlyourway;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by alangley on 12/10/15.
 */
public class CywCloudInterface {
    //constants
	private final String defaultInstanceName = "cywCsWebInterface";
    private final int c_minimumDownloadThresholdTime = 5;       //if shorter than this most likely the server was not reached
    private final int c_downloadDecreaseTime = 10;              //when a router terminates a connection then this amount is subtracted from the download expire time
    private final int c_downloadErrorWaitTime = 5;              //if something goes wrong then wait this amount of seconds before starting a new request
    private final int c_downloadSlippageTime = 2;               //add this amount of time for connection to be established
    private final int c_minimumDownloadTimeout = 10;            //minimum amount of time a download timeout can be set to 
    private final int c_maximumDownloadTimeout = 1800;          //minimum amount of time a download timeout can be set to 
    public  final int c_defaultDownloadRequestTimeout = 120;    //default timeout for download request
    private final int c_dataPacket = -1;
    private final int c_requestCredentials = -2;
    private final int c_cancelRequest = -3;
    private final int c_1second = 1000 * 10000;
    private final int c_5seconds = 5000 * 10000;
    private final int c_10seconds = 10000 * 10000;
    private final int c_maxPacketSize = 100000;   //bytes

    //property variables
    private String serverIpAddr;
    private String serverIpAddr2;
    private String serverUrlUpload;
    private String serverUrlDownload;

    //locks
    private Object _waitMasterSetLock = new Object();
    private Object _urlStringsLock = new Object();
    private Object _buildUrlsLock = new Object();
    private Object _SendCancelRequestLock = new Object();

    //event wait handles
    private ManualResetEvent waitToCloud;
    private ManualResetEvent waitFromCloud;
    private ManualResetEvent waitMaster;

    //thread variables
    private Thread threadToCloud;
    private Thread threadFromCloud;
    private Thread threadMaster;
    private Boolean toCloudThreadRunning = false;
    //private bool ToCloudThreadTerminated = true;
    private Boolean fromCloudThreadRunning = false;
    //private bool FromCloudThreadTerminated = true;
    private Boolean masterThreadRunning = false;
    //private bool masterThreadTerminated = true;
	private Boolean restartDownload = false;

    //state machine variables
    private enum statesEnum { RequestCredentials, Running };
    private statesEnum state = statesEnum.RequestCredentials;

    //queues
    private Queue<CywToCloudPacketClass> toCloudQueue = new PriorityQueue<CywToCloudPacketClass>();
    private Queue<CywSendPacketHttpClass> toMasterForCloudQueue = new PriorityQueue<CywSendPacketHttpClass>();
    private Queue<FromToCloudToMasterThreadData> fromToCloudToMasterQueue = new PriorityQueue<FromToCloudToMasterThreadData>();
    private Queue<FromFromCloudToMasterThreadData> fromFromCloudToMasterQueue = new PriorityQueue<FromFromCloudToMasterThreadData>();

    //other variables
    private int fromCloudCnt = 0;   //this variable is sent in the url to make sure I don't get a cached response
    private int toCloudCnt = 0;
    private Boolean closingThreads = false;
    //private bool encryptedSendBusy = false;
    private Boolean serviceRunning = false;
    private Dictionary<String, String> errorCodes = new Hashtable<String, String>();
    private Boolean networksUpdated = false;

    //marshal delegates
    //private delegate void MarshalFromCloudResponse(string data);
    //private delegate void MarshalToCloudResponse(String resp);

    //URLs
    private String downloadUrl = "";
    private String uploadUrl = "";
    private String downloadSslUrl = "";
    private String uploadSslUrl = "";
    private String downloadParams;
    private String uploadParams;

    //event variables - all moved to ConnectionListener
    private List<ConnectionListener> _connectionListeners = new ArrayList<ConnectionListener>();
    public void addConnectionListener(ConnectionListener listener){
        _connectionListeners.add(listener);
    }
    private void debugMessagesEvent(String message){
        for(ConnectionListener listener : _connectionListeners)
            listener.debugMessages(message);
    }

    private void errorEvent(String message){
        for(ConnectionListener listener : _connectionListeners)
            listener.error(message);
    }

    private void connectionStatusEvent(boolean connected){
        for(ConnectionListener listener : _connectionListeners)
            listener.connectionStatusDelegate(connected);
    }

    private void dataReceivedEvent(DataReceivedEvent event){
        for(ConnectionListener listener : _connectionListeners)
            listener.dataReceived(event);
    }

    //public delegate void connectionStatusDelegate(object sender, bool connected);
    //public delegate void dataReceivedDelegate(object sender, byte[] data, string dataType, int fromSessionID);
    //public delegate void debugMessagesDelegate(object sender, string message);
    //public delegate void errorDelegate(object sender, string errorCode);
    //public event connectionStatusDelegate connectionStatusEvent;
    //public event dataReceivedDelegate dataReceivedEvent;
    //public event errorDelegate errorEvent;
    //public event debugMessagesDelegate debugMessagesEvent;

    //properties
    private String userName;
    public String getUserName() { return userName; }

    private List<String> networkNames;
    public List<String> getNetworkNames(){ return networkNames; }

    private String networkPassword;
    public String getNetworkPassword() { return networkPassword; }

    private String deviceId;

    private String sessionId;
    public String getSessionId() { return sessionId; }

    private String name;
    public String getName() { return name; }
    public void setName(String value) { name = value; }

    private boolean enableDebugMessages;
    public boolean getEnableDebugMessages() { return enableDebugMessages; }
    public void setEnableDebugMessages(boolean value) { enableDebugMessages = value; }

	private boolean discoverable;
    public boolean getDiscoverable() { return discoverable; }
    public void setDiscoverable(boolean value) { discoverable = value; }

    public int getHttpDownloadRequestCount()
    {
        return fromCloudCnt;
    }

    public int getHttpUploadRequestCount()
    {
        return toCloudCnt;
    }

    private boolean _useEncryption = false;
    public boolean getUseEncryption() { return _useEncryption; }
    public void setUseEncryption(boolean value) throws UnsupportedEncodingException {
        if (value != _useEncryption) {
            _useEncryption = value;
            if (serviceRunning) {
                //when encryption value changes while service is running then the download request must be cancelled so a
                //new download request can be generated
                synchronized (_SendCancelRequestLock) {
                    sendCancelRequest(false);
                }
            }
        }
    }

    private int _downloadRequestTimeout = c_defaultDownloadRequestTimeout;   //seconds
    public int getDownloadRequestTimeout() { return _downloadRequestTimeout; }
    public void setDownloadRequestTimeout(int value) {
        if (value > c_maximumDownloadTimeout) {
            _downloadRequestTimeout = c_maximumDownloadTimeout;
        } else if (value < c_minimumDownloadTimeout) {
            _downloadRequestTimeout = c_minimumDownloadTimeout;
        } else {
            _downloadRequestTimeout = value;
        }
		if (serviceRunning)
		{
			synchronized (_SendCancelRequestLock) {
                try {
                    sendCancelRequest(false);
                }
                catch (UnsupportedEncodingException e)
                {

                }
            }
		}
    }

    public boolean getConnected() {
        if (state == statesEnum.Running) {
            return true;
        } else {
            return false;
        }
    }

    public void setNewNetworkNames(List<String> networkNames) throws UnsupportedEncodingException {
        this.networkNames = networkNames;
        if (state == statesEnum.Running)
        {
            //when service is running cancel the current download request so new default networks can be set
            networksUpdated = false;
            sendCancelRequest(false);
        }
    }

    private void buildUrls()
    {
        synchronized (_urlStringsLock)
        {
            //build download url
            StringBuilder url = new StringBuilder("http://")
                    .append(serverIpAddr2)
                    .append("/Download");
            downloadUrl = url.toString();

            //build download parameters
            StringBuilder parameters = new StringBuilder("~id=")
                    .append(deviceId);
            downloadParams = parameters.toString();

            //build upload url
            url = new StringBuilder("http://")
                    .append(serverIpAddr)
                    .append("/Upload");
            uploadUrl = url.toString();

            //build upload parameters
            parameters = new StringBuilder("~id=")
                    .append(deviceId);
            uploadParams = parameters.toString();

            //build encryption download url
            url = new StringBuilder("https://")
                    .append(serverUrlDownload)
                    .append("/Download");
            downloadSslUrl = url.toString();

            //build encryption upload url
            url = new StringBuilder("https://")
                    .append(serverUrlUpload)
                    .append("/Upload");
            uploadSslUrl = url.toString();
        }
    }

    private void sendCancelRequest(Boolean terminateSession) throws UnsupportedEncodingException {
        StringBuilder postStr;
        CywSendPacketHttpClass sendPacket = new CywSendPacketHttpClass();
        StringBuilder urlssl = new StringBuilder("https://");
        StringBuilder url = new StringBuilder("http://");

        url.append(serverIpAddr);
        urlssl.append(serverUrlUpload);

        url.append("/CancelDownload");
        urlssl.append("/CancelDownload");
        sendPacket.url = url.toString();
        sendPacket.urlSsl = urlssl.toString();

        postStr = new StringBuilder("~id=");
        postStr.append(deviceId);
		if (terminateSession)
        {
            postStr.append("~ts=1");
        }
		sendPacket.sendData = postStr.toString().getBytes("UTF8");
        sendPacket.packetType = c_cancelRequest;
        toMasterForCloudQueue.add(sendPacket);
        synchronized (_waitMasterSetLock)
        {
            waitMaster.Set();    //set master thread eventwaithandle
        }
    }

    //constructor function
    public CywCloudInterface(String username, String networkPassword, String... networkNames)
    {
        this.userName = username;
        this.networkPassword = networkPassword;
        this.networkNames = new ArrayList<String>();
        if ( null != networkNames )
            for (String networkName : networkNames)
                this.networkNames.add(networkName);
        sessionId = "-1";
        name = defaultInstanceName;
		discoverable = true;
    }

    public void startService()
    {
        final CywCloudInterface instance = this;

        closingThreads = false;
        serviceRunning = true;
        waitToCloud = new ManualResetEvent(false);
        waitFromCloud = new ManualResetEvent(false);
        waitMaster = new ManualResetEvent(false);
        threadToCloud = new Thread() {
            public void run() {
                toCloudThread(instance);
            }
        };
        //threadToCloud.IsBackground = true;
        threadToCloud.start();
        threadFromCloud = new Thread() {
            public void run() {
                try {
                    fromCloudThread(instance);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        };
        //threadFromCloud.IsBackground = true;
        threadFromCloud.start();

        threadMaster = new Thread() {
            public void run() {
                try {
                    masterThread(instance);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        threadMaster.start();
    }

    public void closeConnection()
    {
        //stop from cloud first by sending a cancel request to cloud
        fromCloudThreadRunning = false;
        toCloudThreadRunning = false;
        masterThreadRunning = false;
        closingThreads = true;
        serviceRunning = false;
        synchronized (_SendCancelRequestLock)
        {
            try {
                sendCancelRequest(true);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
			connectionStatusEvent(false);  //service has been stopped
        }
    }


    public int sendData(CywDataToSend data) throws UnsupportedEncodingException {
		//cannot send an empty data packet
        if ((data.dataForSending == null) || (data.dataForSending.length == 0))
        {
            return 6;
        }
        CywSendPacketHttpClass sendPacket = new CywSendPacketHttpClass();

        synchronized (_urlStringsLock)
        {
            sendPacket.url = uploadUrl;
            sendPacket.urlSsl = uploadSslUrl;
            sendPacket.defaultParams = uploadParams;
        }
        sendPacket.sendData = tildeEncode(data.dataForSending);
        sendPacket.packetType = c_dataPacket;
        sendPacket.toSessionIDs = data.toSessionIDs;
        List<String> tildeEncodedNetworks = new ArrayList<String>();
        for (int i = 0; i < data.toNetworks.size(); i++)
        {
            tildeEncodedNetworks.add(tildeEncode(data.toNetworks.get(i)));
        }
        sendPacket.toNetworks = tildeEncodedNetworks;
        sendPacket.dataType = tildeEncode(data.dataType);
        toMasterForCloudQueue.add(sendPacket);
        synchronized (_waitMasterSetLock)
        {
            waitMaster.Set();    //set master thread eventwaithandle
        }
		return 0;
    }

	public void sendDiscovery(ArrayList<String> networks) throws UnsupportedEncodingException
    {
        CywDataToSend data = new CywDataToSend();
		String dStr = "?";
        data.dataForSending = dStr.getBytes("UTF8");
        data.dataType = "Discovery";
        data.toNetworks = networks;
        sendData(data);
    }

    public void SendDiscovery() throws UnsupportedEncodingException
    {
        CywDataToSend data = new CywDataToSend();
		String dStr = "?";
        data.dataForSending = dStr.getBytes("UTF8");
        data.dataType = "Discovery";
        sendData(data);
    }

    private boolean checkIfDataCanBeAdded(CywSendPacketHttpClass packet1, CywSendPacketHttpClass packet2)
    {
        boolean theSame = true;
        if (packet1.url != packet2.url) theSame = false;
        if (packet1.dataType != packet2.dataType) theSame = false;
        if (packet1.packetType != packet2.packetType) theSame = false;
        if (packet1.toNetworks.size() != packet2.toNetworks.size()) theSame = false;
        if (packet1.toSessionIDs.size() != packet2.toSessionIDs.size()) theSame = false;

        if (theSame)
        {
            //check if the individual to networks and session ids are the same
            for (int i = 0; i < packet1.toNetworks.size(); i++)
            {
                if (packet1.toNetworks.get(i) != packet2.toNetworks.get(i)) theSame = false;
            }
            for (int i = 0; i < packet1.toSessionIDs.size(); i++)
            {
                if (packet1.toSessionIDs.get(i) != packet2.toSessionIDs.get(i)) theSame = false;
            }
        }

        return theSame;
    }

    private void masterThread(CywCloudInterface ciData) throws IOException {
        //CywCloudInterface ciData = (CywCloudInterface)inObject;
        ciData.masterThreadRunning = true;
        //ciData.masterThreadTerminated = false;
        boolean somethingHappened = false;
        boolean waitingForResponse = false;
        long waitBeforeRetry = DateTimeHelper.getNow();
        //List<byte> postData;
        //StringBuilder postStr;
        CywToCloudPacketClass toCloudPacket = null; //packet sent out from thread to toCloud thread
        CywSendPacketHttpClass sendPacket = null;  //packet coming into the thread from a function call
        //StringBuilder url;
        //StringBuilder urlSsl;
        //Queue<int> packetIdsWaitingForResponse = new Queue<int>();
        //bool fromCloudStopped = false;
        //bool stopRequestSent = false;
        long stopRequestTimeoutTick = DateTimeHelper.getNow();
        CywEncodeDecode cywEncodeDecode = new CywEncodeDecode();

        //retry variables
        boolean lastPacketSent = true;
        CywToCloudPacketClass retryPacket = null;
        int retryCount = 0;
        int maxRetries = 3;

        while (ciData.masterThreadRunning)
        {
            //state machine
            if (!waitingForResponse)
            {
                //state machine
                switch (state)
                {
                    case RequestCredentials:
                        //check if enough time elapsed between retries to not swamp the server
                        if (waitBeforeRetry > DateTimeHelper.getNow())
                        {
                            break;
                        }
                        somethingHappened = true;
						if (!getConnected())
						{
							connectionStatusEvent(false);
							state = statesEnum.RequestCredentials;
						}
                        waitBeforeRetry = DateTimeHelper.getNow() + c_5seconds;

                        waitingForResponse = true;
                        toCloudPacket = new CywToCloudPacketClass();
                        toCloudPacket.url = "http://www.controlyourway.com/GetCredentials";
                        toCloudPacket.urlSsl = "https://www.controlyourway.com/GetCredentials";
                        RequestCredentialsDecodedData credReq = new RequestCredentialsDecodedData();
                        credReq.username = userName;
                        credReq.networkPassword = networkPassword;
                        toCloudPacket.sendData = cywEncodeDecode.encodeUserCredentialsRequest(credReq);
                        toCloudPacket.packetType = c_requestCredentials;
                        toCloudQueue.add(toCloudPacket);
                        waitToCloud.Set();
                        if (enableDebugMessages) {
                            debugMessagesEvent("Request credentials");
                        }
                        networksUpdated = false;  //whenever the connection restarts the default networks needs to be sent again
                        break;
                    case Running:
                        if (!lastPacketSent) //the last packet was not sent, try again
                        {
                            toCloudQueue.add(retryPacket);
                            waitingForResponse = true;
                            waitToCloud.Set();
                            lastPacketSent = true;
                            if (enableDebugMessages) {
                                debugMessagesEvent("Sending packet last packet again");
                            }
                        }
                        else if (toMasterForCloudQueue.size() > 0) //check if there is new data to send
                        {
                            ByteArrayOutputStream dataToSend = new ByteArrayOutputStream();
                            //List<byte> dataToSend = new List<byte>();
                            boolean addId = true;   //the id must only be added once
                            toCloudPacket = new CywToCloudPacketClass();
                            while ((toMasterForCloudQueue.size() > 0) && (dataToSend.size() < c_maxPacketSize))
                            {
                                if (!addId)
                                {
                                    //this is not the first message added. check if the next message in the queue is the same type of message
                                    //only one message type is allowed per upload
                                    CywSendPacketHttpClass sendPacketPeekForType = toMasterForCloudQueue.peek();
                                    if (sendPacketPeekForType == null)
                                    {
                                        break;
                                    }
                                    if (sendPacketPeekForType.packetType != sendPacket.packetType)
                                    {
                                        break;
                                    }
                                }
                                sendPacket = toMasterForCloudQueue.remove();
                                if (sendPacket != null)
                                {
                                    switch (sendPacket.packetType)
                                    {
                                        case c_dataPacket:
                                            if (addId)
                                            {
                                                addId = false;
                                                dataToSend.write(sendPacket.defaultParams.getBytes("UTF8"));  //add the id field
                                                toCloudPacket.packetType = sendPacket.packetType;
                                                toCloudPacket.url = sendPacket.url;
                                                toCloudPacket.urlSsl = sendPacket.urlSsl;
                                            }
                                            //add networks
                                            for (int i = 0; i < sendPacket.toNetworks.size(); i++)
                                            {
                                                String strToAdd = "~n=" + sendPacket.toNetworks.get(i);
                                                dataToSend.write(strToAdd.getBytes("UTF8"));
                                            }
                                            //add session ids
                                            for (int i = 0; i < sendPacket.toSessionIDs.size(); i++)
                                            {
                                                String strToAdd = "~s=" + sendPacket.toSessionIDs.get(i);
                                                dataToSend.write(strToAdd.getBytes("UTF8"));
                                            }
                                            //add data type if present
                                            if (!sendPacket.dataType.equals(""))
                                            {
                                                String strToAdd = "~dt=" + sendPacket.dataType;
                                                dataToSend.write(strToAdd.getBytes("UTF8"));
                                            }
                                            //add data - must be the last parameter per message
                                            dataToSend.write("~d=".getBytes("UTF8"));
                                            dataToSend.write(sendPacket.sendData);


                                            //see if more packets can be added
                                            while ((toMasterForCloudQueue.size() > 0) && (dataToSend.size() < c_maxPacketSize))
                                            {
                                                //make sure that data is going to the same URL before dequeueing it
                                                CywSendPacketHttpClass sendPacketPeek = toMasterForCloudQueue.peek();
                                                if (sendPacketPeek != null)
                                                {
                                                    if (checkIfDataCanBeAdded(sendPacket, sendPacketPeek))
                                                    {
                                                        //add the data because the data is going to the same devices
                                                        CywSendPacketHttpClass sendPacketNew = toMasterForCloudQueue.remove();
                                                        if (sendPacketNew != null)
                                                        {
                                                            dataToSend.write(sendPacketNew.sendData);
                                                        }
                                                    }
                                                    else
                                                    {
                                                        break;
                                                    }
                                                }
                                                else
                                                {
                                                    break;
                                                }
                                            }
                                            break;
                                        case c_cancelRequest:
                                            toCloudPacket.packetType = sendPacket.packetType;
                                            toCloudPacket.url = sendPacket.url;
                                            toCloudPacket.urlSsl = sendPacket.urlSsl;
                                            dataToSend.write(sendPacket.sendData);
                                            break;
                                    }
                                }
                                //add counter
                                String counterToAdd = "~z=" + toCloudCnt;
                                dataToSend.write(counterToAdd.getBytes("UTF8"));
                                toCloudCnt++;
								retryCount = 0;
                                toCloudPacket.sendData = dataToSend.toByteArray();
                                toCloudQueue.add(toCloudPacket);
                                waitingForResponse = true;
                                waitToCloud.Set();
                                if (enableDebugMessages)
                                {
                                    debugMessagesEvent("Sending packet");
                                }
                            }
                        }
                        break;
                }
                //end of state machine
            }

            //////////////////////////////////////////////////////////////////////////////////////////////
            //see if response from toCloud thread
            if (fromToCloudToMasterQueue.size() > 0)
            {
                //parse response from upload request
                FromToCloudToMasterThreadData d = fromToCloudToMasterQueue.remove();
                if (d != null)
                {
                    somethingHappened = true;
                    waitingForResponse = false;
                    switch(d.packetType)
                    {
                        case c_requestCredentials:
                            ResponseCredentialsDecodedData credResp = cywEncodeDecode.decodeUserCredentialsResponse(d.response);
                            switch (credResp.error)
                            {
                                case "0":
                                    //success
                                    deviceId = credResp.id;
                                    sessionId = credResp.sessionId;
                                    serverIpAddr = credResp.ipAddresses.get(0);
                                    serverIpAddr2 = credResp.ipAddresses.get(1);
                                    serverUrlUpload = credResp.domainNames.get(0);
                                    serverUrlDownload = credResp.domainNames.get(1);
                                    errorCodes = credResp.errorCodes;
                                    state = statesEnum.Running;
                                    if (enableDebugMessages)
                                    {
                                        debugMessagesEvent("Connection established");
                                    }
                                    synchronized (_buildUrlsLock)
	                                {
	                                    buildUrls();
	                                }
	                                waitFromCloud.Set();  //from cloud thread can start requests
	                                connectionStatusEvent(true);  //call user event
	
	
	
	
	                                break;
                                case "8":
                                    //invalid username or network password
                                    errorEvent(credResp.error);
                                    break;
                                case "20":
                                    //connection problem
                                    errorEvent(credResp.error);
                                    break;
                                default:
                                    //protocol error
                                    errorEvent("7");  //Protocol error
                                    break;
                            }
                            break;
                        case c_dataPacket:
                            //get error code returned after uploading data
                            String errorCode = cywEncodeDecode.decodeUploadResponse(d.response);
                            boolean sendError = false;  //fail immediately
                            boolean retryMessage = false;  //retry message again if we have not reached max retries
                            switch (errorCode)
                            {
                                case "0":  //success
                                    if (enableDebugMessages)
                                    {
                                        debugMessagesEvent("Last message sent");
                                    }
                                    break;
                                case "1":  //Unknown error
                                    retryMessage = true;
                                    break;
                                case "2":  //Data could not be sent to one or more recipients
                                    sendError = true;
                                    break;
                                case "7":  //Protocol error
                                    retryMessage = true;
                                    break;
                                case "12":  //Invalid/expired id
                                    sendError = true;
                                    state = statesEnum.RequestCredentials;
                                    break;
                                case "15":
                                    //response to a cancel request
                                    if (closingThreads)
                                    {
                                        //stopping all the threads
                                        ciData.masterThreadRunning = false;
                                        ciData.toCloudThreadRunning = false;
                                        ciData.waitToCloud.Set();
										state = statesEnum.RequestCredentials;
                                        connectionStatusEvent(false);  //service has been stopped
                                        if (enableDebugMessages)
                                        {
                                            debugMessagesEvent("Request has been sent to stop download thread");
                                        }
                                    }
                                    break;
                                case "20":  //Could not establish connection to website
                                    retryMessage = true;
                                    break;
                                default:
                                    errorCode = "3"; //Invalid data received from server, please restart service
                                    retryMessage = true;
                                    break;
                            }
                            if (retryMessage)
                            {
                                if (retryCount < maxRetries)
                                {
                                    lastPacketSent = false;
                                    retryPacket = d.lastPacketSent;
                                    retryCount++;
                                }
                                else
                                {
									lastPacketSent = true;
                                    errorEvent(errorCode);
                                }
                            }
                            if (sendError)
                            {
                                errorEvent(errorCode);
                            }
                            break;
                    }
                }
            }

            //////////////////////////////////////////////////////////////////////////////////////////// 0,12,18,24
            //data from FromCloud thread
            if (fromFromCloudToMasterQueue.size() > 0)
            {
                FromFromCloudToMasterThreadData d = fromFromCloudToMasterQueue.remove();
                if (d != null)
                {
                    somethingHappened = true;
                    switch (d.errorCode)
                    {
                        case "0":      //no error - data was received
                            try
                            {
								//check if a dicovery message was received and respond
                                String recStr = new String(d.data, StandardCharsets.UTF_8);
                                if (discoverable && (recStr.equals("?")) && d.dataType.equals("Discovery"))
                                {
                                    //respond to discovery
                                    CywDataToSend data = new CywDataToSend();
                                    data.dataForSending = name.toString().getBytes("UTF8");
                                    data.dataType = "Discovery Response";
                                    data.toSessionIDs.add(d.fromSessionID);
                                    sendData(data);
                                    debugMessagesEvent("Discovery response sent");
                                }
                                else
                                {
                                	dataReceivedEvent(new DataReceivedEvent(d.data, d.dataType, d.fromSessionID));
								}
                            }
                            catch (Exception e)
                            {
                                System.out.print(e.getMessage());
                            }
                            break;
                        case "12":     //invalid session id
                            state = statesEnum.RequestCredentials;
                            if (enableDebugMessages)
                            {
                                debugMessagesEvent("Invalid session ID from FromCloud thread");
                            }
                            break;
                        //case "e16":   //no data available
                        //    break;
                        case "18":     //multiple download requests made with the same session id
                            state = statesEnum.RequestCredentials;
                            if (enableDebugMessages)
                            {
                                debugMessagesEvent("Multiple downloads from same session ID");
                            }
                            break;
                        case "24":     //download request timeout changed
                            if (enableDebugMessages)
                            {
                                debugMessagesEvent("Download request timeout changed to: " + ciData._downloadRequestTimeout + " seconds");
                            }
                            break;
                    }
                }
            }

            if (!somethingHappened)
            {
                ciData.waitMaster.WaitOne(100);
                ciData.waitMaster.Reset();
            }
            somethingHappened = false;
        }
        //ciData.masterThreadTerminated = true;
    }

    //Thread that will be responsible for uploading data
    private void toCloudThread(CywCloudInterface ciData) { //throws IOException {
        Queue<CywToCloudPacketClass> toData = ciData.toCloudQueue;
        //System.Net.ServicePointManager.Expect100Continue = false;
        int maxReadSize = 10000;
        //CywWebRequest cywWebRequest = new CywWebRequest();

        ciData.toCloudThreadRunning = true;
        //ciData.ToCloudThreadTerminated = false;

        while (ciData.toCloudThreadRunning)
        {
            if (toData.size() > 0)
            {
                //upload new data to cyw server
                CywToCloudPacketClass packet = toData.remove();
                if (packet != null)
                {
                    byte[] responseFromServer = null;
                    //List<byte> repackageResponse = new List<byte>();
                    int readLength = 0;
                    try
                    {
                        HttpURLConnection connection = null;

                        //HttpWebRequest request;
                        if (_useEncryption)
                        {
                            URL url = new URL(packet.urlSsl);
                            connection = (HttpURLConnection)url.openConnection();
                            //request = (HttpWebRequest)WebRequest.Create(packet.urlSsl.ToString());
                        }
                        else
                        {
                            URL url = new URL(packet.url);
                            connection = (HttpURLConnection)url.openConnection();
                            //request = (HttpWebRequest)WebRequest.Create(packet.url.ToString());
                        }
                        //request.KeepAlive = false;
                        connection.setRequestProperty("Keep-Aive",
                                "false");
                        //request.Method = "POST";
                        connection.setRequestMethod("POST");
                        connection.setDoOutput(true);
                        //request.Proxy = null;
                        //request.ContentType = "application/octet-stream";
                        connection.setRequestProperty("Content-Type",
                                "application/octet-stream");
                        //request.ContentLength = packet.sendData.Length;
                        connection.setRequestProperty("Content-Length", Integer.toString(packet.sendData.length));
                        //Stream dataStream = request.GetRequestStream();
                        //dataStream.Write(packet.sendData, 0, packet.sendData.Length);
                        //dataStream.Close();

                        DataOutputStream wr = new DataOutputStream (
                                connection.getOutputStream());
                        wr.write(packet.sendData);
                        wr.close();


                        InputStream dataStream = connection.getInputStream();
                        //BufferedReader rd = new BufferedReader(new InputStreamReader(is));


                        ByteArrayOutputStream responseBuf = new ByteArrayOutputStream();

                        //WebResponse response = request.GetResponse();
                        //List<Byte> responseBuf = new ArrayList<Byte>();
                        //dataStream = response.GetResponseStream();
                        do
                        {
                            responseFromServer = new byte[maxReadSize];
                            readLength = dataStream.read(responseFromServer, 0, maxReadSize);
                            if (readLength < maxReadSize)
                            {
                                //responseBuf.addAll(responseFromServer.Take(readLength).ToArray());
                                responseBuf.write(responseFromServer, 0, readLength);
                            }
                            else
                            {
                                //responseBuf.addAll(responseFromServer);
                                responseBuf.write(responseFromServer, 0, readLength);
                            }
                        }
                        while (readLength == maxReadSize);
                        responseFromServer = responseBuf.toByteArray();

                        //for testing
                        //string str = Encoding.UTF8.GetString(responseFromServer, 0, responseFromServer.Length);

                        dataStream.close();
                        //response.Close();
                    }
                    catch(Exception ex)
                    {
                        try {
                            responseFromServer = "~e=20".getBytes("UTF8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }

                    FromToCloudToMasterThreadData d = new FromToCloudToMasterThreadData();
                    d.response = responseFromServer;
                    d.packetType = packet.packetType;
                    d.lastPacketSent = packet;
                    fromToCloudToMasterQueue.add(d);
                    synchronized (_waitMasterSetLock)
                    {
                        waitMaster.Set();    //set master thread eventwaithandle
                    }
                }
            }
            else
            {
                waitToCloud.WaitOne(100);
                waitToCloud.Reset();
            }
        }
        //ciData.ToCloudThreadTerminated = true;
    }

    private void fromCloudThread(CywCloudInterface ciData) throws UnsupportedEncodingException
    {
        //CywCloudInterface ciData = (CywCloudInterface)inObject;
        Boolean waitBeforeNextReq = false;
        long lastDownloadStartTime = DateTimeHelper.getNow();
        long secondlastDownloadStartTime = DateTimeHelper.getNow();
        //Boolean restartDownload = false;
        CywEncodeDecode cywEncodeDecode = new CywEncodeDecode();
        int maxReadSize = 10000;
        Boolean lastDownloadSuccessful = false;

        ciData.fromCloudThreadRunning = true;
        //ciData.FromCloudThreadTerminated = false;

        while (ciData.fromCloudThreadRunning)
        {
            //Thread.Sleep(100);
            waitBeforeNextReq = false;

            //check if state machine is in a running state
            if (ciData.state == statesEnum.Running)
            {
                byte[] responseFromServer = null;
                //List<byte> repackageResponse = new List<byte>();
                int readLength = 0;
                try
                {
                    //request data from server
                    secondlastDownloadStartTime = DateTimeHelper.getNow();
                    lastDownloadStartTime = DateTimeHelper.getNow();
                    lastDownloadSuccessful = false;
                    StringBuilder url = new StringBuilder();
                    synchronized (_urlStringsLock)
                    {
                        if (_useEncryption) {
                            url.append(ciData.downloadSslUrl);
                        }
                        else {
                            url.append(ciData.downloadUrl);
                        }
                    }
                    StringBuilder postData = new StringBuilder(ciData.downloadParams);
                    boolean updateNetworks = false;
                    if (!networksUpdated)
                    {
                        for (int i = 0; i < networkNames.size(); i++)
                        {
                            postData.append("~n=")
                                    .append(tildeEncode(networkNames.get(i)));
                        }
                        updateNetworks = true;
                    }
                    else
                    {
                        if (getDownloadRequestTimeout() != c_defaultDownloadRequestTimeout)
                        {
                            postData.append("~t=")
                                    .append(Integer.toString(_downloadRequestTimeout));
                        }
                        if (restartDownload)
                        {
                            restartDownload = false;
                            postData.append("~r=1");
                        }
                    }

                    postData.append("~z=");
                    postData.append(Integer.toString(fromCloudCnt));
                    fromCloudCnt++;
                    if (enableDebugMessages) {
                        Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                        if (updateNetworks) {
                            debugMessagesEvent("Update default networks started (" + Integer.toString(fromCloudCnt) + "), " + formatter.format(new Date()));
                        } else {
                            debugMessagesEvent("New download request started (" + Integer.toString(fromCloudCnt) + "), " + formatter.format(new Date()));
                        }
                    }

                    HttpURLConnection connection = (HttpURLConnection)new URL(url.toString()).openConnection();
                    //HttpWebRequest request = (HttpWebRequest)WebRequest.Create(url.ToString());
                    connection.setRequestProperty("Keep-Aive",
                            "false");
                    //request.Method = "POST";
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
//                    request.KeepAlive = false;
  //                  request.Method = "POST";
                    //request.Proxy = null;
                    //add 30 seconds to timeout
                    int timeout = _downloadRequestTimeout;
                    timeout += 30;
                    //convert to ms
                    timeout *= 1000;
                    //request.Timeout = timeout;
                    connection.setConnectTimeout(timeout);
                    // no need to set authenticator - NTLM doesn't work over the internet!
                    //request.Credentials = CredentialCache.DefaultCredentials;

                    byte[] byteArray = postData.toString().getBytes("UTF8");
                    //request.ContentType = "application/octet-stream";
                    connection.setRequestProperty("Content-Type",
                            "application/octet-stream");

                    //request.ContentLength = byteArray.Length;
                    connection.setRequestProperty("Content-Length", Integer.toString(byteArray.length));

                    //Stream dataStream = request.GetRequestStream();
                    //dataStream.Write(byteArray, 0, byteArray.Length);
                    //dataStream.Close();

                    DataOutputStream wr = new DataOutputStream (
                            connection.getOutputStream());
                    wr.write(byteArray);
                    wr.close();


                    InputStream dataStream = connection.getInputStream();
                    ByteArrayOutputStream responseBuf = new ByteArrayOutputStream();

                    //WebResponse response = request.GetResponse();
                    //List<byte> responseBuf = new List<byte>();
                    //dataStream = response.GetResponseStream();
                    do
                    {
                        responseFromServer = new byte[maxReadSize];
                        readLength = dataStream.read(responseFromServer, 0, maxReadSize);
                        if (readLength < maxReadSize)
                        {
                            //responseBuf.addAll(responseFromServer.Take(readLength).ToArray());
                            responseBuf.write(responseFromServer, 0, readLength);
                        }
                        else
                        {
                            //responseBuf.addAll(responseFromServer);
                            responseBuf.write(responseFromServer, 0, readLength);
                        }
                    }
                    while (readLength == maxReadSize);
                    responseFromServer = responseBuf.toByteArray();
                    dataStream.close();
                    //response.Close();
                }
                catch( Exception ex)
                {
                }
                List<KeyValueDataClass> kv = null;
                if (responseFromServer == null)
                {
                    kv = new ArrayList<KeyValueDataClass>();
                    List tempByteArray = new ArrayList();
                    tempByteArray.add((byte) '1');
                    KeyValueDataClass temp = new KeyValueDataClass("e", tempByteArray);
                    kv.add(temp);
                }
                else
                {
                    kv = cywEncodeDecode.decodeCywProtocol(responseFromServer, null);
                }
                KeyValueDataClass errorCode = kv.get(0);
                if (!errorCode.key.equals("e"))
                {
                    waitBeforeNextReq = true;
                }
                else
                {
                    FromFromCloudToMasterThreadData d = null;
                    switch(cywEncodeDecode.byteArrayToString(errorCode.value))
                    {
                        case "0":
                            //data received
                            d = new FromFromCloudToMasterThreadData();
                            for (int i = 1; i < kv.size(); i++)
                            {
                                switch (kv.get(i).key)
                                {
                                    case "f":  //from session id
                                        d.fromSessionID = Integer.parseInt(cywEncodeDecode.byteArrayToString(kv.get(i).value));
                                        break;
                                    case "dt": //data type
                                        d.dataType = cywEncodeDecode.byteArrayToString(kv.get(i).value);
                                        break;
                                    case "d":  //data
                                        d.data = kv.get(i).value;
                                        fromFromCloudToMasterQueue.add(d);
                                        d = new FromFromCloudToMasterThreadData();
                                        break;
                                }
                            }
                            lastDownloadSuccessful = true;
                            break;
                        case "1":  //unknown error
                            if (enableDebugMessages)
                            {
                                debugMessagesEvent("Download error - Unknown error");
                            }
                            waitBeforeNextReq = true;
                            break;
                        case "4": 	//New set of networks to which device is listening was set
                            networksUpdated = true;
                            if (enableDebugMessages)
                            {
                                debugMessagesEvent("All listen to networks was set");
                            }
                            lastDownloadSuccessful = true;
                            break;
                        case "5": 	//Not all of the networks to which device is listening was set
                            networksUpdated = true;
                            if (enableDebugMessages)
                            {
                                debugMessagesEvent("Not all listen to networks was set");
                            }
                            errorEvent("5");
                            lastDownloadSuccessful = true;
                            break;
                        case "7": 	//Protocol error
                            if (enableDebugMessages)
                            {
                                debugMessagesEvent("Download error - Protocol error");
                            }
                            waitBeforeNextReq = true;
                            lastDownloadSuccessful = true;
                            break;
                        case "12":	//Invalid/expired id
                            d = new FromFromCloudToMasterThreadData();
                            d.errorCode = "12";
                            fromFromCloudToMasterQueue.add(d);
                            if (enableDebugMessages)
                            {
                                debugMessagesEvent("Download error - Session ID expired");
                            }
                            waitBeforeNextReq = true;
                            lastDownloadSuccessful = true;
                            break;
                        case "15":	//Download request cancelled by user
                            //waitBeforeNextReq = true;
                            if (enableDebugMessages)
                            {
                                debugMessagesEvent("Download cancelled by user");
                            }
                            lastDownloadSuccessful = true;
                            break;
                        case "16":	//No data available - restart connection immediately
                            lastDownloadSuccessful = true;
                            break;
                        case "18":	//Multiple download requests made with the same session ID
                            //there are devices on the internet that will send the same request to the server again without us knowing about it. This will cause a duplicate request error.
                            //if the request is older than 5 seconds then this is what happened. Calculate the time it took and make sure that the request time is shorter than this
                            long currentTime = DateTimeHelper.getNow();
                            long deltaT;
                            if (lastDownloadSuccessful)
                            {
                                deltaT = currentTime - lastDownloadStartTime;
                            }
                            else
                            {
                                //I found that the modem times out after 30 seconds and then brek the connection causing this module to start a new download.
                                //The next download will receive error code 18 (multiple downloads with same id). We cannot use the download start time of 
                                //this request because it just started. Then use the time of the second last download request
                                deltaT = currentTime - secondlastDownloadStartTime;
                            }
                            deltaT /= c_1second;   //convert to seconds
                            int checkTime = c_minimumDownloadThresholdTime;
                            if (!lastDownloadSuccessful)
                            {
                                //the download request waited before making a new request - account for that
                                checkTime += c_downloadErrorWaitTime + c_downloadSlippageTime;
                            }
                            if (checkTime >= 5)
                            {
                                //something else generated a new request
                                if (lastDownloadSuccessful)
                                {
                                    deltaT -= c_downloadDecreaseTime + c_downloadSlippageTime;
                                }
                                else
                                {
                                    //the download thread waited for 5 seconds before starting a new request - subtract that as well
                                    deltaT -= c_downloadDecreaseTime + c_downloadSlippageTime + c_minimumDownloadThresholdTime;
                                }
                                if (deltaT < c_minimumDownloadTimeout)
                                {
                                    deltaT = c_minimumDownloadTimeout;
                                }
                                if (deltaT <= ciData._downloadRequestTimeout)
                                {
                                    ciData._downloadRequestTimeout = (int)deltaT;
                                    d = new FromFromCloudToMasterThreadData();
                                    d.errorCode = "24";
                                    fromFromCloudToMasterQueue.add(d);
                                    restartDownload = true;
                                }
                            }
                            else
                            {
                                //if failed immediately with error code 18 then different problem
                                d = new FromFromCloudToMasterThreadData();
                                d.errorCode = "18";
                                fromFromCloudToMasterQueue.add(d);
                            }
                            lastDownloadSuccessful = true;
                            break;
                    }
                }
            }
            else
            {
                waitBeforeNextReq = true;
            }
            if (waitBeforeNextReq == true)
            {
                waitFromCloud.WaitOne(c_downloadErrorWaitTime * 1000);
                waitFromCloud.Reset();
            }

        }
        //ciData.FromCloudThreadTerminated = true;
    }

    //add extra ~
    private byte[] tildeEncode(byte[] array) throws UnsupportedEncodingException {
        return tildeEncode(new String(array, "UTF8")).getBytes("UTF8");
    }

    //add extra ~
    private String tildeEncode(String str)
    {
        String enc = str.replace("~", "~~");
        return enc;
    }

    //remove ~
    private byte[] tildeDecode(byte[] array) throws UnsupportedEncodingException {
        return tildeDecode(new String(array, "UTF8")).getBytes("UTF8");
    }

    //remove extra ~
    private String tildeDecode(String str)
    {
        String dec = str.replace("~~", "~");
        return dec;
    }
}

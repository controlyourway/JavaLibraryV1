package com.controlyourway;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by alangley on 16/10/15.
 */
public class CywEncodeDecode
{
    private List<KeyValueDataClass> decodedData = new ArrayList<KeyValueDataClass>();
    private decodeStates state;
    private StringBuilder theKey;
    private List theValue;

    //public bool error { get; private set; }
    private enum decodeStates
    {
        key,
        value,
        controlCharInValue
    };

    public final char c_controlChar = '~';
    public final char c_endOfKey = '=';
    public final String c_protocolGetCredentials = "1";
    public final char c_deviceIdSessionIdSeparator = 's';
    public final int c_ThreadSleepTimeWhenError = 2000;  //ms

    private void startNextEntry()
    {
        KeyValueDataClass keyValue = new KeyValueDataClass(theKey.toString(), theValue);
        decodedData.add(keyValue);
        state = decodeStates.key;
        theKey = new StringBuilder();
        theValue = new ArrayList<Byte>();
    }

    //return null if error occured
    public List<KeyValueDataClass> decodeCywProtocol(byte[] data, List<String> keysNotToDecode)
    {
        if (data == null) return null;
        if (data.length == 0) return null;
        if ((char)data[0] != c_controlChar) return null;
        state = decodeStates.key;
        theKey = new StringBuilder();
        theValue = new ArrayList<Byte>();
        decodedData = new ArrayList<KeyValueDataClass>();

        for (int i = 1; i < data.length; i++)
        {
            byte b = data[i];
            char c = (char)b;
            boolean lastChar = (i == data.length - 1) ? true : false;
            switch(state)
            {
                case key:
                    if (lastChar || (c == c_controlChar))
                    {
                        return null;
                    }
                    if (c == c_endOfKey)
                    {
                        state = decodeStates.value;
                    }
                    else
                    {
                        theKey.append(c);
                    }
                    break;
                case value:
                    if (c == c_controlChar)
                    {
                        if (lastChar)
                        {
                            return null;   //last character cannot be a control char
                        }
                        else
                        {
                            state = decodeStates.controlCharInValue;
                        }
                    }
                    else
                    {
                        theValue.add(b);
                        if (lastChar)
                        {
                            KeyValueDataClass keyValue = new KeyValueDataClass(theKey.toString(), theValue);
                            decodedData.add(keyValue);
                        }
                    }
                    break;
                case controlCharInValue:
                    if (c == c_controlChar)
                    {
                        //double control character means the character
                        theValue.add(b);
                        if (keysNotToDecode != null)
                        {
                            if (JLinq.where(keysNotToDecode, o -> theKey.toString().equals( o ) ).size() > 0)
                            {
                                //this key should not be decoded - like the data that will be uploaded, otherwise it will have to be encoded again
                                theValue.add(b);
                            }
                        }
                        state = decodeStates.value;
                        if (lastChar)
                        {
                            KeyValueDataClass keyValue = new KeyValueDataClass(theKey.toString(), theValue);
                            decodedData.add(keyValue);
                        }
                    }
                    else
                    {
                        if (lastChar)
                        {
                            return null;   //last character cannot be a control char
                        }
                        else
                        {
                            startNextEntry();
                            theKey.append(c);
                            state = decodeStates.key;
                        }
                    }
                    break;
                default:
                    return null;
            }
        }
        return decodedData;
    }

    public String byteArrayToString(byte[] byteArray) throws UnsupportedEncodingException {
        String result = new String(byteArray, "UTF8");
        return result;
    }

    private String getStringValueForKey(String key)
    {
        String response = "";
        try
        {
            byte[] value = JLinq.first(JLinq.where(decodedData, o -> key.equals(o.key))).value;
            response = new String(value, "UTF8");
        }
        catch (Exception ex)
        {
        }
        return response;
    }

    private List<String> getStringListForKey(String key)
    {
        List<String> response =  new ArrayList<String>();
        try
        {
            Collection<KeyValueDataClass> data = JLinq.where(decodedData, o -> key.equals(o.key));
            for (KeyValueDataClass item :  data)
            {
                response.add(new String(item.value, "UTF8"));
            }
        }
        catch(Exception ex)
        {
        }
        return response;
    }

    private String ccEncodeString(String str)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++)
        {
            char charAtI = str.charAt(i);
            if (charAtI == c_controlChar)
            {
                sb.append("~~");
            }
            else
            {
                sb.append(charAtI);
            }
        }
        return sb.toString();
    }

    public byte[] encodeUserCredentialsRequest(RequestCredentialsDecodedData cred) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder("~p=")
                .append(cred.protocol)
                .append("~u=")
                .append(ccEncodeString(cred.username))
                .append("~ps=")
                .append(ccEncodeString(cred.networkPassword))
                .append("~ec=1");   //request all the error codes as well
        byte[] bytes = sb.toString().getBytes("UTF8");
        return bytes;
    }

    //this is called by the client to decode  when a device wants to connect.
    public ResponseCredentialsDecodedData decodeUserCredentialsResponse(byte[] data) throws UnsupportedEncodingException {
        decodeCywProtocol(data, null);
        ResponseCredentialsDecodedData cred = new ResponseCredentialsDecodedData();
        if (data == null)
        {
            cred.error = "7";  //protocol error
            return cred;
        }
        String errorCode = getStringValueForKey("e");
        cred.error = errorCode;
        if (!"0".equals(errorCode))
        {
            //data error
            return cred;
        }

        cred.id = getStringValueForKey("id");
        cred.ipAddresses = getStringListForKey("ip");
        cred.domainNames = getStringListForKey("dn");
        if ((cred.ipAddresses.size() != 2) || (cred.domainNames.size() != 2))
        {
            cred.error = "7";  //protocol error, there needs to be 2 ip addresses and 2 domain names
            return cred;
        }

        //build session id
        StringBuilder sesId = new StringBuilder();
        for (int i = 0; i < cred.id.length(); i++)
        {
            if (cred.id.charAt(i) == 's')
            {
                break;
            }
            sesId.append(cred.id.charAt(i));
        }
        cred.sessionId = sesId.toString();

        //go through values, if any of the keys are numbers then it is an error code. Build up error code dictionary
        for (int i = 0; i < decodedData.size(); i++)
        {
            try {
                int number = Integer.parseInt(decodedData.get(i).key);
                cred.errorCodes.put(decodedData.get(i).key, new String(decodedData.get(i).value, "UTF8"));
            } catch (NumberFormatException nfe) {
            }
        }

        return cred;
    }

    //this is called by the client to decode  when a device wants to connect.
    public String decodeUploadResponse(byte[] data)
    {
        decodeCywProtocol(data, null);
        String errorCode = "1";  //unknown error
        if (data == null)
        {
            return errorCode;
        }
        errorCode = getStringValueForKey("e");
        if ("".equals(errorCode))
        {
            errorCode = "1";  //unknown error
        }
        return errorCode;
    }
}

package com.controlyourway.examples;

import com.controlyourway.ConnectionListener;
import com.controlyourway.CywCloudInterface;
import com.controlyourway.CywDataToSend;
import com.controlyourway.DataReceivedEvent;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Created by alangley on 8/11/15.
 */
public class Logger implements ConnectionListener
{
    private CywCloudInterface _cloud;

    public Logger(CywCloudInterface cloud){
        _cloud = cloud;
    }

    @Override
    public void connectionStatusDelegate(Boolean connected) {
        if (connected) {
            System.out.println("Connected.");
            CywDataToSend data = new CywDataToSend();
            try {
                data.convertStringForSending("Foo");

                data.toNetworks.add("network 1");

                String currentSessionId =  _cloud.getSessionId();
                data.toSessionIDs.add(Integer.parseInt(currentSessionId));

                data.dataType = "debugmessage";

                _cloud.sendData(data);

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        else
            System.out.println("Disconnected.");


    }

    @Override
    public void dataReceived(DataReceivedEvent event) {
        System.out.println("Data received.");
        String str = new String(event.data, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder()
                .append("Data type: ")
                .append(event.dataType)
                .append(" from: ")
                .append(event.fromSessionID)
                .append("\r\n")
                .append(str);
        System.out.println(sb.toString());
    }

    @Override
    public void debugMessages(String message) {
        System.out.println("Debug: " + message);
    }

    @Override
    public void error(String errorCode) {
        System.out.println("Error: " + errorCode);
    }
}
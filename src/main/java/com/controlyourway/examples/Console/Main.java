package com.controlyourway.examples.Console;

import com.controlyourway.ConnectionListener;
import com.controlyourway.CywCloudInterface;
import com.controlyourway.CywDataToSend;
import com.controlyourway.DataReceivedEvent;
import com.controlyourway.examples.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

public class Main {


    private static String readLine() throws IOException {
        if (System.console() != null) {
            return System.console().readLine();
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                System.in));
        return reader.readLine();
    }

    public static void main(String[] args) throws IOException {
        CywCloudInterface cloud = new CywCloudInterface("your_email@address.com", "your_network_password", "network 1");
        cloud.addConnectionListener(new ConsoleListener(cloud));
        cloud.setName("My Java Test");
        cloud.startService();

        System.out.println("\nType text to send and press ENTER, type quit to end application.\n");
        String line = null;
        while((line = readLine()) != null && !line.equalsIgnoreCase("quit")) {
            CywDataToSend data = new CywDataToSend();
            data.dataType = "test message";
            data.convertStringForSending(line);
            cloud.sendData(data);
        }
        cloud.closeConnection();
        System.exit(0);
    }
}

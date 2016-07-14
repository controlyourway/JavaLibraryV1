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
	// write your code here

        CywCloudInterface cloud = new CywCloudInterface("hubert@robospace.co.nz", "hrj123", "network 1");
        //cloud.setNewNetworkNames("network 1");
        cloud.addConnectionListener(new Logger(cloud));

        cloud.startService();

        Console c = System.console();
        System.out.println("\nPress ENTER to proceed.\n");
        if (c != null) {
            c.readLine();
        } else
            readLine();
        cloud.closeConnection();
        System.exit(0);
    }
}

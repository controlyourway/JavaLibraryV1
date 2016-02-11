package com.controlyourway;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by alangley on 22/10/15.
 */
public class CywDataToSend
{
    public byte[] dataForSending;
    public String dataType = "";
    public List<Integer> toSessionIDs = new ArrayList<Integer>();
    public List<String> toNetworks = new ArrayList<String>();

    public void convertStringForSending(String sendStr) throws UnsupportedEncodingException {
        dataForSending = sendStr.getBytes("UTF8");
    }
}
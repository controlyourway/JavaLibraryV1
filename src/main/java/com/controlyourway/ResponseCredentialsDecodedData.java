package com.controlyourway;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

/**
 * Created by alangley on 17/10/15.
 */
public class ResponseCredentialsDecodedData
{
    public String error = "";
    public String id = "";
    public String sessionId = "";
    public List<String> ipAddresses = new ArrayList<String>();
    public List<String> domainNames = new ArrayList<String>();
    public Dictionary<String, String> errorCodes = new Hashtable<String, String>();
}
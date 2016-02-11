package com.controlyourway;

import java.util.List;

/**
 * Created by alangley on 12/10/15.
 */
public class CywSendPacketHttpClass {
    public String url;
    public String urlSsl;
    public byte[] sendData;
    public String dataType;
    public int packetType;
    public String defaultParams = "";
    public List<Integer> toSessionIDs;
    public List<String> toNetworks;
}

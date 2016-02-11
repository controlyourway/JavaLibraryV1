package com.controlyourway;

/**
 * Created by alangley on 12/10/15.
 */
public class DataReceivedEvent {
    public byte[] data;
    public String dataType;
    public int fromSessionID;

    public DataReceivedEvent(byte[] data, String dataType, int fromSessionID){
        this.data = data;
        this.dataType = dataType;
        this.fromSessionID = fromSessionID;
    }

}

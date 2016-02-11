package com.controlyourway;

/**
 * Created by alangley on 12/10/15.
 */
public interface ConnectionListener {
    public void connectionStatusDelegate(Boolean connected);
    public void dataReceived(DataReceivedEvent event);
    public void debugMessages(String message);
    public void error(String errorCode);
}

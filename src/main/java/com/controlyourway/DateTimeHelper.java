package com.controlyourway;

/**
 * Created by alangley on 16/10/15.
 */
public class DateTimeHelper {
    public static long getNow()
    {
        long TICKS_AT_EPOCH = 621355968000000000L;
        return System.currentTimeMillis()*10000 + TICKS_AT_EPOCH;
    }
}

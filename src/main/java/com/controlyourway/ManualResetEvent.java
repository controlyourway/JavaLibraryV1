package com.controlyourway;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by alangley on 12/10/15.
 */
public class ManualResetEvent {
    private final static int MAX_WAIT = 1000;
    private final static String TAG = "ManualEvent";
    private Semaphore semaphore = new Semaphore(MAX_WAIT, false);

    private volatile boolean signaled = false;

    public ManualResetEvent(boolean signaled) {
        this.signaled = signaled;
        if (!signaled) {
            semaphore.drainPermits();
        }
    }

    public boolean WaitOne() {
        return WaitOne(Long.MAX_VALUE);
    }

    private volatile int count = 0;

    public boolean WaitOne(long millis) {
        boolean bRc = true;
        if (signaled)
            return true;

        try {
            ++count;
            if (count > MAX_WAIT) {
                //Log.w(TAG, "More requests than waits: " + String.valueOf(count));
            }

            //Log.d(TAG, "ManualEvent WaitOne Entered");
            bRc = semaphore.tryAcquire(millis, TimeUnit.MILLISECONDS);
            //Log.d(TAG, "ManualEvent WaitOne=" + String.valueOf(bRc));
        } catch (InterruptedException e) {
            bRc = false;
        } finally {
            --count;
        }

        //Log.d(TAG, "ManualEvent WaitOne Exit");
        return bRc;
    }

    public void Set() {
        //Log.d(TAG, "ManualEvent Set");
        signaled = true;
        semaphore.release(MAX_WAIT);
    }

    public void Reset() {
        signaled = false;
        //stop any new requests
        int count = semaphore.drainPermits();
        //Log.d(TAG, "ManualEvent Reset: Permits drained=" + String.valueOf(count));
    }
}
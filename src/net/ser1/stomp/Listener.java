package net.ser1.stomp;

import java.util.Map;

/**
 * (c)2005 Sean Russell
 */
public interface Listener
{
    public void message(Map<String, String> headers, String body);

    default public long getTimeout() { return 0; };
    default public long getTimeoutThreshold() { return 100000; }
}
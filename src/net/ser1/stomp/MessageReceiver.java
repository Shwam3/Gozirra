package net.ser1.stomp;

import java.util.Map;

/**
 * (c)2005 Sean Russell
 */
public interface MessageReceiver
{
    public void receive(Command command, Map<String, String> header, String body);
    public void disconnect();
    public boolean isClosed();
}
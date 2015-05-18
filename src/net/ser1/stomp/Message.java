package net.ser1.stomp;

import java.util.Map;

public class Message
{
    private final Command             command;
    private final Map<String, String> header;
    private final String              body;

    protected Message(Command command, Map<String, String> header, String body)
    {
        this.command = command;
        this.header  = header;
        this.body    = body;
    }

    public Map<String, String> headers()
    {
	return header;
    }

    public String body()
    {
        return body;
    }

    public Command command()
    {
        return command;
    }
}
package net.ser1.stomp;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

class Transmitter
{
    public static void transmit(Command command, Map<String, String> header, String body, OutputStream out) throws IOException
    {
        StringBuilder message = new StringBuilder(command.toString());
        message.append("\n");

        if (header != null)
            for (String key : header.keySet())
                message.append(key).append(":").append(header.get(key)).append("\n");

        message.append("\n");

        if (body != null)
            message.append(body);

        message.append("\000");

        out.write(message.toString().getBytes(Command.ENCODING));
    }
}
package net.ser1.stomp;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * (c)2005 Sean Russell
 */
public class Receiver extends Thread
{
    private MessageReceiver receiver;
    private BufferedReader  input;

    protected Receiver()
    {
        super("Stomp-Receiver");
    }

    public Receiver(MessageReceiver m, InputStream input)
    {
        super("Stomp-Receiver");
        setup(m, input);
    }

    protected void setup(MessageReceiver receiver, InputStream input)
    {
        this.receiver = receiver;
        try
        {
            this.input = new BufferedReader(new InputStreamReader(input, Command.ENCODING));
        }
        catch (UnsupportedEncodingException e) {}
    }

    @Override
    public void run()
    {
        // Loop reading from stream, calling receive()
        try
        {
            while (!isInterrupted())
            {
                // Get command
                if (input.ready())
                {
                    String command = input.readLine();

                    if (command.length() > 0)
                    {
                        try
                        {
                            Command c = Command.valueOf(command);
                            // Get headers
                            Map<String, String> headers = new HashMap<>();
                            String header;
                            while ((header = input.readLine()).length() > 0)
                            {
                              int ind = header.indexOf(':');
                              headers.put(header.substring(0, ind).trim(), header.substring(ind + 1, header.length()).trim());
                            }
                            // Read body
                            StringBuilder body = new StringBuilder();
                            int b;
                            while ((b = input.read()) != 0)
                                body.append((char) b);

                            try
                            {
                              receiver.receive(c, headers, body.toString());
                            }
                            catch (Exception e) {}

                            /*Command c = Command.getCommand(command);

                            // Get headers
                            HashMap<String, String> headers = new HashMap<>();
                            String header;

                            while ((header = input.readLine()).length() > 0)
                            {
                                int ind = header.indexOf(':');
                                headers.put(header.substring(0, ind).trim(), header.substring(ind + 1, header.length()).trim());
                            }

                            // Read body
                            StringBuilder body = new StringBuilder();
                            if (c == Command.MESSAGE || c == Command.ERROR || c == Command.SEND)
                            {
                                if (headers.containsKey("content-length"))
                                {
                                    for (int i = 0; body.length() < contentLength; i++)
                                    {
                                        int b = input.read();
                                        System.out.print((char)b);

                                        if (b > -1)
                                        {
                                            if (i == 1)
                                                b = 0x8b;

                                            char chr = (char) b;

                                            body.append(chr);
                                        }
                                    }
                                    while (input.read() != 0);
                                }
                                else
                                {
                                    int b;

                                    while ((b = input.read()) > 0)
                                        body.append((char) b);
                                }
                            }
                            else
                                input.readLine();

                            try
                            {
                                receiver.receive(c, headers, body.toString());
                            }
                            catch (Exception e) {}*/
                        }
                        catch (Error e)
                        {
                            e.printStackTrace();

                            try { while (input.read() != 0); }
                            catch (Exception ex) {}

                            try
                            {
                                receiver.receive(Command.ERROR, null, e.getMessage() + "\n");
                            }
                            catch (Exception ex) {}
                        }
                    }
                    else
                        receiver.receive(Command.HEARTBEAT, null, null);
                }
                else
                {
                    if (receiver.isClosed())
                    {
                        receiver.disconnect();
                        return;
                    }

                    try
                    {
                        Thread.sleep(200);
                    }
                    catch(InterruptedException e)
                    {
                        interrupt();
                    }
                }
            }
        }
        catch (Exception e)
        {
            System.err.println("Stomp exiting because of an exception");
            e.printStackTrace();
            receiver.receive(Command.ERROR, null, e.getMessage());
        }
    }
}
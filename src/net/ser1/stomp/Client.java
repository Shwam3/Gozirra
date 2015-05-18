package net.ser1.stomp;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import javax.security.auth.login.LoginException;

/**
 * Implements a Stomp client connection to a Stomp server via the network.
 *
 * Example:
 * <pre>
 *     Client c = new Client( "localhost", 61626, "ser", "ser" );
 *     c.subscribe( "/my/channel", new Listener() { ... } );
 *     c.subscribe( "/my/other/channel", new Listener() { ... } );
 *     c.send( "/other/channel", "Some message" );
 *
 *     c.disconnect();
 * </pre>
 *
 * @see Stomp
 */
public class Client extends Stomp implements MessageReceiver
{
    private Thread       listener;
    private OutputStream output;
    private InputStream  input;
    private Socket       socket;

    /**
     * Connects to a server
     *
     * Example:
     * <pre>
     *     Client stomp_client = new Client( "host.com", 61626 );
     *     stomp_client.subscribe( "/my/messages" );
     *     ...
     * </pre>
     *
     * @see Stomp
     * @param server The IP or host name of the server
     * @param port The port the server is listening on
     */
    public Client(String server, int port, String login, String pass) throws IOException, LoginException
    {
        socket = new Socket(server, port);
        input  = socket.getInputStream();
        output = socket.getOutputStream();

        listener = new Receiver(this, input);
        listener.start();

        // Connect to the server
        Map<String, String> header = new HashMap<>();
        header.put("host", server);
        header.put("accept-version", "1.2");
        header.put("login", login);
        header.put("passcode", pass);
        transmit(Command.CONNECT, header, null);

        // Busy loop bail out
        int x=0;
        try
        {
            String error = null;
            while (!isConnected() && ((error = nextError()) == null))
            {
                Thread.sleep(100);
                if (x++ > 100)
                    throw new LoginException("Did not connect in time!");
            }
            if (error != null)
                throw new LoginException(error);
        }
        catch (InterruptedException e) {}
    }

    @Override
    public boolean isClosed()
    {
        return socket.isClosed();
    }

    @Override
    public void disconnect(Map<String, String> header)
    {
        if (!isConnected())
            return;

        transmit(Command.DISCONNECT, header, null);

        listener.interrupt();
        Thread.yield();

        try { input.close(); }
        catch (IOException e) {}

        try { output.close(); }
        catch (IOException e) {}

        try { socket.close(); }
        catch (IOException e) {}

        connected = false;
    }

    /**
     * Transmit a message to the server
     */
    @Override
    public void transmit(Command command, Map<String, String> header, String body)
    {
        try
        {
            Transmitter.transmit(command, header, body, output);
        }
        catch (Exception e)
        {
            receive(Command.ERROR, null, e.getMessage());
        }
    }
}
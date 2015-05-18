package net.ser1.stomp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    private String clientId;

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
    public Client(String server, int port, String login, String pass, String clientId) throws IOException, LoginException
    {
        socket = new Socket(server, port);
        input  = socket.getInputStream();
        output = socket.getOutputStream();

        listener = new Receiver(this, input);
        listener.start();

        this.clientId = clientId;

        // Connect to the server
        Map<String, String> header = new HashMap<>();
        header.put("host", server);
        header.put("accept-version", "1.2");
        header.put("login", login);
        header.put("passcode", pass);
        header.put("client-id", clientId);
        header.put("heart-beat", "0,60000");

        transmit(Command.CONNECT, header, null);

        // Busy loop bail out
        try
        {
            int connectAttempts = 0;
            String error = null;

            while (connectAttempts <= 20 && (!isConnected() && ((error = nextError()) == null)))
            {
                Thread.sleep(100);
                connectAttempts++;
            }

            if (connectAttempts > 20)
                throw new LoginException("Did not connect in time!");

            if (error != null)
                throw new LoginException(error);
        }
        catch (InterruptedException e) {}
    }

    public void subscribe(String topicName, String topicID, Listener listener)
    {
        Map<String, String> headers = new HashMap<>();

        headers.put("ack", "client-individual");
        headers.put("id",  clientId + "-" + topicID);
        headers.put("activemq.subscriptionName", clientId + "-" + topicID);

        super.subscribe(topicName, listener, headers);
    }

    @Override
    public boolean isClosed()
    {
        return socket.isClosed();
    }

    public void ack(String ackId)
    {
        if (ackId == null || ackId.isEmpty())
            throw new IllegalArgumentException("ackId cannot be null or empty");

        Map<String, String> headers = new HashMap<>();
        headers.put("id", ackId.replace("\\c", ":"));

        transmit(Command.ACK, headers, null);
    }

    @Override
    public void disconnect(Map<String, String> header)
    {
        if (!isConnected())
            return;

        transmit(Command.DISCONNECT, header, null);
        listener.interrupt();

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
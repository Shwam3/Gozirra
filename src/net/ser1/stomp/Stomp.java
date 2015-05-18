package net.ser1.stomp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * A Stomp messaging implementation.
 *
 * Messages are handled in one of two ways. If subscribe was called with
 * a listener, then incoming messages are delivered to all listeners
 * of that channel, and the message is deleted from the queue. If no
 * listener was provided for that channel, then messages are placed in
 * a queue and can be retrieved with getNext(). In all cases, when
 * messages are retrieved, they are deleted from the queue.
 *
 * Notes:
 * * FIXME: ERROR messages don't do anything.
 *
 * (c)2005 Sean Russell
 */
public abstract class Stomp
{
    /**
     * A map of channel => listener pairs. String => Listener.
     */
    private final Map<String, List<Listener>> channelListeners = new HashMap<>();

    /**
     * Things that are listening for communication errors. Contains
     * Listeners.
     */
    private final List<Listener> errorListeners = new ArrayList<>();

    /**
     * A message queue; where messages that have no listeners are
     * stored. Contains Messages.
     */
    private final Stack<Message> messageQueue = new Stack<>();

    /**
     * Incoming receipts (as String IDs)
     */
    private final List<String> receipts = new ArrayList<>();

    /**
     * True if connected to a server; false otherwise
     */
    protected boolean connected = false;

    /**
     * Incoming errors (as String messages)
     */
    private final List<String> errorList = new ArrayList<>();

    /**
     * Disconnect from a server, including headers.
     * Must be implemented by the child class. Should set the connected flag to false.
     *
     * @param header A map of key/value headers
     */
    public abstract void disconnect(Map<String, String> header);

    /**
     * Transmit a message to a server. Must be implemented by the child class.
     * The implementation must handle cases where the header and/or the body
     * are null.
     *
     * @param command The Stomp command. If null, causes an error.
     * @param header A map of headers. If null, an empty map is used.
     * @param body The body of the message. May be empty.
     */
    protected abstract void transmit(Command command, Map<String, String> header, String body);

    /**
     * Disconnect from a server. Must be implemented by the child class.
     */
    public void disconnect()
    {
        disconnect(null);
    }

    /**
     * Transmit a message to a server.
     *
     * @param command The Stomp command. If null, causes an error.
     * @param header A map of headers. If null, an empty map is used.
     */
    protected void transmit(Command command, Map<String, String> header)
    {
        transmit(command, header, null);
    }

    /**
     * Transmit a message to a server.
     *
     * @param command The Stomp command. If null, causes an error.
     */
    protected void transmit(Command command)
    {
        transmit(command, null, null);
    }

    /**
     * Begins a transaction. Messages will not be delivered to
     * subscribers until commit() has been called.
     */
    public void begin()
    {
        transmit(Command.BEGIN);
    }

    /**
     * Begins a transaction. Messages will not be delivered to
     * subscribers until commit() has been called.
     *
     * @param header Additional headers to send to the server.
     */
    public void begin(Map<String, String> header)
    {
        transmit(Command.BEGIN, header);
    }

    /**
     * Commits a transaction, causing any messages sent since begin()
     * was called to be delivered.
     */
    public void commit()
    {
        transmit(Command.COMMIT);
    }

    /**
     * Commits a transaction, causing any messages sent since begin()
     * was called to be delivered.
     *
     * @param header Additional headers to send to the server.
     */
    public void commit(Map<String, String> header)
    {
        transmit(Command.BEGIN, header);
    }

    /**
     * Commits a transaction, causing any messages sent since begin()
     * was called to be delivered. This method does not return until
     * the server has confirmed that the commit was successfull.
     */
    public void commitW() throws InterruptedException
    {
        commitW(null);
    }

    /**
     * Commits a transaction, causing any messages sent since begin()
     * was called to be delivered. This method does not return until
     * the server has confirmed that the commit was successfull.
     */
    public void commitW(Map<String, String> header) throws InterruptedException
    {
        String receipt = addReceipt(header);
        transmit(Command.COMMIT, header);
        waitOnReceipt(receipt);
    }

    /**
     * Aborts a transaction. Messages sent since begin() was called
     * are destroyed and are not sent to subscribers.
     */
    public void abort()
    {
        transmit(Command.ABORT);
    }

    /**
     * Aborts a transaction. Messages sent since begin() was called
     * are destroyed and are not sent to subscribers.
     *
     * @param header Additional headers to send to the server.
     */
    public void abort(Map<String, String> header)
    {
        transmit(Command.ABORT, header);
    }

    /**
     * Subscribe to a channel.
     *
     * @param name The name of the channel to listen on
     */
    public void subscribe(String name)
    {
        subscribe(name, null, null);
    }

    /**
     * Subscribe to a channel.
     *
     * @param name The name of the channel to listen on
     * @param header Additional headers to send to the server.
     */
    public void subscribe(String name, Map<String, String> header)
    {
        subscribe(name, null, header);
    }

    /**
     * Subscribe to a channel.
     *
     * @param name The name of the channel to listen on
     * @param listener A listener to receive messages sent to the channel
     */
    public void subscribe(String name, Listener listener)
    {
        subscribe(name, listener, null);
    }

    /**
     * Subscribe to a channel.
     *
     * @param channelName The name of the channel to listen on
     * @param channelListener A listener to receive messages sent to the channel
     * @param headers Additional headers to send to the server.
     */
    public void subscribe(String channelName, Listener channelListener, Map<String, String> headers)
    {
        synchronized (channelListeners)
        {
            if (channelListener != null)
            {
                List<Listener> list = channelListeners.get(channelName);
                if (list == null)
                {
                    list = new ArrayList<>();
                    channelListeners.put(channelName, list);
                }

                if (!list.contains(channelListener))
                    list.add(channelListener);
            }
        }

        if (headers == null)
            headers = new HashMap<>();

        headers.put("destination", channelName);
        transmit(Command.SUBSCRIBE, headers);
    }

    public void addListener(String channelName, Listener channelListener)
    {
        synchronized (channelListeners)
        {
            if (channelListener != null)
            {
                List<Listener> list = channelListeners.get(channelName);
                if (list == null)
                {
                    list = new ArrayList<>();
                    channelListeners.put(channelName, list);
                }

                if (!list.contains(channelListener))
                    list.add(channelListener);
            }
        }
    }

    private String addReceipt(Map<String, String> header)
    {
        if (header == null)
            header = new HashMap<>();

        String receipt = String.valueOf(hashCode() + "&" + System.currentTimeMillis());
        header.put("receipt", receipt);
        return receipt;
    }

    /**
     * Subscribe to a channel. This method blocks until it receives a
     * receipt from the server.
     *
     * @param name The name of the channel to listen on
     * @param header Additional headers to send to the server.
     * @param listener A listener to receive messages sent to the channel
     */
    public void subscribeW(String name, Listener listener, Map<String, String> header) throws InterruptedException
    {
        String receipt = addReceipt(header);
        subscribe(name, listener, header);
        waitOnReceipt(receipt);
    }

    /**
     * Subscribe to a channel. This method blocks until it receives a
     * receipt from the server.
     *
     * @param name The name of the channel to listen on
     * @param listener A listener to receive messages sent to the channel
     */
    public void subscribeW(String name, Listener listener) throws InterruptedException
    {
        subscribeW(name, listener, null);
    }

    /**
     * Unsubscribe from a channel. Automatically unregisters all
     * listeners of the channel. To re-subscribe with listeners,
     * subscribe must be passed the listeners again.
     *
     * @param name The name of the channel to unsubscribe from.
     */
    public void unsubscribe(String name)
    {
        unsubscribe(name, (Map<String, String>) null);
    }

    /**
     * Unsubscribe a single listener from a channel. This does not
     * send a message to the server unless the listener is the only
     * listener of this channel.
     *
     * @param name The name of the channel to unsubscribe from.
     * @param listener The listener to unsubscribe
     */
    public void unsubscribe(String name, Listener listener)
    {
        synchronized (channelListeners)
        {
            List<Listener> list = channelListeners.get(name);

            if (list != null)
            {
                list.remove(listener);

                if (list.isEmpty())
                    unsubscribe(name);
            }
        }
    }

    /**
     * Unsubscribe from a channel. Automatically unregisters all
     * listeners of the channel. To re-subscribe with listeners,
     * subscribe must be passed the listeners again.
     *
     * @param name The name of the channel to unsubscribe from.
     * @param header Additional headers to send to the server.
     */
    public void unsubscribe(String name, Map<String, String> header)
    {
        if (header == null)
            header = new HashMap<>();

        synchronized(channelListeners)
        {
            channelListeners.remove(name);
        }

        header.put("destination", name);
        transmit(Command.UNSUBSCRIBE, header);
    }

    /**
     * Unsubscribe from a channel. Automatically unregisters all
     * listeners of the channel. To re-subscribe with listeners,
     * subscribe must be passed the listeners again.    This method
     * blocks until a receipt is received from the server.
     *
     * @param name The name of the channel to unsubscribe from.
     */
    public void unsubscribeW(String name) throws InterruptedException
    {
        unsubscribe(name, (Map<String, String>) null);
    }

    /**
     * Unsubscribe from a channel. Automatically unregisters all
     * listeners of the channel. To re-subscribe with listeners,
     * subscribe must be passed the listeners again.    This method
     * blocks until a receipt is received from the server.
     *
     * @param name The name of the channel to unsubscribe from.
     */
    public void unsubscribeW(String name, Map<String, String> header) throws InterruptedException
    {
        String receipt = addReceipt(header);
        unsubscribe(name, (Map<String, String>) null);
        waitOnReceipt(receipt);
    }

    /**
     * Send a message to a channel synchronously.    This method does
     * not return until the server acknowledges with a receipt.
     *
     * @param destination The name of the channel to send the message to
     * @param message The message to send.
     */
    public void sendW(String destination, String message) throws InterruptedException
    {
        sendW(destination, message, null);
    }

    /**
     * Send a message to a channel synchronously. This method does
     * not return until the server acknowledges with a receipt.
     *
     * @param destination The name of the channel to send the message to
     * @param message The message to send.
     */
    public void sendW(String destination, String message, Map<String, String> header) throws InterruptedException
    {
        String receipt = addReceipt(header);
        send(destination, message, header);
        waitOnReceipt(receipt);
    }

    /**
     * Send a message to a channel.
     *
     * @param destination The name of the channel to send the message to
     * @param message The message to send.
     */
    public void send(String destination, String message)
    {
        send(destination, message, null);
    }

    /**
     * Send a message to a channel.
     *
     * @param dest The name of the channel to send the message to
     * @param mesg The message to send.
     * @param header Additional headers to send to the server.
     */
    public void send(String dest, String mesg, Map<String, String> header)
    {
        if (header == null)
            header = new HashMap<>();

        header.put("destination", dest);
        transmit(Command.SEND, header, mesg);
    }

    /**
     * Get the next unconsumed message in the queue. This is non-blocking.
     *
     * @return the next message in the queue, or null if the queue
     *    contains no messages. This is non-blocking.
     */
    public Message getNext()
    {
        synchronized(messageQueue)
        {
            return messageQueue.pop();
        }
    }

    /**
     * Get the next unconsumed message for a particular channel. This is non-blocking.
     *
     * @param name the name of the channel to search for
     *
     * @return the next message for the channel, or null if the queue
     *    contains no messages for the channel.
     */
    public Message getNext(String name)
    {
        synchronized(messageQueue)
        {
            for (int i = 0; i < messageQueue.size(); i++)
            {
                Message message = messageQueue.get(i);
                if (message.headers().get("destination").equals(name))
                {
                    messageQueue.remove(i);
                    return message;
                }
            }
        }
        return null;
    }

    public void addErrorListener(Listener listener)
    {
        synchronized (errorListeners)
        {
            errorListeners.add(listener);
        }
    }

    public void removeErrorListener(Listener listener)
    {
        synchronized (errorListeners)
        {
            errorListeners.remove(listener);
        }
    }

    /**
     * Checks to see if a receipt has come in.
     *
     * @param receipt_id the id of the receipts to find
     */
    public boolean hasReceipt(String receipt_id)
    {
        synchronized(receipts)
        {
            for (String recipt : receipts)
            {
                if (recipt.equals(receipt_id))
                    return true;
            }
        }
        return false;
    }

    /**
     * Deletes all receipts with a given ID
     *
     * @param receipt_id the id of the receipts to delete
     */
    public void clearReceipt(String receipt_id)
    {
        synchronized(receipts)
        {
            for (String recipt : receipts)
                if (recipt.equals(receipt_id))
                    receipts.remove(recipt);
        }
    }

    /**
     * Remove all of the receipts
     */
    public void clearReceipts()
    {
        synchronized(receipts)
        {
            receipts.clear();
        }
    }

    public void waitOnReceipt(String receipt_id) throws java.lang.InterruptedException
    {
        synchronized(receipts)
        {
            while (!hasReceipt(receipt_id))
            receipts.wait();
        }
    }

    public boolean waitOnReceipt(String receipt_id, long timeout) throws java.lang.InterruptedException
    {
        synchronized(receipts)
        {
            while (!hasReceipt(receipt_id))
                receipts.wait(timeout);

            return receipts.contains(receipt_id);
        }
    }

    public boolean isConnected()
    {
        return connected;
    }

    public String nextError()
    {
        synchronized(errorList)
        {
            if (errorList.isEmpty())
                return null;

            return errorList.remove(0);
        }
    }

    public void receive(Command command, Map<String, String> header, String body)
    {
        if (command == Command.MESSAGE)
        {
            String destination = header.get("destination");
            synchronized(channelListeners)
            {
                List<Listener> listeners = channelListeners.get(destination);
                if (listeners != null)
                    for (Listener listener : listeners)
                        try { listener.message(header, body); }
                        catch (Exception e) { e.printStackTrace(); }
                else
                    messageQueue.push(new Message(command, header, body));
            }
        }
        else if (command == Command.CONNECTED)
        {
            connected = true;
        }
        else if (command == Command.RECEIPT)
        {
            receipts.add(header.get("receipt-id"));
            synchronized(receipts)
            {
                receipts.notify();
            }
        }
        else if (command == Command.ERROR)
        {
            if (!errorListeners.isEmpty())
            {
                synchronized (errorListeners)
                {
                    for (Listener listener : errorListeners)
                    {
                        try
                        {
                            listener.message(header, body);
                        }
                        catch (Exception e) { e.printStackTrace(); /* Don't let listeners screw us over by throwing exceptions */ }
                    }
                }
            }
            else
            {
                synchronized(errorList)
                {
                    errorList.add(body);
                }
            }
        }
    }
}
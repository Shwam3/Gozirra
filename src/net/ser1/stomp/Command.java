package net.ser1.stomp;

public enum Command
{
    ABORT       ("ABORT"),
    ACK         ("ACK"),
    BEGIN       ("BEGIN"),
    COMMIT      ("COMMIT"),
    CONNECT     ("CONNECT"),
    DISCONNECT  ("DISCONNECT"),
    NACK        ("NACK"),
    SEND        ("SEND"),
    SUBSCRIBE   ("SUBSCRIBE"),
    UNSUBSCRIBE ("UNSUBSCRIBE"),

    CONNECTED   ("CONNECTED"),
    ERROR       ("ERROR"),
    MESSAGE     ("MESSAGE"),
    RECEIPT     ("RECEIPT");

    public  final static String ENCODING = "UTF-8";
    private final        String command;

    private Command(String commandName)
    {
        command = commandName;
    }

    public static Command getCommand(String command)
    {
        for (Command cmd : values())
            if (cmd.name().equalsIgnoreCase(command.trim()))
                return cmd;

        throw new Error("Unrecognised command: " + command.trim());
    }

    @Override
    public String toString()
    {
        return command;
    }
}
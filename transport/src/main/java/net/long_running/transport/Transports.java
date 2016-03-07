package net.long_running.transport;

public class Transports
{
    public static TransportBuilder createTransport(String name)
    {
        return new TransportBuilder(name);
    }
}

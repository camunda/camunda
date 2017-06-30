package io.zeebe.transport;

public class Transports
{
    public static TransportBuilder createTransport(String name)
    {
        return new TransportBuilder(name);
    }
}

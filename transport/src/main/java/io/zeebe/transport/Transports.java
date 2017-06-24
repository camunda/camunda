package io.zeebe.transport;

public class Transports
{
    public static ServerTransportBuilder newServerTransport()
    {
        return new ServerTransportBuilder();
    }

    public static ClientTransportBuilder newClientTransport()
    {
        return new ClientTransportBuilder();
    }
}

package org.camunda.tngp.transport.protocol;

import java.util.Arrays;

import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class Protocols
{

    public static final short REQUEST_RESPONSE = 0;
    public static final short FULL_DUPLEX_SINGLE_MESSAGE = 1;

    public static final int NUM_PROTOCOLS = 2;

    public static TransportChannelHandler[] handlerForAllProtocols(TransportChannelHandler handler)
    {
        final TransportChannelHandler[] handlers = new TransportChannelHandler[NUM_PROTOCOLS];

        Arrays.fill(handlers, handler);

        return handlers;
    }
}

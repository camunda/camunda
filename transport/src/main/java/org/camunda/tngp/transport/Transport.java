package org.camunda.tngp.transport;

import java.net.InetSocketAddress;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.impl.BaseChannelImpl;
import org.camunda.tngp.transport.impl.TransportContext;

import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;
import uk.co.real_logic.agrona.concurrent.AgentRunner;

public class Transport
{
    protected TransportContext transportContext;
    protected Int2ObjectHashMap<BaseChannelImpl> channelMap;

    protected final Dispatcher receiveBuffer;
    protected final Dispatcher sendBuffer;

    public Transport(TransportContext transportContext)
    {
        this.transportContext = transportContext;
        this.receiveBuffer = transportContext.getReceiveBuffer();
        sendBuffer = transportContext.getSendBuffer();
    }

    public ClientChannelBuilder createClientChannel(InetSocketAddress remoteAddress)
    {
        return new ClientChannelBuilder(transportContext, remoteAddress);
    }

    public ServerSocketBindingBuilder createServerSocketBinding(InetSocketAddress addr)
    {
        return new ServerSocketBindingBuilder(transportContext, addr);
    }

    public Dispatcher getReceiveBuffer()
    {
        return receiveBuffer;
    }

    public Dispatcher getSendBuffer()
    {
        return sendBuffer;
    }

    public void close()
    {
        try
        {
            transportContext.getSendBuffer().close();
        }
        catch(InterruptedException e)
        {
            // TODO
            e.printStackTrace();
        }

        final AgentRunner[] agentRunners = transportContext.getAgentRunners();
        for (AgentRunner agentRunner : agentRunners)
        {
            agentRunner.close();
        }
    }

}

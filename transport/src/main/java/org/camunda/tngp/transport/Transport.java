package org.camunda.tngp.transport;

import java.net.InetSocketAddress;

import org.camunda.tngp.transport.impl.TransportContext;

import uk.co.real_logic.agrona.concurrent.AgentRunner;

public class Transport
{
    protected TransportContext transportContext;

    public Transport(TransportContext transportContext)
    {
        this.transportContext = transportContext;
    }

    public ClientChannelBuilder createClientChannel(InetSocketAddress remoteAddress)
    {
        return new ClientChannelBuilder(transportContext, remoteAddress);
    }

    public ServerSocketBindingBuilder createServerSocketBinding(InetSocketAddress addr)
    {
        return new ServerSocketBindingBuilder(transportContext, addr);
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

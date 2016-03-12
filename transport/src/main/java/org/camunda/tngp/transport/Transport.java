package org.camunda.tngp.transport;

import java.net.InetSocketAddress;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.transport.impl.BaseChannelImpl;
import org.camunda.tngp.transport.impl.TransportContext;

import uk.co.real_logic.agrona.DirectBuffer;
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

    public long send(int  channelId, DirectBuffer payloadBuffer, int offset, int length)
    {
        // use channel id as stream id in the shared send buffer
        long msgId = sendBuffer.offer(payloadBuffer, offset, length, channelId);

        if(msgId > 0)
        {
            // return the position of the message itself, not the next message
            // TODO: turn this into the default behavior of the dispatcher
            // https://github.com/meyerdan/dispatcher/issues/5
            msgId -= alignedLength(length);
        }

        return msgId;
    }

    /**
     * Non blocking send of a message. Attempts to copy the message to the transport's send buffer
     * and returns.
     *
     * @param channel the {@link BaseChannel} on which the message should be sent.
     * @param payloadBuffer the buffer containing the message payload
     * @param offset the the offset at which the message starts in the payload buffer
     * @param length the length of the message in bytes.
     * @return the unique id of the message.
     */
    public long send(BaseChannel channel, DirectBuffer payloadBuffer, int offset, int length)
    {
        return send(channel.getId(), payloadBuffer, offset, length);
    }

    public int poll(FragmentHandler frgHandler, int maxMessages)
    {
        return receiveBuffer.poll(frgHandler, maxMessages);
    }

    public BaseChannel getChannel(int channelId)
    {
        return channelMap.get(channelId);
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

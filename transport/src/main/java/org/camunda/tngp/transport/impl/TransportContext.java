package org.camunda.tngp.transport.impl;

import java.nio.ByteBuffer;

import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.impl.agent.ReceiverCmd;
import org.camunda.tngp.transport.impl.agent.SenderCmd;
import org.camunda.tngp.transport.impl.agent.TransportConductorCmd;

public class TransportContext
{
    protected static final int CONTROL_FRAME_BUFFER_SIZE = RingBufferDescriptor.TRAILER_LENGTH + (1024 * 4);

    protected ManyToOneRingBuffer controlFrameBuffer = new ManyToOneRingBuffer(
            new UnsafeBuffer(ByteBuffer.allocateDirect(CONTROL_FRAME_BUFFER_SIZE)));
    protected ManyToOneConcurrentArrayQueue<ReceiverCmd> receiverCmdQueue = new ManyToOneConcurrentArrayQueue<>(100);
    protected ManyToOneConcurrentArrayQueue<SenderCmd> senderCmdQueue = new ManyToOneConcurrentArrayQueue<>(100);
    protected ManyToOneConcurrentArrayQueue<TransportConductorCmd> conductorCmdQueue = new ManyToOneConcurrentArrayQueue<>(100);

    protected Dispatcher sendBuffer;
    protected Subscription senderSubscription;

    protected long channelKeepAlivePeriod;
    protected int maxMessageLength;

    protected AgentRunner[] agentRunners;

    public void setSendBuffer(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

    public Dispatcher getSendBuffer()
    {
        return sendBuffer;
    }

    public Subscription getSenderSubscription()
    {
        return senderSubscription;
    }

    public void setSenderSubscription(Subscription senderSubscription)
    {
        this.senderSubscription = senderSubscription;
    }

    public ManyToOneConcurrentArrayQueue<ReceiverCmd> getReceiverCmdQueue()
    {
        return receiverCmdQueue;
    }

    public void setReceiverCmdQueue(ManyToOneConcurrentArrayQueue<ReceiverCmd> receiverCmdQueue)
    {
        this.receiverCmdQueue = receiverCmdQueue;
    }

    public ManyToOneConcurrentArrayQueue<SenderCmd> getSenderCmdQueue()
    {
        return senderCmdQueue;
    }

    public void setSenderCmdQueue(ManyToOneConcurrentArrayQueue<SenderCmd> senderCmdQueue)
    {
        this.senderCmdQueue = senderCmdQueue;
    }

    public int getMaxMessageLength()
    {
        return maxMessageLength;
    }

    public void setMaxMessageLength(int maxMessageLength)
    {
        this.maxMessageLength = maxMessageLength;
    }

    public AgentRunner[] getAgentRunners()
    {
        return agentRunners;
    }

    public void setAgentRunners(AgentRunner[] agentRunners)
    {
        this.agentRunners = agentRunners;
    }

    public ManyToOneConcurrentArrayQueue<TransportConductorCmd> getConductorCmdQueue()
    {
        return conductorCmdQueue;
    }

    public void setConductorCmdQueue(ManyToOneConcurrentArrayQueue<TransportConductorCmd> clientConductorCmdQueue)
    {
        this.conductorCmdQueue = clientConductorCmdQueue;
    }

    public void setChannelKeepAlivePeriod(long channelKeepAlivePeriod)
    {
        this.channelKeepAlivePeriod = channelKeepAlivePeriod;
    }

    public long getChannelKeepAlivePeriod()
    {
        return channelKeepAlivePeriod;
    }

    public ManyToOneRingBuffer getControlFrameBuffer()
    {
        return controlFrameBuffer;
    }

}

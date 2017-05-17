package org.camunda.tngp.transport.impl;

import org.agrona.concurrent.AgentRunner;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;

public class TransportContext
{
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

    public void setChannelKeepAlivePeriod(long channelKeepAlivePeriod)
    {
        this.channelKeepAlivePeriod = channelKeepAlivePeriod;
    }

    public long getChannelKeepAlivePeriod()
    {
        return channelKeepAlivePeriod;
    }

}

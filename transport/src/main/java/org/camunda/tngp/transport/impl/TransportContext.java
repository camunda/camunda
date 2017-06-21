package org.camunda.tngp.transport.impl;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.util.actor.ActorReference;

public class TransportContext
{
    protected Dispatcher sendBuffer;
    protected Subscription senderSubscription;

    protected long channelKeepAlivePeriod;
    protected int maxMessageLength;

    protected ActorReference conductorRef;
    protected ActorReference senderRef;
    protected ActorReference receiverRef;
    protected boolean sendBufferExternallyManaged;

    public void setSendBuffer(Dispatcher sendBuffer, boolean sendBufferExternallyManaged)
    {
        this.sendBuffer = sendBuffer;
        this.sendBufferExternallyManaged = sendBufferExternallyManaged;
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

    public void setChannelKeepAlivePeriod(long channelKeepAlivePeriod)
    {
        this.channelKeepAlivePeriod = channelKeepAlivePeriod;
    }

    public long getChannelKeepAlivePeriod()
    {
        return channelKeepAlivePeriod;
    }

    public ActorReference getConductor()
    {
        return conductorRef;
    }

    public void setConductor(ActorReference conductor)
    {
        this.conductorRef = conductor;
    }

    public ActorReference getSender()
    {
        return senderRef;
    }

    public void setSender(ActorReference sender)
    {
        this.senderRef = sender;
    }

    public ActorReference getReceiver()
    {
        return receiverRef;
    }

    public void setReceiver(ActorReference receiver)
    {
        this.receiverRef = receiver;
    }

    public boolean isSendBufferExternallyManaged()
    {
        return sendBufferExternallyManaged;
    }
}

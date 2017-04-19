package org.camunda.tngp.test.broker.protocol.clientapi;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.transport.singlemessage.SingleMessageHeaderDescriptor;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class RawMessageCollector implements TransportChannelHandler, Supplier<RawMessage>
{
    protected List<RawMessage> messages = new CopyOnWriteArrayList<>();
    protected int eventToReturn = 0;

    protected Object monitor = new Object();
    protected static final long MAX_WAIT = 10 * 1000L;

    protected boolean eventsAvailable()
    {
        return eventToReturn < messages.size();
    }

    @Override
    public RawMessage get()
    {
        if (!eventsAvailable())
        {
            // block haven't got enough events yet
            try
            {
                synchronized (monitor)
                {
                    monitor.wait(MAX_WAIT);
                }
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }

        if (!eventsAvailable())
        {
            // if still not available
            throw new RuntimeException("no more events available");
        }

        final RawMessage nextMessage = messages.get(eventToReturn);
        eventToReturn++;
        return nextMessage;
    }

    @Override
    public void onChannelOpened(TransportChannel transportChannel)
    {
    }

    @Override
    public void onChannelClosed(TransportChannel transportChannel)
    {
    }

    @Override
    public void onChannelSendError(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
    }

    @Override
    public boolean onChannelReceive(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        final short protocolId = buffer.getShort(TransportHeaderDescriptor.protocolIdOffset(offset));

        int messageOffset = offset + TransportHeaderDescriptor.HEADER_LENGTH;
        if (protocolId == Protocols.FULL_DUPLEX_SINGLE_MESSAGE)
        {
            messageOffset += SingleMessageHeaderDescriptor.HEADER_LENGTH;
        }
        else if (protocolId == Protocols.REQUEST_RESPONSE)
        {
            messageOffset += RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH;
        }
        else
        {
            throw new RuntimeException("unexpected protocol " + protocolId);
        }

        messages.add(new RawMessage(protocolId, messages.size(), buffer, messageOffset, length - (messageOffset - offset)));

        synchronized (monitor)
        {
            monitor.notifyAll();
        }

        return true;
    }

    @Override
    public void onControlFrame(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
    }

    public List<RawMessage> getMessages()
    {
        return messages;
    }

    public void moveToTail()
    {
        this.eventToReturn = messages.size();
    }

    public void moveToHead()
    {
        this.eventToReturn = 0;
    }

    public int getNumMessages()
    {
        return messages.size();
    }

    public long getNumMessagesFulfilling(Predicate<RawMessage> predicate)
    {
        return messages.stream().skip(eventToReturn).filter(predicate).count();
    }

}

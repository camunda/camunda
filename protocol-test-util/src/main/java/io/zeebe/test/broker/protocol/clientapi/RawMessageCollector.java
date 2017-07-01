package io.zeebe.test.broker.protocol.clientapi;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import io.zeebe.transport.Channel;
import io.zeebe.transport.protocol.Protocols;
import io.zeebe.transport.protocol.TransportHeaderDescriptor;
import io.zeebe.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import io.zeebe.transport.singlemessage.SingleMessageHeaderDescriptor;
import io.zeebe.transport.spi.TransportChannelHandler;

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
    public void onChannelOpened(Channel transportChannel)
    {
    }

    @Override
    public void onChannelClosed(Channel transportChannel)
    {
    }

    @Override
    public void onChannelSendError(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
    }

    @Override
    public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
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
    public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        return true;
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

package io.zeebe.test.broker.protocol.clientapi;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;

import io.zeebe.transport.ClientInputListener;

public class RawMessageCollector implements ClientInputListener, Supplier<RawMessage>
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

    @Override
    public void onResponse(int streamId, long requestId, DirectBuffer buffer, int offset, int length)
    {
        messages.add(new RawMessage(true, messages.size(), buffer, offset, length));
        synchronized (monitor)
        {
            monitor.notifyAll();
        }
    }

    @Override
    public void onMessage(int streamId, DirectBuffer buffer, int offset, int length)
    {
        messages.add(new RawMessage(false, messages.size(), buffer, offset, length));
        synchronized (monitor)
        {
            monitor.notifyAll();
        }
    }

}

package org.camunda.tngp.broker.logstreams.requests;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.util.buffer.BufferWriter;

public class LogStreamRequestManager
{
    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected final OneToOneConcurrentArrayQueue<LogStreamRequest> queuedRequests;
    protected final OneToOneConcurrentArrayQueue<LogStreamRequest> pooledRequests;

    public LogStreamRequestManager(Dispatcher sendBuffer, int capacity)
    {
        queuedRequests = new OneToOneConcurrentArrayQueue<>(capacity);
        pooledRequests = new OneToOneConcurrentArrayQueue<>(capacity);

        for (int i = 0; i < capacity; i++)
        {
            queuedRequests.add(new LogStreamRequest());
        }

        this.sendBuffer = sendBuffer;
    }

    public boolean enqueue(LogStreamRequest request)
    {
        return queuedRequests.offer(request);
    }

    public LogStreamRequest poll(long position)
    {
        LogStreamRequest request = queuedRequests.peek();

        if (request != null)
        {
            if (request.logStreamPosition == position)
            {
                queuedRequests.remove();
            }
            else
            {
                request = null;
            }
        }

        return request;
    }

    public LogStreamRequest open()
    {
        final LogStreamRequest request = pooledRequests.poll();

        if (request != null)
        {
            request.open(this);
        }

        return request;
    }

}

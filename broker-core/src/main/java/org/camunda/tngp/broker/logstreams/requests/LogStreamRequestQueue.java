package org.camunda.tngp.broker.logstreams.requests;

import org.agrona.concurrent.OneToOneConcurrentArrayQueue;

public class LogStreamRequestQueue
{
    final OneToOneConcurrentArrayQueue<LogStreamRequest> queuedRequests;
    final OneToOneConcurrentArrayQueue<LogStreamRequest> pooledRequests;

    public LogStreamRequestQueue(int capacity)
    {
        queuedRequests = new OneToOneConcurrentArrayQueue<>(capacity);
        pooledRequests = new OneToOneConcurrentArrayQueue<>(capacity);

        for (int i = 0; i < capacity; i++)
        {
            queuedRequests.add(new LogStreamRequest());
        }
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
            if (request.logStreamPosition != position)
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

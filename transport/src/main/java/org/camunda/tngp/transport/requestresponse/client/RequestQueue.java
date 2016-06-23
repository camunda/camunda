package org.camunda.tngp.transport.requestresponse.client;

import org.camunda.tngp.transport.util.BoundedArrayQueue;

public class RequestQueue extends BoundedArrayQueue<TransportRequest>
{
    public RequestQueue(int capacity)
    {
        super(capacity);
    }

    public boolean hasCapacity()
    {
        return getCapacity() - size() > 0;
    }

    public TransportRequest awaitNext()
    {
        final TransportRequest request = poll();

        if (request != null)
        {
            request.awaitResponse();
        }

        return request;
    }

    public TransportRequest pollNextResponse()
    {
        final TransportRequest request = peek();

        if (request != null)
        {
            if (request.pollResponse())
            {
                remove();
                return request;
            }
        }

        return null;
    }

    public void awaitAll()
    {
        for (TransportRequest request : this)
        {
            try
            {
                request.awaitResponse();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public void closeAll()
    {
        for (TransportRequest request : this)
        {
            try
            {
                request.awaitResponse();
                request.close();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        clear();
    }

}

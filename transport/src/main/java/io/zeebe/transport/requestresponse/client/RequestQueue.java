package io.zeebe.transport.requestresponse.client;

import io.zeebe.transport.Loggers;
import io.zeebe.util.BoundedArrayQueue;
import org.slf4j.Logger;

public class RequestQueue extends BoundedArrayQueue<TransportRequest>
{
    public static final Logger LOG = Loggers.TRANSPORT_LOGGER;

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
                LOG.error("Failed to get response", e);
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
                LOG.error("Failed to clsoe request", e);
            }
        }

        clear();
    }

}

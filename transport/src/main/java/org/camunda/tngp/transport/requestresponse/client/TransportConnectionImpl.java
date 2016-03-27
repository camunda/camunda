package org.camunda.tngp.transport.requestresponse.client;

import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.util.LongArrayIndex;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class TransportConnectionImpl implements TransportConnection
{
    public static int STATE_POOLED = 1;
    public static int STATE_OPEN = 2;
    public static int STATE_CLOSING = 3;

    public final static AtomicIntegerFieldUpdater<TransportConnectionImpl> STATE_FIELD
        = AtomicIntegerFieldUpdater.newUpdater(TransportConnectionImpl.class, "state");

    protected volatile int state = STATE_POOLED;
    protected long id;

    protected final TransportConnectionPoolImpl connectionManager;
    protected final Dispatcher sendBuffer;

    protected final LongArrayIndex<TransportRequestImpl> openRequests;

    protected TransportRequestPool requestPool;

    // set while the connection is in state #STATE_CLOSING
    protected CompletableFuture<TransportConnection> closeFuture;

    public TransportConnectionImpl(
            final Transport transport,
            final TransportConnectionPoolImpl connectionManager,
            final TransportRequestPool transportRequestPool,
            final int maxRequests)
    {
        this.connectionManager = connectionManager;
        this.requestPool = transportRequestPool;
        this.openRequests = new LongArrayIndex<>(maxRequests);
        this.sendBuffer = transport.getSendBuffer();
    }

    public void open(long connectionId)
    {
        this.id = connectionId;
        openRequests.reset();
        STATE_FIELD.set(this, STATE_OPEN);
    }

    @Override
    public PooledTransportRequest openRequest(int channelId, int length)
    {
        final PooledTransportRequest request = requestPool.getRequest();

        if(request != null)
        {
            if(openRequest(request, channelId, length))
            {
                return request;
            }
        }

        return null;
    }

    @Override
    public boolean openRequest(
            final TransportRequest request,
            final int channelId,
            final int msgLength)
    {
        if(STATE_FIELD.get(this) != STATE_OPEN)
        {
            throw new IllegalStateException("Cannot open request on "+this+", connection is not open.");
        }

        final TransportRequestImpl requestImpl = (TransportRequestImpl) request;
        final int framedLength = framedLength(msgLength);
        final long now = System.currentTimeMillis();

        final long requestId = openRequests.put(requestImpl);

        long claimedPosition = 0;

        if(requestId >= 0)
        {
            requestImpl.begin(this, requestId, channelId, now);

            do
            {
                claimedPosition = sendBuffer.claim(requestImpl.getClaimedFragment(), framedLength, channelId);
            }
            while(claimedPosition == -2);

            if(claimedPosition >= 0)
            {
                requestImpl.writeHeader();
            }
            else
            {
                requestImpl.abort();
            }
        }

        return claimedPosition >= 0;
    }

    @Override
    public boolean sendRequest(
            final TransportRequest request,
            final int channelId,
            final int msgLength)
    {
        boolean isOpen = openRequest(request, channelId, msgLength);

        if(isOpen)
        {
            try
            {
                final MutableDirectBuffer claimedBuffer = request.getClaimedRequestBuffer();
                final int claimedOffset = request.getClaimedOffset();
                claimedBuffer.putBytes(claimedOffset, request.getRequestBuffer(), 0, msgLength);
                request.commit();
            }
            catch(Exception e)
            {
                request.abort();
                e.printStackTrace();
            }
        }

        return isOpen;
    }

    /**
     * Closes the connection and returns it to the pool.
     */
    public void close()
    {
        if (STATE_FIELD.compareAndSet(this, STATE_OPEN, STATE_CLOSING))
        {
            this.closeFuture = new CompletableFuture<>();

            if (openRequests.size() > 0)
            {
                for (Object obj : openRequests.getObjects())
                {
                    if(obj != null)
                    {
                        final TransportRequestImpl request = (TransportRequestImpl) obj;
                        if (!request.awaitResponse(10, TimeUnit.SECONDS))
                        {
                            request.close();
                        }
                    }
                }
            }

            STATE_FIELD.set(this, STATE_POOLED);
            connectionManager.onConnectionClosed(this);
        }
    }

    public long getId()
    {
        return id;
    }

    @Override
    public boolean isOpen()
    {
        return STATE_FIELD.get(this) == STATE_OPEN;
    }

    // invoked in conductor thread
    public void processChannelClosed(TransportChannel transportChannel)
    {
        if(openRequests.size() > 0)
        {
            for (Object request : openRequests.getObjects())
            {
                if(request != null)
                {
                    ((TransportRequestImpl)request).processChannelClosed(transportChannel);
                }
            }
        }
    }

    // invoked in receive thread
    public boolean processResponse(DirectBuffer buffer, int offset, int length)
    {
        boolean isHandled = false;

        final long requestId = buffer.getLong(requestIdOffset(offset));

        final TransportRequestImpl request = openRequests.poll(requestId);

        if(request != null)
        {
            isHandled = request.processResponse(buffer, offset, length, requestId);
        }

        return isHandled;
    }

    // invoked in sender thread
    public boolean processSendError(long requestId)
    {
        boolean isHandled = false;

        final TransportRequestImpl request = openRequests.poll(requestId);

        if (request != null)
        {
            isHandled = request.processSendError(request.getId());
        }

        return isHandled;
    }


    public void onRequestClosed(TransportRequestImpl request)
    {
        if(STATE_FIELD.get(this) == STATE_OPEN)
        {
            openRequests.remove(request.getId(), request);
        }
    }

}


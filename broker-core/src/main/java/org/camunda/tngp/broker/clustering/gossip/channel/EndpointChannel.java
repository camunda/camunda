package org.camunda.tngp.broker.clustering.gossip.channel;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.ClientChannelBuilder;
import org.camunda.tngp.transport.Transport;

public class EndpointChannel
{
    protected static final int STATE_CLOSED = 0;
    protected static final int STATE_CONNECTING = 1;
    protected static final int STATE_CONNECTED = 2;
    protected static final int STATE_FAILED = 3;

    protected int state = STATE_CLOSED;

    protected final InetSocketAddress remoteAddress;
    protected final Endpoint endpoint;

    protected ClientChannel clientChannel;
    protected CompletableFuture<ClientChannel> future;

    protected long lastUsedTime = -1L;
    protected int referenceCount = 0;

    public EndpointChannel(final Endpoint endpoint)
    {
        this.endpoint = endpoint;
        this.remoteAddress = new InetSocketAddress(endpoint.host(), endpoint.port());
    }

    public void open(final Transport transport, final BiFunction<Transport, InetSocketAddress, ClientChannelBuilder> clientChannelFactory)
    {
        if (state == STATE_CLOSED)
        {
            future = clientChannelFactory.apply(transport, remoteAddress).connectAsync();
            state = STATE_CONNECTING;
        }
        else
        {
            throw new IllegalStateException("Cannot open channel, has not been closed.");
        }
    }

    public ClientChannel getClientChannel()
    {
        if (state == STATE_CONNECTING)
        {
            processConnecting();
        }

        return clientChannel;
    }

    public boolean isClientChannelOpen()
    {
        boolean isOpen = false;

        ClientChannel channel = null;
        try
        {
            channel = getClientChannel();
            isOpen = channel != null && channel.isOpen();
        }
        catch (final RuntimeException e)
        {
            isOpen = false;
        }

        return isOpen;
    }

    protected void processConnecting()
    {
        if (future != null && future.isDone())
        {
            try
            {
                clientChannel = future.get();
                state = STATE_CONNECTED;
            }
            catch (final Exception e)
            {
                state = STATE_FAILED;
                throw new RuntimeException(e);
            }
        }
    }

    public void close()
    {
        try
        {
            if (state == STATE_CONNECTING)
            {
                processConnecting();
            }
        }
        catch (final Exception e)
        {
            // ignore
        }

        switch (state)
        {
            case STATE_CONNECTED:
            {
                clientChannel.closeAsync();
                break;
            }

            case STATE_CONNECTING:
            {
                future.whenComplete((c, t) ->
                {
                    if (t != null)
                    {
                        c.closeAsync();
                    }
                });
            }
        }

        lastUsedTime = -1;
        referenceCount = 0;
        clientChannel = null;
        future = null;
        state = STATE_CLOSED;
    }

    public Endpoint getEndpoint()
    {
        return endpoint;
    }

    public boolean isReferenced()
    {
        return referenceCount > 0;
    }

    public void incrementReferenceCount()
    {
        referenceCount++;
    }

    public void decrementReferenceCount()
    {
        if (referenceCount > 0)
        {
            referenceCount--;
        }
    }

    public long getLastUsedTime()
    {
        return lastUsedTime;
    }

    public void setLastUsedTime(final long lastUsedTime)
    {
        this.lastUsedTime = lastUsedTime;
    }

    public boolean isConnected()
    {
        return state == STATE_CONNECTED;
    }

    public boolean isFailed()
    {
        return state == STATE_FAILED;
    }

    public boolean isClosed()
    {
        return state == STATE_CLOSED;
    }

    public InetSocketAddress getRemoteAddress()
    {
        return remoteAddress;
    }
}

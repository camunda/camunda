package org.camunda.tngp.transport.impl;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.PooledFuture;
import org.camunda.tngp.transport.SocketAddress;

public class ChannelRequest implements PooledFuture<ClientChannel>
{

    protected SocketAddress remoteAddress = new SocketAddress();
    protected ObjectPool<ChannelRequest> requestPool;

    protected volatile boolean failed;
    protected volatile ClientChannel channel;
    protected volatile CompletableFuture<ClientChannel> channelFuture;

    public ChannelRequest(ObjectPool<ChannelRequest> pool)
    {
        this.requestPool = pool;
    }

    public void init(SocketAddress address, CompletableFuture<ClientChannel> channelFuture)
    {
        this.remoteAddress.wrap(address);
        this.channelFuture = channelFuture;
    }

    @Override
    public void resolve(ClientChannel channel)
    {
        if (this.channel != null)
        {
            throw new RuntimeException("Request is already resolved");
        }
        this.channel = channel;
        ((ClientChannelImpl) channel).countUsageBegin();
        if (channelFuture != null)
        {
            channelFuture.complete(channel);
        }
    }

    @Override
    public void fail()
    {
        this.failed = true;
        if (channelFuture != null)
        {
            channelFuture.cancel(true);
        }
    }

    @Override
    public boolean isFailed()
    {
        return failed;
    }

    @Override
    public ClientChannel poll()
    {
        return channel;
    }

    protected boolean isResolved()
    {
        return failed || channel != null;
    }

    @Override
    public void release()
    {
        if (!isResolved())
        {
            // this is important to returning the future to the pool while it is still
            // in use by the party resolving/failing it
            throw new RuntimeException("Cannot release future before it is resolved");
        }

        reset();
        requestPool.returnObject(this);
    }

    protected void reset()
    {
        this.channel = null;
        this.failed = false;
    }

    public SocketAddress getRemoteAddress()
    {
        return remoteAddress;
    }
}

package io.zeebe.transport.impl;

import java.util.concurrent.CompletableFuture;

import io.zeebe.transport.Channel;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.PooledFuture;
import io.zeebe.util.collection.ObjectPool;

public class ChannelRequest implements PooledFuture<Channel>
{

    protected SocketAddress remoteAddress = new SocketAddress();
    protected ObjectPool<ChannelRequest> requestPool;

    protected volatile boolean failed;
    protected volatile Channel channel;
    protected volatile CompletableFuture<Channel> channelFuture;

    public ChannelRequest(ObjectPool<ChannelRequest> pool)
    {
        this.requestPool = pool;
    }

    public void init(SocketAddress address, CompletableFuture<Channel> channelFuture)
    {
        this.remoteAddress.wrap(address);
        this.channelFuture = channelFuture;
    }

    @Override
    public void resolve(Channel channel)
    {
        if (this.channel != null)
        {
            throw new RuntimeException("Request is already resolved");
        }
        this.channel = channel;
        ((ChannelImpl) channel).countUsageBegin();
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
    public Channel poll()
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
            // it is important to avoid returning the future to the pool while it is still
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

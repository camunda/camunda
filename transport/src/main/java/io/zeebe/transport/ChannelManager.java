package io.zeebe.transport;

import java.util.concurrent.CompletableFuture;

import io.zeebe.util.PooledFuture;

public interface ChannelManager
{

    /**
     * Non-blocking; garbage-free if there is already an open channel for
     * that remote address
     */
    PooledFuture<Channel> requestChannelAsync(SocketAddress remoteAddress);

    /**
     * Blocking; Not garbage-free
     */
    Channel requestChannel(SocketAddress remoteAddress);

    /**
     * Release a channel once it is no longer in use. Allows the manager
     * to close it eventually if a channel is not used anymore. Once
     * this method has been called, calling code should discard the
     * reference to the channel object.
     */
    void returnChannel(Channel channel);

    /**
     * Completes when all channels have been closed.
     */
    CompletableFuture<Void> closeAllChannelsAsync();
}

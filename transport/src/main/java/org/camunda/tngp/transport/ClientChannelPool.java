package org.camunda.tngp.transport;

/**
 * Manages a set of client channels that share the same handling for incoming messages. Channels
 * are reused based on the remote address.
 */
public interface ClientChannelPool
{

    /**
     * Requests a new channel from this pool. Non-blocking.
     *
     * @return null if internal capacities for opening a channel are currently exhausted
     */
    PooledFuture<ClientChannel> requestChannelAsync(SocketAddress remoteAddress);

    /**
     * Same as {@link #requestChannelAsync(SocketAddress)} but blocking.
     *
     * @throws RuntimeException if internal capacities for opening a channel are currently exhausted
     */
    ClientChannel requestChannel(SocketAddress remoteAddress);

    /**
     * Reports that a channel is no longer in use. Allows the pool to evict idle channels.
     * The pool may close the channel in the future.
     */
    void returnChannel(ClientChannel channel);

}

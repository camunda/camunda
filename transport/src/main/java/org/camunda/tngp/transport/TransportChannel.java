package org.camunda.tngp.transport;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;

public interface TransportChannel extends AutoCloseable
{
    int getId();

    CompletableFuture<TransportChannel> closeAsync();

    void close();

    /**
     * Schedules a control frame to be sent on this channel.
     * Sending is performed asynchronously in the context of another agent.
     *
     * @return true if control frame could be scheduled for sending
     */
    boolean scheduleControlFrame(DirectBuffer frame, int offset, int length);

    boolean scheduleControlFrame(DirectBuffer frame);

    boolean isOpen();

    boolean isConnecting();

    boolean isClosed();

}
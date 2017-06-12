package org.camunda.tngp.transport;

import org.agrona.DirectBuffer;

/**
 * Note that state inspection methods return the channel's current state,
 * which is coupled to but not synchronized with the state of the underlying
 * TCP connection. E.g. a channel can be ready while in fact the TCP connection
 * has already been closed. Eventually, the channel will leave state ready.
 */
public interface Channel
{
    SocketAddress getRemoteAddress();

    /**
     * @return Stream id to use for writing to send buffer
     */
    int getStreamId();

    /**
     * @return true if channel is connected to remote and
     *   registered with sender and receiver
     */
    boolean isReady();

    /**
     * @return true if channel is closed (either expectedly due to request
     *   or unexpectedly due to io errors); closed is a terminal state, i.e. once closed,
     *   a channel will never leave the state again
     */
    boolean isClosed();

    boolean scheduleControlFrame(DirectBuffer controlFrameBuffer);

    boolean scheduleControlFrame(DirectBuffer controlFrameBuffer, int offset, int length);

}

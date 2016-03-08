package net.long_running.transport;

import net.long_running.dispatcher.AsyncCompletionCallback;
import net.long_running.transport.impl.BaseChannelImpl.State;
import uk.co.real_logic.agrona.DirectBuffer;

public interface BaseChannel
{

    long offer(DirectBuffer payload, int offset, int length);

    Integer getId();

    void close(AsyncCompletionCallback<Boolean> completionCallback);

    boolean closeSync() throws InterruptedException;

    State getState();

}
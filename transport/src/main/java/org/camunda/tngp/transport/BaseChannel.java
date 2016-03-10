package org.camunda.tngp.transport;

import org.camunda.tngp.transport.impl.BaseChannelImpl.State;

import net.long_running.dispatcher.AsyncCompletionCallback;
import uk.co.real_logic.agrona.DirectBuffer;

public interface BaseChannel
{

    long offer(DirectBuffer payload, int offset, int length);

    Integer getId();

    void close(AsyncCompletionCallback<Boolean> completionCallback);

    boolean closeSync() throws InterruptedException;

    State getState();

}
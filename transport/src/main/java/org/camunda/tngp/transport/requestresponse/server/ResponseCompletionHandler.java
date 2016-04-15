package org.camunda.tngp.transport.requestresponse.server;

import uk.co.real_logic.agrona.DirectBuffer;

/**
 * Handles completion of a deferred response
 *
 */
public interface ResponseCompletionHandler
{
    void onAsyncWorkCompleted(
            DeferredResponse response,
            DirectBuffer asyncWorkBuffer,
            int offset,
            int length,
            Object attachement,
            long blockPosition);

    void onAsyncWorkFailed(
            DeferredResponse response,
            DirectBuffer asyncWorkBuffer,
            int offset,
            int length,
            Object attachement);
}

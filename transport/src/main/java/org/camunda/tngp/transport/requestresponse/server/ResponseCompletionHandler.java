package org.camunda.tngp.transport.requestresponse.server;

/**
 * Handles completion of a deferred response
 *
 */
public interface ResponseCompletionHandler
{
    void onAsyncWorkCompleted(DeferredResponse response);

    void onAsyncWorkFailed(DeferredResponse response);
}

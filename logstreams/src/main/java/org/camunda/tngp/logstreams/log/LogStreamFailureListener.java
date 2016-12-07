package org.camunda.tngp.logstreams.log;

public interface LogStreamFailureListener
{
    /**
     * indicates that events after the provided position have failed to be written.
     */
    void onFailed(long failedPosition);

    /**
     * indicates that the stream is writable again after recovering from an error.
     */
    void onRecovered();
}

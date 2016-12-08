package org.camunda.tngp.logstreams.log;

/**
 * React on failures related to a log stream.
 */
public interface LogStreamFailureListener
{
    /**
     * indicates that events after the provided position have failed to be
     * written.
     */
    void onFailed(long failedPosition);

    /**
     * indicates that the stream is writable again after recovering from an
     * error.
     */
    void onRecovered();
}

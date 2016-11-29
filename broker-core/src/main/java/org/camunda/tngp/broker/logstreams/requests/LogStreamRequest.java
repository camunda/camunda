package org.camunda.tngp.broker.logstreams.requests;


/**
 * Holds information about a request which was written to a log stream and is processed asynchronously.
 *
 */
public class LogStreamRequest
{
    /**
     * the position at which the request was published to the log stream
     */
    protected long logStreamPosition;

    /**
     * the channel on which the request was received
     */
    protected int channelId;

    /**
     * the id of the connection in which the request was received
     */
    protected long connectionId;

    /**
     * the id of the request
     */
    protected long requestId;

    /**
     * The queue to which the request was deferred.
     */
    protected LogStreamRequestQueue queue;

    public long getLogStreamPosition()
    {
        return logStreamPosition;
    }

    public void setLogStreamPosition(long logStreamPosition)
    {
        this.logStreamPosition = logStreamPosition;
    }

    public long getConnectionId()
    {
        return connectionId;
    }

    public void setConnectionId(long connectionId)
    {
        this.connectionId = connectionId;
    }

    public long getRequestId()
    {
        return requestId;
    }

    public void setRequestId(long requestId)
    {
        this.requestId = requestId;
    }

    public int getChannelId()
    {
        return channelId;
    }

    public void setChannelId(int channelId)
    {
        this.channelId = channelId;
    }

    public void enqueue()
    {
        queue.queuedRequests.add(this);
    }

    public void close()
    {
        channelId = -1;
        requestId = -1;
        logStreamPosition = -1;
        connectionId = -1;

        queue.pooledRequests.add(this);
        queue = null;
    }

    public void open(LogStreamRequestQueue streamRequestQueue)
    {
        queue = streamRequestQueue;
    }

}

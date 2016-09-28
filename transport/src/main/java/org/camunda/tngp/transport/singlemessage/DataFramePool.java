package org.camunda.tngp.transport.singlemessage;

import org.camunda.tngp.dispatcher.Dispatcher;

public interface DataFramePool
{

    /**
     * @return null, if frame could not be opened (e.g. because of no space in the send buffer)
     */
    OutgoingDataFrame openFrame(int messageLength, int channelId);

    static DataFramePool newBoundedPool(int capacity, Dispatcher sendBuffer)
    {
        return new DataFramePoolImpl(capacity, sendBuffer);
    }
}

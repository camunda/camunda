package io.zeebe.transport.singlemessage;

import io.zeebe.dispatcher.Dispatcher;

public interface DataFramePool
{

    /**
     * @param channelId yes
     * @param messageLength required payload size
     *
     * @return null, if frame could not be opened (e.g. because of no space in the send buffer)
     */
    OutgoingDataFrame openFrame(int channelId, int messageLength);

    static DataFramePool newBoundedPool(int capacity, Dispatcher sendBuffer)
    {
        return new DataFramePoolImpl(capacity, sendBuffer);
    }
}

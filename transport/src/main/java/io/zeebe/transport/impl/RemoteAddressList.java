package io.zeebe.transport.impl;

import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;

/**
 * Threadsafe datastructure for indexing remote addresses and assigning
 * streamIds.
 *
 */
public class RemoteAddressList
{
    private volatile int size;
    private RemoteAddress[] index = new RemoteAddress[0];

    public RemoteAddress getByStreamId(int streamId)
    {
        if (streamId < size)
        {
            return index[streamId];
        }

        return null;
    }

    public RemoteAddress getByAddress(SocketAddress inetSocketAddress)
    {
        final int currSize = size;

        for (int i = 0; i < currSize; i++)
        {
            final RemoteAddress remoteAddress = index[i];

            if (remoteAddress.getAddress().equals(inetSocketAddress))
            {
                return remoteAddress;
            }
        }

        return null;
    }

    public synchronized RemoteAddress register(SocketAddress inetSocketAddress)
    {
        RemoteAddress result = getByAddress(inetSocketAddress);

        if (result == null)
        {
            final int prevSize = size;
            final int newSize = prevSize + 1;

            final RemoteAddress remoteAddress = new RemoteAddress(prevSize, new SocketAddress(inetSocketAddress));

            final RemoteAddress[] newAddresses = new RemoteAddress[newSize];
            System.arraycopy(index, 0, newAddresses, 0, prevSize);
            newAddresses[remoteAddress.getStreamId()] = remoteAddress;

            this.index = newAddresses;
            this.size = newSize; // publish

            result = remoteAddress;
        }

        return result;
    }
}

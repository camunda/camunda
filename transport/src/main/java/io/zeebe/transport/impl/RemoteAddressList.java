/**
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

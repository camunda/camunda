/*
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
package io.zeebe.raft.backpressure;

public class BackpressureHelper
{
    /**
     * records the size in bytes of each event by position. Required when the follower
     * acknowledges positions.
     */
    private final EventSizesByPosition eventSizesByPosition = new EventSizesByPosition();

    /**
     * The size of the remote buffer in bytes
     */
    private int remoteBufferSize;

    /**
     * The number of bytes that are currently "in flight". Sent, but not acknowledged
     */
    private int currentInFlight = 0;

    /**
     * First Event needs to be acknowledged before the next one can be sent
     */
    private boolean isFirstEventAcknowledged;

    private boolean isFirstEventSent;

    /**
     * Initializes the backpressure helper with a remote buffersize
     *
     * @param remoteBufferSize size of the remote buffer in bytes
     */
    public BackpressureHelper(int remoteBufferSize)
    {
        this.remoteBufferSize = remoteBufferSize - (int) (remoteBufferSize * 0.2);
    }

    public void onEventSent(long position, int eventSize)
    {
        if (!isFirstEventSent && !isFirstEventAcknowledged)
        {
            isFirstEventSent = true;
        }

        eventSizesByPosition.add(position, eventSize);
        currentInFlight += eventSize;
    }

    public void onEventAcknowledged(long position)
    {
        if (isFirstEventSent && !isFirstEventAcknowledged)
        {
            isFirstEventAcknowledged = true;
        }
        currentInFlight -= eventSizesByPosition.markConsumed(position);
    }

    public void reset()
    {
        eventSizesByPosition.reset();
        currentInFlight = 0;
        isFirstEventSent = false;
        isFirstEventAcknowledged = false;
    }

    public boolean canSend(int bytes)
    {
        if (!isFirstEventSent)
        {
            return true;
        }
        else
        {
            return remoteBufferSize > currentInFlight + bytes;
        }
    }
}

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

import java.util.Arrays;

import org.agrona.BitUtil;

/**
 * Used for dealing with backpressure in replication
 *<p>
 * Optimized datastructure allowing to record event sizes by position so that
 * we can calculate the size of the "in flight" dataset during replication.
 *<p>
 * For each event position that has not yet been acknowledged by the follower, the size
 * of the corresponding event (in bytes) is recorded. When the follower
 * acknowledges an event, all event sizes up to (and including) this position are removed.
 *<p>
 * The datastructure is array backed and optimized for linear scanning access. The arrays
 * are automatically grown to the necessary size. Once this state is reached, it is garbage free
 * in a ring-buffer like fashion.
 *
 */
public class EventSizesByPosition
{
    private long[] positions;
    private int[] eventSizes;
    private int capacity;
    private long head;
    private long tail;

    public EventSizesByPosition(int initialCapacity)
    {
        this.capacity = initialCapacity;

        this.positions = new long[capacity];
        this.eventSizes = new int[capacity];

        this.head = 0;
        this.tail = 0;
    }

    public EventSizesByPosition()
    {
        this(32);
    }

    /**
     * The number of record. Note: this is not the total of the current "in-flight" dataset.
     * Call {@link #getTotalEventSizes()} to obtain that.
     *
     * @return the number of records
     */
    public int size()
    {
        return (int) (head - tail);
    }

    /**
     * record the size of an event by position
     *
     * @param pos the position of the vent
     * @param eventSize the size of the event
     */
    public void add(long pos, int eventSize)
    {
        ensureCapacity();

        final int offset = offset(head++, capacity);

        positions[offset] = pos;
        eventSizes[offset] = eventSize;
    }

    /**
     * mark positions consumed up to (and including) the provided limit.
     *
     * @param limit the last position to remove (inclusive)
     * @return the total event size consumed
     */
    public int markConsumed(long limit)
    {
        int consumed = 0;

        while (!isEmpty())
        {
            final int offset = offset(tail, capacity);
            final long position = positions[offset];

            if (position <= limit)
            {
                consumed += eventSizes[offset];

                positions[offset] = 0;
                eventSizes[offset] = 0;

                tail++;
            }
            else
            {
                break;
            }
        }

        return consumed;
    }

    public boolean isEmpty()
    {
        return size() == 0;
    }

    private static int offset(long pos, int capacity)
    {
        return (int) ((pos) & (capacity - 1));
    }

    public int getCurrentCapacity()
    {
        return capacity;
    }

    private void ensureCapacity()
    {
        if (size() == capacity)
        {
            capacity = BitUtil.findNextPositivePowerOfTwo(capacity + 1);

            final long[] positions = new long[capacity];
            System.arraycopy(this.positions, 0, positions, 0, this.positions.length);
            this.positions = positions;

            final int[] eventSizes = new int[capacity];
            System.arraycopy(this.eventSizes, 0, eventSizes, 0, this.eventSizes.length);
            this.eventSizes = eventSizes;
        }
    }

    public void reset()
    {
        head = 0;
        tail = 0;
        Arrays.fill(positions, 0);
        Arrays.fill(eventSizes, 0);
    }
}

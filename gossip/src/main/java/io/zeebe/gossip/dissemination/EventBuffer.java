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
package io.zeebe.gossip.dissemination;

import java.util.*;
import java.util.function.Supplier;

public class EventBuffer<T>
{
    private final EventIterator iterator = new EventIterator();

    private final BufferedEvent<T>[] buffer;

    private int size = 0;

    @SuppressWarnings("unchecked")
    public EventBuffer(Supplier<BufferedEvent<T>> eventFactory, int capacity)
    {
        this.buffer = new BufferedEvent[capacity];

        for (int i = 0; i < capacity; i++)
        {
            buffer[i] = eventFactory.get();
        }
    }

    public BufferedEvent<T> add()
    {
        for (int i = 0; i < buffer.length; i++)
        {
            final BufferedEvent<T> event = buffer[i];
            if (!event.isSet())
            {
                event.recycle();

                size += 1;

                return event;
            }
        }
        return null;
    }

    public int size()
    {
        return size;
    }

    public void clear()
    {
        for (int i = 0; i < buffer.length; i++)
        {
            final BufferedEvent<T> event = buffer[i];
            event.clear();
        }
    }

    public void removeEventsWithSpreadCountGreaterThan(int spreadCount)
    {
        final Iterator<BufferedEvent<T>> it = iterator(Integer.MAX_VALUE);
        while (it.hasNext())
        {
            final BufferedEvent<T> event = it.next();

            if (event.getSpreadCount() > spreadCount)
            {
                size -= 1;

                event.clear();
            }
        }
    }

    public void sortBySpreadCount()
    {
        Arrays.sort(buffer);
    }

    public Iterator<BufferedEvent<T>> iterator(int limit)
    {
        iterator.reset(limit);

        return iterator;
    }

    private class EventIterator implements Iterator<BufferedEvent<T>>
    {
        private int index = 0;
        private int count = 0;
        private int limit = 0;

        public void reset(int limit)
        {
            index = 0;
            count = 0;

            this.limit = limit;
        }

        @Override
        public boolean hasNext()
        {
            for (int i = index; i < buffer.length && count < limit; i++)
            {
                final BufferedEvent<T> event = buffer[i];

                if (event.isSet())
                {
                    return true;
                }
            }

            return false;
        }

        @Override
        public BufferedEvent<T> next()
        {
            if (hasNext())
            {
                final BufferedEvent<T> event = buffer[index];

                count += 1;
                index += 1;

                return event;
            }
            else
            {
                throw new NoSuchElementException();
            }
        }
    }

}

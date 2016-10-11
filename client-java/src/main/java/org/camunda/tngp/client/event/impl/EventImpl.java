/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.client.event.impl;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.client.event.Event;

public class EventImpl implements Event
{
    protected long position;

    protected final UnsafeBuffer eventBuffer;

    public EventImpl(int eventLength)
    {
        eventBuffer = new UnsafeBuffer(new byte[eventLength]);
    }

    @Override
    public int getEventLength()
    {
        return eventBuffer.capacity();
    }

    @Override
    public DirectBuffer getEventBuffer()
    {
        return eventBuffer;
    }

    @Override
    public long getPosition()
    {
        return position;
    }

    public void setPosition(long position)
    {
        this.position = position;
    }

}

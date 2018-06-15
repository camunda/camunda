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
package io.zeebe.raft.util;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import java.util.Objects;

import io.zeebe.logstreams.log.LoggedEvent;

public class EventInfo
{
    private final long position;
    private final int term;
    private final String message;

    public EventInfo(long position, int term, String message)
    {
        this.position = position;
        this.term = term;
        this.message = message;
    }

    public EventInfo(LoggedEvent event)
    {
        this.position = event.getPosition();
        this.term = event.getRaftTerm();
        this.message = bufferAsString(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());
    }

    public long getPosition()
    {
        return position;
    }

    public int getTerm()
    {
        return term;
    }

    public String getMessage()
    {
        return message;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        final EventInfo eventInfo = (EventInfo) o;
        return position == eventInfo.position && term == eventInfo.term && Objects.equals(message, eventInfo.message);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(position, term, message);
    }

    @Override
    public String toString()
    {
        return "EventInfo{" + "position=" + position + ", term=" + term + ", message='" + message + '\'' + '}';
    }
}

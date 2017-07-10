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
package io.zeebe.client.event.impl;

public class TopicSubscriberEvent
{

    protected SubscriberEventType eventType;
    protected long startPosition;
    protected String name;
    protected int prefetchCapacity;
    protected boolean forceStart;

    public TopicSubscriberEvent()
    {
        reset();
    }

    public long getStartPosition()
    {
        return startPosition;
    }

    public void setStartPosition(long startPosition)
    {
        this.startPosition = startPosition;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public void setPrefetchCapacity(int prefetchCapacity)
    {
        this.prefetchCapacity = prefetchCapacity;
    }

    public int getPrefetchCapacity()
    {
        return prefetchCapacity;
    }

    public void setEventType(SubscriberEventType event)
    {
        this.eventType = event;
    }

    public SubscriberEventType getEventType()
    {
        return eventType;
    }

    public boolean isForceStart()
    {
        return forceStart;
    }

    public void setForceStart(boolean forceStart)
    {
        this.forceStart = forceStart;
    }

    public void reset()
    {
        this.startPosition = -1L;
        this.prefetchCapacity = -1;
        this.eventType = null;
        this.name = null;
        this.forceStart = false;
    }
}

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
package io.zeebe.client.event.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.impl.cmd.ReceiverAwareResponseResult;
import io.zeebe.client.task.impl.subscription.EventSubscriptionCreationResult;
import io.zeebe.transport.RemoteAddress;

public class TopicSubscriberEvent extends EventImpl implements EventSubscriptionCreationResult, ReceiverAwareResponseResult
{

    protected long startPosition = -1L;
    protected String name;
    protected int prefetchCapacity = -1;
    protected boolean forceStart;

    protected RemoteAddress remote;

    @JsonCreator
    public TopicSubscriberEvent(@JsonProperty("state") String state)
    {
        super(TopicEventType.SUBSCRIBER, state);
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

    public boolean isForceStart()
    {
        return forceStart;
    }

    public void setForceStart(boolean forceStart)
    {
        this.forceStart = forceStart;
    }

    @Override
    public void setReceiver(RemoteAddress receiver)
    {
        this.remote = receiver;
    }

    @Override
    @JsonIgnore
    public RemoteAddress getEventPublisher()
    {
        return remote;
    }

    @Override
    @JsonIgnore
    public long getSubscriberKey()
    {
        return metadata.getKey();
    }

    @Override
    @JsonIgnore
    public int getPartitionId()
    {
        return metadata.getPartitionId();
    }
}

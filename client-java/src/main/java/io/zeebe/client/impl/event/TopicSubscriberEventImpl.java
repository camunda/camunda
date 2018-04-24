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
package io.zeebe.client.impl.event;

import com.fasterxml.jackson.annotation.*;
import io.zeebe.client.api.events.TopicSubscriberEvent;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.ReceiverAwareResponseResult;
import io.zeebe.client.impl.record.TopicSubscriberRecordImpl;
import io.zeebe.client.impl.subscription.EventSubscriptionCreationResult;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.transport.RemoteAddress;

public class TopicSubscriberEventImpl extends TopicSubscriberRecordImpl implements TopicSubscriberEvent, EventSubscriptionCreationResult, ReceiverAwareResponseResult
{
    private RemoteAddress remote;

    @JsonCreator
    public TopicSubscriberEventImpl(@JacksonInject ZeebeObjectMapper objectMapper)
    {
        super(objectMapper, RecordType.EVENT);
    }

    @JsonIgnore
    @Override
    public TopicSubscriberState getState()
    {
        return TopicSubscriberState.valueOf(getMetadata().getIntent());
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
        return getKey();
    }

    @Override
    @JsonIgnore
    public int getPartitionId()
    {
        return getMetadata().getPartitionId();
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("TopicSubscriberEvent [state=");
        builder.append(getState());
        builder.append(", name=");
        builder.append(getName());
        builder.append(", startPosition=");
        builder.append(getStartPosition());
        builder.append(", isForceStart=");
        builder.append(isForceStart());
        builder.append(", prefetchCapacit)=");
        builder.append(getPrefetchCapacity());
        builder.append("]");
        return builder.toString();
    }

}

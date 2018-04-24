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
import io.zeebe.client.api.events.TopicSubscriptionEvent;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.record.TopicSubscriptionRecordImpl;
import io.zeebe.protocol.clientapi.RecordType;

public class TopicSubscriptionEventImpl extends TopicSubscriptionRecordImpl implements TopicSubscriptionEvent
{
    @JsonCreator
    public TopicSubscriptionEventImpl(@JacksonInject ZeebeObjectMapper objectMapper)
    {
        super(objectMapper, RecordType.EVENT);
    }

    @JsonIgnore
    @Override
    public TopicSubscriptionState getState()
    {
        return TopicSubscriptionState.valueOf(getMetadata().getIntent());
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("TopicSubscriptionrEvent [state=");
        builder.append(getState());
        builder.append(", name=");
        builder.append(getName());
        builder.append(", ackPosition=");
        builder.append(getAckPosition());
        builder.append("]");
        return builder.toString();
    }

}

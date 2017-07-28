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
import com.fasterxml.jackson.annotation.JsonProperty;

import io.zeebe.client.event.TopicEventType;

public class TopicSubscriptionEvent extends EventImpl
{
    protected String name;
    protected long ackPosition = -1L;

    @JsonCreator
    public TopicSubscriptionEvent(@JsonProperty("state") String state)
    {
        super(TopicEventType.SUBSCRIPTION, state);
    }

    public String getName()
    {
        return name;
    }

    public void setName(String subscriptionName)
    {
        this.name = subscriptionName;
    }

    public long getAckPosition()
    {
        return ackPosition;
    }

    public void setAckPosition(long ackPosition)
    {
        this.ackPosition = ackPosition;
    }

}

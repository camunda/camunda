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
package io.zeebe.client.topic.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.event.impl.EventImpl;

// TODO: rename the current TopicEvent to something that reflects its purpose (=> that it is "raw")
public class NewTopicEventImpl extends EventImpl
{
    protected final String name;
    protected final int partitions;

    @JsonCreator
    public NewTopicEventImpl(
            @JsonProperty("state") String state,
            @JsonProperty("name") String name,
            @JsonProperty("partitions") int partitions)
    {
        super(TopicEventType.TOPIC, state);
        this.name = name;
        this.partitions = partitions;
    }

    public String getName()
    {
        return name;
    }

    public int getPartitions()
    {
        return partitions;
    }

}

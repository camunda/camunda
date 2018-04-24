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

import io.zeebe.client.event.Event;
import io.zeebe.client.event.impl.EventImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.cmd.CommandImpl;
import io.zeebe.protocol.Protocol;
import io.zeebe.util.EnsureUtil;

public class CreateTopicCommandImpl extends CommandImpl<Event>
{

    protected final TopicEventImpl event;

    public CreateTopicCommandImpl(RequestManager client, String name, int partitions, int replicationFactor)
    {
        super(client);
        EnsureUtil.ensureNotNull("name", name);

        this.event = new TopicEventImpl(TopicEventType.CREATE.name(), name, partitions, replicationFactor);
        this.event.setTopicName(Protocol.SYSTEM_TOPIC);
        this.event.setPartitionId(Protocol.SYSTEM_PARTITION);
    }


    @Override
    public EventImpl getEvent()
    {
        return event;
    }

    @Override
    public String getExpectedStatus()
    {
        return TopicEventType.CREATING.name();
    }

}

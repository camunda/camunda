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
package io.zeebe.client.impl.topic;

import io.zeebe.client.api.commands.CreateTopicCommandStep1;
import io.zeebe.client.api.commands.CreateTopicCommandStep1.*;
import io.zeebe.client.api.events.TopicEvent;
import io.zeebe.client.impl.CommandImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.command.TopicCommandImpl;
import io.zeebe.client.impl.record.RecordImpl;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.intent.TopicIntent;
import io.zeebe.util.EnsureUtil;

public class CreateTopicCommandImpl extends CommandImpl<TopicEvent> implements CreateTopicCommandStep1, CreateTopicCommandStep2, CreateTopicCommandStep3, CreateTopicCommandStep4
{
    private final TopicCommandImpl command = new TopicCommandImpl(TopicIntent.CREATE);

    public CreateTopicCommandImpl(RequestManager client)
    {
        super(client);

        this.command.setTopicName(Protocol.SYSTEM_TOPIC);
        this.command.setPartitionId(Protocol.SYSTEM_PARTITION);
    }

    @Override
    public CreateTopicCommandStep2 name(String topicName)
    {
        EnsureUtil.ensureNotNull("name", topicName);

        this.command.setName(topicName);
        return this;
    }

    @Override
    public CreateTopicCommandStep3 partitions(int partitions)
    {
        EnsureUtil.ensureGreaterThan("partitions", partitions, 0);
        this.command.setPartitions(partitions);
        return this;
    }

    @Override
    public CreateTopicCommandStep4 replicationFactor(int replicationFactor)
    {
        EnsureUtil.ensureGreaterThan("replicationFactor", replicationFactor, 0);
        this.command.setReplicationFactor(replicationFactor);
        return this;
    }

    @Override
    public RecordImpl getCommand()
    {
        return command;
    }

}

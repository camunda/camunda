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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.cmd.AbstractControlMessageWithoutResponseCmd;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.util.EnsureUtil;

public class CloseTopicSubscriptionCmdImpl extends AbstractControlMessageWithoutResponseCmd<CloseSubscriptionRequest>
{

    protected CloseSubscriptionRequest request = new CloseSubscriptionRequest();
    protected long subscriberKey;

    public CloseTopicSubscriptionCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, MsgPackConverter msgPackConverter, final Topic topic)
    {
        super(commandManager, objectMapper, topic, CloseSubscriptionRequest.class, ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION);
    }

    @Override
    public void validate()
    {
        topic.validate();
        EnsureUtil.ensureGreaterThanOrEqual("subscriberKey", subscriberKey, 0);
    }

    public CloseTopicSubscriptionCmdImpl subscriberKey(final long subscriberKey)
    {
        this.subscriberKey = subscriberKey;
        return this;
    }

    @Override
    protected Object writeCommand()
    {
        request.setTopicName(topic.getTopicName());
        request.setPartitionId(topic.getPartitionId());
        request.setSubscriberKey(subscriberKey);

        return request;
    }

    @Override
    protected void reset()
    {
        request.reset();
    }

}

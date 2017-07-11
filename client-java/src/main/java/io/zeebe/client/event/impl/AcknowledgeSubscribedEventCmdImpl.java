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
import io.zeebe.client.impl.cmd.AbstractExecuteCmdImpl;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.util.EnsureUtil;

public class AcknowledgeSubscribedEventCmdImpl extends AbstractExecuteCmdImpl<TopicSubscriptionEvent, Long>
{
    protected final TopicSubscriptionEvent ack = new TopicSubscriptionEvent();

    public AcknowledgeSubscribedEventCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, MsgPackConverter msgPackConverter, final Topic topic)
    {
        super(commandManager, objectMapper, topic, TopicSubscriptionEvent.class, EventType.SUBSCRIPTION_EVENT);
    }

    @Override
    public void validate()
    {
        super.validate();
        EnsureUtil.ensureGreaterThanOrEqual("ackPosition", ack.getAckPosition(), 0L);
        EnsureUtil.ensureNotNull("subscriptionName", ack.getName());
    }

    public AcknowledgeSubscribedEventCmdImpl subscriptionName(String name)
    {
        this.ack.setName(name);
        return this;
    }

    public AcknowledgeSubscribedEventCmdImpl ackPosition(long position)
    {
        this.ack.setAckPosition(position);
        return this;
    }

    @Override
    protected Object writeCommand()
    {
        return ack;
    }

    @Override
    protected void reset()
    {
        ack.reset();
    }

    @Override
    protected long getKey()
    {
        return -1L;
    }

    @Override
    protected Long getResponseValue(long key, TopicSubscriptionEvent event)
    {
        return key;
    }

}

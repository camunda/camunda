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
package io.zeebe.client.impl.subscription;

import static io.zeebe.util.VarDataUtil.readBytes;

import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.impl.record.GeneralRecordImpl;
import io.zeebe.protocol.clientapi.*;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.transport.*;
import org.agrona.DirectBuffer;

public class SubscribedEventCollector implements ClientMessageHandler
{
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final SubscribedRecordDecoder subscribedRecordDecoder = new SubscribedRecordDecoder();

    private final SubscribedEventHandler eventHandler;
    private final MsgPackConverter converter;
    private final ZeebeObjectMapper objectMapper;

    public SubscribedEventCollector(
            SubscribedEventHandler eventHandler,
            MsgPackConverter converter,
            ZeebeObjectMapper objectMapper)
    {
        this.eventHandler = eventHandler;
        this.converter = converter;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean onMessage(ClientOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset,
            int length)
    {
        messageHeaderDecoder.wrap(buffer, offset);

        offset += MessageHeaderDecoder.ENCODED_LENGTH;

        final int templateId = messageHeaderDecoder.templateId();

        final boolean messageHandled;

        if (templateId == SubscribedRecordDecoder.TEMPLATE_ID)
        {

            subscribedRecordDecoder.wrap(buffer, offset, messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

            final int partitionId = subscribedRecordDecoder.partitionId();
            final long position = subscribedRecordDecoder.position();
            final long key = subscribedRecordDecoder.key();
            final long subscriberKey = subscribedRecordDecoder.subscriberKey();
            final RecordType recordType = subscribedRecordDecoder.recordType();
            final SubscriptionType subscriptionType = subscribedRecordDecoder.subscriptionType();
            final ValueType valueType = subscribedRecordDecoder.valueType();
            final Intent intent = Intent.fromProtocolValue(valueType, subscribedRecordDecoder.intent());

            final byte[] valueBuffer = readBytes(subscribedRecordDecoder::getValue, subscribedRecordDecoder::valueLength);

            final GeneralRecordImpl event = new GeneralRecordImpl(
                    objectMapper,
                    converter,
                    recordType,
                    valueType,
                    valueBuffer);

            event.setPartitionId(partitionId);
            event.setPosition(position);
            event.setKey(key);
            event.setIntent(intent);

            messageHandled = eventHandler.onEvent(subscriptionType, subscriberKey, event);
        }
        else
        {
            // ignoring
            messageHandled = true;
        }


        return messageHandled;
    }

}

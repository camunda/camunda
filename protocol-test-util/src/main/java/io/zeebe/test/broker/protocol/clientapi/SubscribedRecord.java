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
package io.zeebe.test.broker.protocol.clientapi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.io.DirectBufferInputStream;

import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.SubscribedRecordDecoder;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.buffer.BufferReader;

public class SubscribedRecord implements BufferReader
{
    protected final RawMessage rawMessage;

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected SubscribedRecordDecoder bodyDecoder = new SubscribedRecordDecoder();

    protected Map<String, Object> value;

    protected MsgPackHelper msgPackHelper = new MsgPackHelper();

    public SubscribedRecord(RawMessage rawMessage)
    {
        this.rawMessage = rawMessage;
        final DirectBuffer buffer = rawMessage.getMessage();
        wrap(buffer, 0, buffer.capacity());
    }

    public int partitionId()
    {
        return bodyDecoder.partitionId();
    }

    public long position()
    {
        return bodyDecoder.position();
    }

    public long key()
    {
        return bodyDecoder.key();
    }

    public long subscriberKey()
    {
        return bodyDecoder.subscriberKey();
    }

    public SubscriptionType subscriptionType()
    {
        return bodyDecoder.subscriptionType();
    }

    public ValueType valueType()
    {
        return bodyDecoder.valueType();
    }

    public RecordType recordType()
    {
        return bodyDecoder.recordType();
    }

    public Intent intent()
    {
        return bodyDecoder.intent();
    }

    public Map<String, Object> value()
    {
        return value;
    }

    public RawMessage getRawMessage()
    {
        return rawMessage;
    }

    @Override
    public void wrap(DirectBuffer responseBuffer, int offset, int length)
    {
        headerDecoder.wrap(responseBuffer, offset);

        if (headerDecoder.templateId() != bodyDecoder.sbeTemplateId())
        {
            throw new RuntimeException("Unexpected response from broker.");
        }

        bodyDecoder.wrap(responseBuffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        final int eventLength = bodyDecoder.valueLength();
        final int eventOffset = bodyDecoder.limit() + SubscribedRecordDecoder.valueHeaderLength();

        try (InputStream is = new DirectBufferInputStream(responseBuffer, offset + eventOffset, eventLength))
        {
            value = msgPackHelper.readMsgPack(is);
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

    }

}

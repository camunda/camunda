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
package io.zeebe.test.broker.protocol.brokerapi;

import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;

import io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.buffer.BufferReader;

public class ExecuteCommandRequest implements BufferReader
{

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ExecuteCommandRequestDecoder bodyDecoder = new ExecuteCommandRequestDecoder();

    protected final MsgPackHelper msgPackHelper;

    protected Map<String, Object> command;
    protected RemoteAddress source;

    public ExecuteCommandRequest(RemoteAddress source, MsgPackHelper msgPackHelper)
    {
        this.source = source;
        this.msgPackHelper = msgPackHelper;
    }

    public long sourceRecordPosition()
    {
        return bodyDecoder.sourceRecordPosition();
    }

    public long key()
    {
        return bodyDecoder.key();
    }

    public int partitionId()
    {
        return bodyDecoder.partitionId();
    }

    public long position()
    {
        return bodyDecoder.position();
    }

    public ValueType valueType()
    {
        return bodyDecoder.valueType();
    }

    public Intent intent()
    {
        return Intent.fromProtocolValue(valueType(), bodyDecoder.intent());
    }

    public Map<String, Object> getCommand()
    {
        return command;
    }

    public RemoteAddress getSource()
    {
        return source;
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        bodyDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        final int commandLength = bodyDecoder.valueLength();
        final int commandOffset = bodyDecoder.limit() + ExecuteCommandRequestDecoder.valueHeaderLength();

        command = msgPackHelper.readMsgPack(new DirectBufferInputStream(
                buffer,
                commandOffset,
                commandLength));
    }

}

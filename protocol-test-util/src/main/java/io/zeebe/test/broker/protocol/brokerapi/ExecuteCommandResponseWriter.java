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
import java.util.function.Function;

import org.agrona.MutableDirectBuffer;

import io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.EnsureUtil;

public class ExecuteCommandResponseWriter extends AbstractMessageBuilder<ExecuteCommandRequest>
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandResponseEncoder bodyEncoder = new ExecuteCommandResponseEncoder();
    protected final MsgPackHelper msgPackHelper;

    protected Function<ExecuteCommandRequest, Long> keyFunction = r -> r.key();
    protected Function<ExecuteCommandRequest, Integer> partitionIdFunction = r -> r.partitionId();
    protected Function<ExecuteCommandRequest, Map<String, Object>> eventFunction;
    protected Function<ExecuteCommandRequest, Long> positionFunction = r -> r.position();
    private Function<ExecuteCommandRequest, Intent> intentFunction = r -> r.intent();

    protected long key;
    protected int partitionId;
    protected byte[] value;
    protected long position;
    private RecordType recordType;
    private Intent intent;
    private ValueType valueType;

    public ExecuteCommandResponseWriter(MsgPackHelper msgPackHelper)
    {
        this.msgPackHelper = msgPackHelper;
    }

    @Override
    public void initializeFrom(ExecuteCommandRequest request)
    {
        key = keyFunction.apply(request);
        partitionId = partitionIdFunction.apply(request);
        position = positionFunction.apply(request);
        final Map<String, Object> deserializedEvent = eventFunction.apply(request);
        value = msgPackHelper.encodeAsMsgPack(deserializedEvent);
        this.valueType = request.valueType();
        this.intent = intentFunction.apply(request);
    }

    public void setPartitionIdFunction(Function<ExecuteCommandRequest, Integer> partitionIdFunction)
    {
        this.partitionIdFunction = partitionIdFunction;
    }

    public void setEventFunction(Function<ExecuteCommandRequest, Map<String, Object>> eventFunction)
    {
        this.eventFunction = eventFunction;
    }

    public void setRecordType(RecordType recordType)
    {
        this.recordType = recordType;
    }

    public void setKeyFunction(Function<ExecuteCommandRequest, Long> keyFunction)
    {
        this.keyFunction = keyFunction;
    }

    public void setPositionFunction(Function<ExecuteCommandRequest, Long> positionFunction)
    {
        this.positionFunction = positionFunction;
    }

    public void setIntentFunction(Function<ExecuteCommandRequest, Intent> intentFunction)
    {
        this.intentFunction = intentFunction;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                ExecuteCommandResponseEncoder.BLOCK_LENGTH +
                ExecuteCommandResponseEncoder.valueHeaderLength() +
                value.length;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        EnsureUtil.ensureNotNull("recordType", recordType);
        EnsureUtil.ensureNotNull("valueType", valueType);
        EnsureUtil.ensureNotNull("intent", intent);

        // protocol header
        headerEncoder
            .wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        // protocol message
        bodyEncoder
            .wrap(buffer, offset)
            .recordType(recordType)
            .valueType(valueType)
            .intent(intent.value())
            .partitionId(partitionId)
            .key(key)
            .position(position)
            .putValue(value, 0, value.length);

    }


}

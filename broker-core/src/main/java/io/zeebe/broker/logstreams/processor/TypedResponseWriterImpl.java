/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.processor;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.transport.ServerOutput;

public class TypedResponseWriterImpl implements TypedResponseWriter
{
    protected CommandResponseWriter writer;
    protected int partitionId;

    private final UnsafeBuffer stringWrapper = new UnsafeBuffer(0, 0);

    public TypedResponseWriterImpl(ServerOutput output, int partitionId)
    {
        this.writer = new CommandResponseWriter(output);
        this.partitionId = partitionId;
    }

    @Override
    public boolean writeRejection(TypedRecord<?> record, RejectionType rejectionType, String rejectionReason)
    {
        final byte[] bytes = rejectionReason.getBytes(StandardCharsets.UTF_8);
        stringWrapper.wrap(bytes);
        return write(RecordType.COMMAND_REJECTION, record.getMetadata().getIntent(), rejectionType, stringWrapper, record);
    }

    @Override
    public boolean writeRejection(TypedRecord<?> record, RejectionType type, DirectBuffer reason)
    {
        return write(RecordType.COMMAND_REJECTION, record.getMetadata().getIntent(), type, reason, record);
    }

    @Override
    public boolean writeEvent(Intent intent, TypedRecord<?> record)
    {
        stringWrapper.wrap(0, 0);
        return write(RecordType.EVENT, intent, RejectionType.NULL_VAL, stringWrapper, record);
    }

    private boolean write(
            RecordType type,
            Intent intent,
            RejectionType rejectionType,
            DirectBuffer rejectionReason,
            TypedRecord<?> record)
    {
        final RecordMetadata metadata = record.getMetadata();

        return writer
            .partitionId(partitionId)
            .position(0) // TODO: this depends on the value of written event => https://github.com/zeebe-io/zeebe/issues/374
            .key(record.getKey())
            .timestamp(record.getTimestamp())
            .intent(intent)
            .recordType(type)
            .valueType(metadata.getValueType())
            .rejectionType(rejectionType)
            .rejectionReason(rejectionReason)
            .valueWriter(record.getValue())
            .tryWriteResponse(metadata.getRequestStreamId(), metadata.getRequestId());
    }

}

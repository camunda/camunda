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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.log.LogStreamBatchWriter.LogEntryBuilder;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;

public class TypedStreamWriterImpl implements TypedStreamWriter, TypedBatchWriter
{
    protected final Consumer<BrokerEventMetadata> noop = m ->
    { };

    protected BrokerEventMetadata metadata = new BrokerEventMetadata();
    protected final Map<Class<? extends UnpackedObject>, EventType> typeRegistry;
    protected final LogStream stream;

    protected LogStreamWriter writer;
    protected LogStreamBatchWriter batchWriter;

    protected int producerId;
    protected int sourcePartitionId;
    protected long sourcePosition;

    public TypedStreamWriterImpl(
            LogStream stream,
            Map<EventType, Class<? extends UnpackedObject>> eventRegistry)
    {
        this.stream = stream;
        metadata.protocolVersion(Protocol.PROTOCOL_VERSION);
        this.writer = new LogStreamWriterImpl(stream);
        this.batchWriter = new LogStreamBatchWriterImpl(stream);
        this.typeRegistry = new HashMap<>();
        eventRegistry.forEach((e, c) -> typeRegistry.put(c, e));
    }

    public void configureSourceContext(int producerId, int sourcePartitionId, long sourcePosition)
    {
        this.producerId = producerId;
        this.sourcePartitionId = sourcePartitionId;
        this.sourcePosition = sourcePosition;
    }

    protected void initMetadata(UnpackedObject event)
    {
        metadata.reset();
        final EventType eventType = typeRegistry.get(event.getClass());

        metadata.eventType(eventType);
    }

    @Override
    public long writeFollowupEvent(long key, UnpackedObject event, Consumer<BrokerEventMetadata> additionalMetadata)
    {
        writer.reset();
        writer.producerId(producerId);

        if (sourcePartitionId >= 0)
        {
            writer.sourceEvent(sourcePartitionId, sourcePosition);
        }

        initMetadata(event);
        additionalMetadata.accept(metadata);

        if (key >= 0)
        {
            writer.key(key);
        }
        else
        {
            writer.positionAsKey();
        }

        return writer
            .metadataWriter(metadata)
            .valueWriter(event)
            .tryWrite();
    }

    @Override
    public long writeFollowupEvent(long key, UnpackedObject event)
    {
        return writeFollowupEvent(key, event, noop);
    }

    @Override
    public long writeNewEvent(UnpackedObject event, Consumer<BrokerEventMetadata> metadata)
    {
        return writeFollowupEvent(-1, event, metadata);
    }

    @Override
    public long writeNewEvent(UnpackedObject event)
    {
        return writeFollowupEvent(-1, event, noop);
    }

    @Override
    public TypedBatchWriter addFollowUpEvent(long key, UnpackedObject event, Consumer<BrokerEventMetadata> additionalMetadata)
    {
        initMetadata(event);
        additionalMetadata.accept(metadata);

        final LogEntryBuilder logEntryBuilder = batchWriter.event();

        if (key >= 0)
        {
            logEntryBuilder.key(key);
        }
        else
        {
            logEntryBuilder.positionAsKey();
        }

        logEntryBuilder
            .metadataWriter(metadata)
            .valueWriter(event)
            .done();

        return this;
    }

    @Override
    public TypedBatchWriter addFollowUpEvent(long key, UnpackedObject event)
    {
        return addFollowUpEvent(key, event, noop);
    }

    @Override
    public TypedBatchWriter addNewEvent(UnpackedObject event)
    {
        return addFollowUpEvent(-1, event, noop);
    }

    @Override
    public TypedBatchWriter addNewEvent(UnpackedObject event, Consumer<BrokerEventMetadata> metadata)
    {
        return addFollowUpEvent(-1, event, metadata);
    }

    @Override
    public long write()
    {
        return batchWriter.tryWrite();
    }

    @Override
    public TypedBatchWriter newBatch()
    {
        batchWriter.reset();
        batchWriter.producerId(producerId);

        if (sourcePartitionId >= 0)
        {
            batchWriter.sourceEvent(sourcePartitionId, sourcePosition);
        }

        return this;
    }

}

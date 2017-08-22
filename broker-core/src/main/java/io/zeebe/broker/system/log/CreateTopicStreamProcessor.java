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
package io.zeebe.broker.system.log;

import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.logstreams.processor.NoopSnapshotSupport;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;

public class CreateTopicStreamProcessor implements StreamProcessor, EventProcessor
{

    protected final BrokerEventMetadata sourceMetadata = new BrokerEventMetadata();
    protected final TopicEvent event = new TopicEvent();

    protected final BrokerEventMetadata targetMetadata = new BrokerEventMetadata()
            .eventType(EventType.TOPIC_EVENT)
            .protocolVersion(Protocol.PROTOCOL_VERSION);

    protected LogStream sourceStream;
    protected LogStream targetStream;

    protected LoggedEvent currentEvent;

    protected final CommandResponseWriter responseWriter;

    public CreateTopicStreamProcessor(CommandResponseWriter responseWriter)
    {
        this.responseWriter = responseWriter;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        this.sourceStream = context.getSourceStream();
        this.targetStream = context.getTargetStream();
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return new NoopSnapshotSupport();
    }

    @Override
    public EventProcessor onEvent(LoggedEvent currentEvent)
    {
        this.currentEvent = currentEvent;

        event.reset();
        sourceMetadata.reset();
        currentEvent.readValue(event);
        currentEvent.readMetadata(sourceMetadata);

        if (TopicState.CREATE == event.getState())
        {
            return this;
        }
        else
        {
            return null;
        }
    }

    @Override
    public void processEvent()
    {
        event.setState(TopicState.CREATE_REJECTED);
    }

    @Override
    public boolean executeSideEffects()
    {
        return responseWriter
            .topicName(sourceStream.getTopicName())
            .partitionId(sourceStream.getPartitionId())
            .position(currentEvent.getPosition())
            .key(currentEvent.getKey())
            .eventWriter(event)
            .tryWriteResponse(sourceMetadata.getRequestStreamId(), sourceMetadata.getRequestId());
    }

    @Override
    public long writeEvent(LogStreamWriter writer)
    {
        targetMetadata.raftTermId(targetStream.getTerm());

        return writer
            .key(currentEvent.getKey())
            .metadataWriter(targetMetadata)
            .valueWriter(event)
            .tryWrite();
    }

    public static MetadataFilter eventFilter()
    {
        return (m) -> m.getEventType() == EventType.TOPIC_EVENT;
    }

}

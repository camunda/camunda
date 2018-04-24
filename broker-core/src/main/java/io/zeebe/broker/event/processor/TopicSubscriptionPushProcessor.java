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
package io.zeebe.broker.event.processor;

import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;

import org.agrona.DirectBuffer;

import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.logstreams.processor.NoopSnapshotSupport;
import io.zeebe.broker.transport.clientapi.SubscribedRecordWriter;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.util.collection.LongRingBuffer;

public class TopicSubscriptionPushProcessor implements StreamProcessor, EventProcessor
{

    protected final RecordMetadata metadata = new RecordMetadata();

    protected LoggedEvent event;

    protected final int clientStreamId;
    protected final long subscriberKey;
    protected long startPosition;
    protected final DirectBuffer name;
    protected final String nameString;
    protected int logStreamPartitionId;

    protected final SnapshotSupport snapshotSupport = new NoopSnapshotSupport();
    protected final SubscribedRecordWriter channelWriter;

    protected LongRingBuffer pendingEvents;
    private StreamProcessorContext context;

    public TopicSubscriptionPushProcessor(
            int clientStreamId,
            long subscriberKey,
            long startPosition,
            DirectBuffer name,
            int prefetchCapacity,
            SubscribedRecordWriter channelWriter)
    {
        this.channelWriter = channelWriter;
        this.clientStreamId = clientStreamId;
        this.subscriberKey = subscriberKey;
        this.startPosition = startPosition;
        this.name = cloneBuffer(name);
        this.nameString = name.getStringWithoutLengthUtf8(0, name.capacity());

        if (prefetchCapacity > 0)
        {
            this.pendingEvents = new LongRingBuffer(prefetchCapacity);
        }
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        this.context = context;
        final LogStreamReader logReader = context.getLogStreamReader();

        final LogStream logStream = context.getLogStream();
        this.logStreamPartitionId = logStream.getPartitionId();

        setToStartPosition(logReader);
        context.suspendController();
    }

    /**
     * @return the position at which this processor actually started. This may be different than the constructor argument
     */
    public long getStartPosition()
    {
        return startPosition;
    }

    protected void setToStartPosition(LogStreamReader logReader)
    {
        if (startPosition >= 0)
        {
            logReader.seek(startPosition);
        }
        else
        {
            logReader.seekToLastEvent();

            if (logReader.hasNext())
            {
                logReader.next();
            }
        }

        startPosition = logReader.getPosition();
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return snapshotSupport;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        this.event = event;
        return this;
    }

    @Override
    public void processEvent()
    {
    }

    @Override
    public boolean executeSideEffects()
    {
        event.readMetadata(metadata);

        final boolean success = channelWriter
            .partitionId(logStreamPartitionId)
            .valueType(metadata.getValueType())
            .recordType(metadata.getRecordType())
            .intent(metadata.getIntent())
            .key(event.getKey())
            .position(event.getPosition())
            .subscriberKey(subscriberKey)
            .subscriptionType(SubscriptionType.TOPIC_SUBSCRIPTION)
            .value(event.getValueBuffer(), event.getValueOffset(), event.getValueLength())
            .tryWriteMessage(clientStreamId);

        if (success && recordsPendingEvents())
        {
            final boolean elementAdded = pendingEvents.addElementToHead(event.getPosition());
            if (!elementAdded)
            {
                throw new RuntimeException("Cannot record pending event " + elementAdded);
            }

            if (pendingEvents.isSaturated())
            {
                this.context.suspendController();
            }
        }

        return success;
    }

    public int getChannelId()
    {
        return clientStreamId;
    }

    public String getNameAsString()
    {
        return nameString;
    }

    public void onAck(long eventPosition)
    {
        context.getActorControl().call(() ->
        {
            if (recordsPendingEvents())
            {
                pendingEvents.consumeAscendingUntilInclusive(eventPosition);
                if (!pendingEvents.isSaturated())
                {
                    this.context.resumeController();
                }
            }
        });
    }

    /**
     * @return true if this subscription requires throttling
     */
    protected boolean recordsPendingEvents()
    {
        return pendingEvents != null;
    }

    public static MetadataFilter eventFilter()
    {
        return m ->
        {
            final ValueType eventType = m.getValueType();
            return
                    // don't push subscription or subscriber events;
                    // this may lead to infinite loops of pushing events that in turn trigger creation of more such events (e.g. ACKs)
                    eventType != ValueType.SUBSCRIPTION &&
                    eventType != ValueType.SUBSCRIBER &&
                    // don't push noop events as they are rather an implementation detail of raft
                    eventType != ValueType.NOOP;
        };
    }

    public DirectBuffer getName()
    {
        return name;
    }

    public long getSubscriptionId()
    {
        return subscriberKey;
    }

    public void enable()
    {
        context.getActorControl().call(() ->
        {
            this.context.resumeController();
        });
    }
}

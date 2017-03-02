package org.camunda.tngp.broker.event.processor;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventFilter;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.spi.SnapshotPositionProvider;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.buffer.BufferUtil;

public class TopicSubscriptionProcessor implements StreamProcessor, EventProcessor, SnapshotPositionProvider
{

    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();
    protected final TopicSubscriptionAck ack = new TopicSubscriptionAck();

    protected LoggedEvent event;

    protected final int channelId;
    protected final int logStreamId;
    protected final long subscriptionId;
    protected final long startPosition;
    protected final String name;
    protected final MutableDirectBuffer nameBuffer;

    protected final SubscribedEventWriter channelWriter;
    protected DeferredCommandContext cmdQueue;
    protected LogStreamWriter streamWriter;

    protected CompletableFuture<Void> ackFuture;
    protected long lastAck;
    protected long pendingAck;


    public TopicSubscriptionProcessor(
            int channelId,
            int logStreamId,
            long subscriptionId,
            long startPosition,
            String name,
            SubscribedEventWriter channelWriter)
    {
        this.channelWriter = channelWriter;
        this.channelId = channelId;
        this.logStreamId = logStreamId;
        this.subscriptionId = subscriptionId;
        this.startPosition = startPosition;
        this.name = name;
        this.nameBuffer = new UnsafeBuffer(name.getBytes(StandardCharsets.UTF_8));
    }

    protected final RecordingSnapshotSupport recoveryListener = new RecordingSnapshotSupport();

    @Override
    public void onOpen(StreamProcessorContext context)
    {

        cmdQueue = context.getStreamProcessorCmdQueue();
        streamWriter = context.getLogStreamWriter();
        final LogStreamReader logReader = context.getSourceLogStreamReader();

        if (!recoveryListener.hasRecovered())
        {
            setToStartPosition(logReader);
        }

        try
        {
            this.lastAck = logReader.getPosition();
        }
        catch (NoSuchElementException e)
        {
            this.lastAck = 0L;
        }

        if (this.lastAck > 0)
        {
            // logReader.getPosition() is the position of the event that we are pushing next;
            // i.e. the event before that is the last acknowledged
            this.lastAck = lastAck - 1;
        }
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
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return recoveryListener;
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
        return channelWriter.channelId(channelId)
            .eventType(metadata.getEventType())
            .longKey(event.getLongKey())
            .position(event.getPosition())
            .topicId(logStreamId)
            .subscriptionId(subscriptionId)
            .subscriptionType(SubscriptionType.TOPIC_SUBSCRIPTION)
            .event(event.getValueBuffer(), event.getValueOffset(), event.getValueLength())
            .tryWriteMessage();
    }

    public int getLogStreamId()
    {
        return logStreamId;
    }

    public int getChannelId()
    {
        return channelId;
    }

    public SubscribedEventWriter getChannelWriter()
    {
        return channelWriter;
    }

    public String getName()
    {
        return name;
    }

    public CompletableFuture<Void> acknowledgeEventPosition(long eventPosition)
    {
        return cmdQueue.<Void>runAsync((future) ->
        {
            if (ackFuture != null)
            {
                throw new RuntimeException("Cannot acknowledge; acknowledgement currently in progress; " +
                        "subscription id " + subscriptionId + "; ack position " + eventPosition);
            }

            metadata.reset();
            metadata.eventType(EventType.SUBSCRIPTION_EVENT);
            metadata.subscriptionId(subscriptionId);

            ack.reset();
            ack
                .subscriptionName(nameBuffer, 0, nameBuffer.capacity())
                .ackPosition(eventPosition);

            streamWriter
                .positionAsKey()
                .sourceEvent(logStreamId, eventPosition)
                .metadataWriter(metadata)
                .valueWriter(ack)
                .tryWrite();

            ackFuture = future;
        })
        .thenAccept((v) ->
        {
            lastAck = eventPosition;
        });
    }

    public void resolveAck()
    {

        cmdQueue.runAsync(() ->
        {
            if (ackFuture != null)
            {
                ackFuture.complete(null);
                ackFuture = null;
            }
        });
    }

    public EventFilter reprocessingFilter()
    {
        return (e) ->
        {
            ack.reset();
            e.readValue(ack);
            return BufferUtil.contentsEqual(nameBuffer, ack.getSubscriptionName());
        };
    }

    @Override
    public long getSnapshotPosition(LoggedEvent lastProcessedEvent, long lastWrittenEventPosition)
    {
        return lastAck;
    }

    public static MetadataFilter eventFilter()
    {
        // don't push ACKs; this may lead to infinite loops of pushing events and ACKing those events
        return (m) -> m.getEventType() != EventType.SUBSCRIPTION_EVENT;
    }
}

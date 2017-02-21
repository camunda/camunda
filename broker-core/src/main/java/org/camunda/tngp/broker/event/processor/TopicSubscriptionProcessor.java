package org.camunda.tngp.broker.event.processor;

import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.logstreams.processor.NoopSnapshotSupport;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;

public class TopicSubscriptionProcessor implements StreamProcessor, EventProcessor
{

    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();

    protected LoggedEvent event;

    protected final int channelId;
    protected final int logStreamId;
    protected final long subscriptionId;

    protected final SubscribedEventWriter channelWriter;

    public TopicSubscriptionProcessor(int channelId, int logStreamId, long subscriptionId, SubscribedEventWriter channelWriter)
    {
        this.channelWriter = channelWriter;
        this.channelId = channelId;
        this.logStreamId = logStreamId;
        this.subscriptionId = subscriptionId;
    }

    protected final NoopSnapshotSupport noopSnapshotSupport = new NoopSnapshotSupport();

    @Override
    public SnapshotSupport getStateResource()
    {
        return noopSnapshotSupport;
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

    public static MetadataFilter eventFilter()
    {
        // ignoring raft events, as they are not related to any client API concept and are sbe encoded
        return (m) -> m.getEventType() != EventType.RAFT_EVENT;
    }
}

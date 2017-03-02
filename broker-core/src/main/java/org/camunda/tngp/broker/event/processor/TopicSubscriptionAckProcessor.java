package org.camunda.tngp.broker.event.processor;

import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.logstreams.processor.NoopSnapshotSupport;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.protocol.clientapi.EventType;

public class TopicSubscriptionAckProcessor implements StreamProcessor
{

    protected final TopicSubscriptionManager subscriptionManager;
    protected final TopicSubscriptionAck ack = new TopicSubscriptionAck();
    protected final SnapshotSupport snapshotResource = new NoopSnapshotSupport();
    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();

    public TopicSubscriptionAckProcessor(TopicSubscriptionManager subscriptionManager)
    {
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        // always begin at the end of the log
        context.getSourceLogStreamReader().seekToLastEvent();
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return snapshotResource;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        metadata.reset();
        metadata.wrap(event.getMetadata(), event.getMetadataOffset(), event.getMetadataLength());

        subscriptionManager.resolveAck(metadata.getSubscriptionId());

        return null;
    }

    public static MetadataFilter filter()
    {
        return (m) -> EventType.SUBSCRIPTION_EVENT == m.getEventType();
    }

}

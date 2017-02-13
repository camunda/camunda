package org.camunda.tngp.broker.event.processor;

import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.BrokerStreamProcessor;
import org.camunda.tngp.broker.logstreams.processor.NoopSnapshotSupport;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.protocol.clientapi.EventType;

public class TopicSubscriptionProcessor extends BrokerStreamProcessor implements EventProcessor
{

    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();

    protected LoggedEvent event;

    protected int channelId;
    protected int logStreamId;

    protected final SubscribedEventWriter channelWriter;

    public TopicSubscriptionProcessor(int channelId, int logStreamId, SubscribedEventWriter channelWriter)
    {
        // TODO: need dynamic filtering
        super(EventType.NULL_VAL);
        this.channelWriter = channelWriter;
        this.channelId = channelId;
        this.logStreamId = logStreamId;
    }

    protected final NoopSnapshotSupport noopSnapshotSupport = new NoopSnapshotSupport();

    @Override
    public SnapshotSupport getStateResource()
    {
        return noopSnapshotSupport;
    }

    @Override
    public EventProcessor onCheckedEvent(LoggedEvent event)
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
        // TODO: must this also return the subscription id so that the client
        // can assign the message to the correct subscription? (yes, if there can be multiple subscriptions per channel?)
        event.readMetadata(metadata);
        return channelWriter.channelId(channelId)
            .eventType(metadata.getEventType())
            .longKey(event.getLongKey())
            .position(event.getPosition())
            .topicId(logStreamId)
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
}

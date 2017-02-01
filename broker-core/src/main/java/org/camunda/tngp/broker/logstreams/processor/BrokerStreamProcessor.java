package org.camunda.tngp.broker.logstreams.processor;

import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.protocol.clientapi.EventType;

/**
 * Wraps another {@link StreamProcessor} and implements guarding behavior on top of it
 *
 * @author Lindhauer
 */
public abstract class BrokerStreamProcessor implements StreamProcessor
{
    protected BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

    protected final EventType processableEventType;

    public BrokerStreamProcessor(final EventType processableEventType)
    {
        this.processableEventType = processableEventType;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        event.readMetadata(sourceEventMetadata);

        final int protocolVersion = sourceEventMetadata.getProtocolVersion();
        if (protocolVersion > Constants.PROTOCOL_VERSION)
        {
            throw new RuntimeException(String.format("Cannot handle event with version newer " +
                    "than what is implemented by broker (%d > %d)", protocolVersion, Constants.PROTOCOL_VERSION));
        }

        EventProcessor eventProcessor = null;

        if (canProcessEventType(sourceEventMetadata.getEventType()))
        {
            eventProcessor = onCheckedEvent(event);
        }

        return eventProcessor;
    }

    protected boolean canProcessEventType(EventType eventType)
    {
        return processableEventType == eventType;
    }

    protected abstract EventProcessor onCheckedEvent(LoggedEvent event);

}

package org.camunda.tngp.broker.logstreams.processor;

import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;

/**
 * Wraps another {@link StreamProcessor} and implements guarding behavior on top of it
 *
 * @author Lindhauer
 */
public abstract class BrokerStreamProcessor implements StreamProcessor
{

    protected BrokerEventMetadata sourceEventMetadata = new BrokerEventMetadata();
    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();

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

        return onCheckedEvent(event);
    }

    protected abstract EventProcessor onCheckedEvent(LoggedEvent event);


}

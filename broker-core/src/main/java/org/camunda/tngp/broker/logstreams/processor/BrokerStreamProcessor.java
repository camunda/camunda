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

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        event.readMetadata(sourceEventMetadata);

        if (sourceEventMetadata.getProtocolVersion() > Constants.PROTOCOL_VERSION)
        {
            // TODO: throw exception defined in log
        }

        return onCheckedEvent(event);
    }

    protected abstract EventProcessor onCheckedEvent(LoggedEvent event);


}

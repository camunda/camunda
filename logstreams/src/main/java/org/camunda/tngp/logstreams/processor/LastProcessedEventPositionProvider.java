package org.camunda.tngp.logstreams.processor;

import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.spi.SnapshotPositionProvider;

public class LastProcessedEventPositionProvider implements SnapshotPositionProvider
{

    @Override
    public long getSnapshotPosition(LoggedEvent lastProcessedEvent, long lastWrittenEventPosition)
    {
        return lastProcessedEvent.getPosition();
    }

}

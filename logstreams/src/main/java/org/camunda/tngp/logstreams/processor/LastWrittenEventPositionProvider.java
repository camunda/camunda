package org.camunda.tngp.logstreams.processor;

import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.spi.SnapshotPositionProvider;

public class LastWrittenEventPositionProvider implements SnapshotPositionProvider
{

    @Override
    public long getSnapshotPosition(LoggedEvent lastProcessedEvent, long lastWrittenEventPosition)
    {
        return lastWrittenEventPosition;
    }

}

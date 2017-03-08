package org.camunda.tngp.logstreams.spi;

import org.camunda.tngp.logstreams.log.LoggedEvent;

public interface SnapshotPositionProvider
{

    /**
     * @return position for which to write a snapshot
     */
    long getSnapshotPosition(LoggedEvent lastProcessedEvent, long lastWrittenEventPosition);
}

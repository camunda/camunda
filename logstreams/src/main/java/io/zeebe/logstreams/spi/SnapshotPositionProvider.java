package io.zeebe.logstreams.spi;

import io.zeebe.logstreams.log.LoggedEvent;

public interface SnapshotPositionProvider
{

    /**
     * @return position for which to write a snapshot
     */
    long getSnapshotPosition(LoggedEvent lastProcessedEvent, long lastWrittenEventPosition);
}

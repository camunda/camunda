package io.zeebe.logstreams.processor;

import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.SnapshotPositionProvider;

public class LastProcessedEventPositionProvider implements SnapshotPositionProvider
{

    @Override
    public long getSnapshotPosition(LoggedEvent lastProcessedEvent, long lastWrittenEventPosition)
    {
        return lastProcessedEvent.getPosition();
    }

}

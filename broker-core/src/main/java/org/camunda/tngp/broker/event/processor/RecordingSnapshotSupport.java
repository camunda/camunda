package org.camunda.tngp.broker.event.processor;

import java.io.InputStream;
import java.io.OutputStream;

import org.camunda.tngp.logstreams.spi.SnapshotSupport;

public class RecordingSnapshotSupport implements SnapshotSupport
{

    protected boolean hasRecovered;

    @Override
    public void writeSnapshot(OutputStream outputStream) throws Exception
    {
    }

    @Override
    public void recoverFromSnapshot(InputStream inputStream) throws Exception
    {
        hasRecovered = true;
    }

    @Override
    public void reset()
    {
        hasRecovered = false;
    }

    public boolean hasRecovered()
    {
        return hasRecovered;
    }

}

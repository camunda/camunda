package org.camunda.tngp.logstreams.spi;

import java.io.IOException;
import java.io.OutputStream;

public interface SnapshotWriter
{
    OutputStream getOutputStream();

    void commit() throws IOException;

    void abort();
}

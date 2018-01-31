package io.zeebe.broker.clustering.management;

import io.zeebe.logstreams.log.LogStream;

public interface OnOpenLogStreamListener
{
    void onOpen(LogStream logStream);
}

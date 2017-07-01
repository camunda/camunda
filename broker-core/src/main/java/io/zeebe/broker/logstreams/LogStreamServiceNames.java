package io.zeebe.broker.logstreams;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.servicecontainer.ServiceName;

public class LogStreamServiceNames
{
    public static final ServiceName<LogStreamsManager> LOG_STREAMS_MANAGER_SERVICE = ServiceName.newServiceName("logstreams.manager", LogStreamsManager.class);
    public static final ServiceName<SnapshotStorage> SNAPSHOT_STORAGE_SERVICE = ServiceName.newServiceName("snapshot.storage", SnapshotStorage.class);
    public static final ServiceName<LogStream> LOG_STREAM_SERVICE_GROUP = ServiceName.newServiceName("log.service", LogStream.class);

    public static final ServiceName<LogStream> logStreamServiceName(String logName)
    {
        return ServiceName.newServiceName(String.format("log.%s", logName), LogStream.class);
    }

}

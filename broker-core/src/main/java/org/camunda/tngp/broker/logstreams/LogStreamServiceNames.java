package org.camunda.tngp.broker.logstreams;

import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.servicecontainer.ServiceName;

public class LogStreamServiceNames
{
    public static final ServiceName<LogStreamsManagerService> LOG_STREAMS_MANAGER_SERVICE = ServiceName.newServiceName("logstreams.manager", LogStreamsManagerService.class);
    public static final ServiceName<SnapshotStorage> SNAPSHOT_STORAGE_SERVICE = ServiceName.newServiceName("snapshot.storage", SnapshotStorage.class);
    public static final ServiceName<LogStream> LOG_STREAM_SERVICE_GROUP = ServiceName.newServiceName("log.service", LogStream.class);

    public static final ServiceName<LogStream> logStreamServiceName(String logName)
    {
        return ServiceName.newServiceName(String.format("log.%s", logName), LogStream.class);
    }

}

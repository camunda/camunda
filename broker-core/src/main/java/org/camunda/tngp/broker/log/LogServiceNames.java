package org.camunda.tngp.broker.log;

import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.servicecontainer.ServiceName;

public class LogServiceNames
{
    public static final ServiceName<LogManager> LOG_MANAGER_SERVICE = ServiceName.newServiceName("log.manager", LogManager.class);
    public static final ServiceName<LogStream> LOG_SERVICE_GROUP = ServiceName.newServiceName("log.service", LogStream.class);

    public static final ServiceName<LogStream> logServiceName(String logName)
    {
        return ServiceName.newServiceName(String.format("log.%s", logName), LogStream.class);
    }

}

package org.camunda.tngp.broker.log;

import org.camunda.tngp.broker.servicecontainer.ServiceName;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogAgentContext;

public class LogServiceNames
{
    public final static ServiceName<LogManager> LOG_MANAGER_SERVICE = ServiceName.newServiceName("log.manager", LogManager.class);
    public final static ServiceName<Dispatcher> LOG_WRITE_BUFFER_SERVICE = ServiceName.newServiceName("log.writebuffer", Dispatcher.class);
    public final static ServiceName<LogAgentContext> LOG_AGENT_CONTEXT_SERVICE = ServiceName.newServiceName("log.agent-context", LogAgentContext.class);

    public final static ServiceName<Log> logServiceName(String logName)
    {
        return ServiceName.newServiceName(String.format("log.%s", logName), Log.class);
    }

}

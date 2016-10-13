package org.camunda.tngp.broker.log;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.impl.agent.LogAgentContext;
import org.camunda.tngp.servicecontainer.ServiceName;

public class LogServiceNames
{
    public static final ServiceName<LogManager> LOG_MANAGER_SERVICE = ServiceName.newServiceName("log.manager", LogManager.class);
    public static final ServiceName<Dispatcher> LOG_WRITE_BUFFER_SERVICE = ServiceName.newServiceName("log.writebuffer", Dispatcher.class);
    public static final ServiceName<LogAgentContext> LOG_AGENT_CONTEXT_SERVICE = ServiceName.newServiceName("log.agent-context", LogAgentContext.class);

    public static final ServiceName<Log> logServiceName(String logName)
    {
        return ServiceName.newServiceName(String.format("log.%s", logName), Log.class);
    }

}

package org.camunda.tngp.broker.system;

import org.camunda.tngp.broker.services.Counters;
import org.camunda.tngp.broker.system.executor.ScheduledExecutor;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.servicecontainer.ServiceName;

public class SystemServiceNames
{
    public static final ServiceName<AgentRunnerServices> AGENT_RUNNER_SERVICE = ServiceName.newServiceName("broker.agentrunner", AgentRunnerServices.class);

    public static final ServiceName<Counters> COUNTERS_MANAGER_SERVICE = ServiceName.newServiceName("broker.countersManager", Counters.class);

    public static final ServiceName<ScheduledExecutor> EXECUTOR_SERVICE = ServiceName.newServiceName("broker.executor", ScheduledExecutor.class);
}

package org.camunda.tngp.broker.system;

import org.camunda.tngp.broker.services.Counters;
import org.camunda.tngp.broker.system.threads.AgentRunnerService;
import org.camunda.tngp.servicecontainer.ServiceName;

public class SystemServiceNames
{
    public static final ServiceName<AgentRunnerService> AGENT_RUNNER_SERVICE = ServiceName.newServiceName("broker.agentrunner", AgentRunnerService.class);

    public static final ServiceName<Counters> COUNTERS_MANAGER_SERVICE = ServiceName.newServiceName("broker.countersManager", Counters.class);
}

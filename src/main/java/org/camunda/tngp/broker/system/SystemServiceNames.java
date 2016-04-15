package org.camunda.tngp.broker.system;

import org.camunda.tngp.broker.servicecontainer.ServiceName;
import org.camunda.tngp.broker.system.threads.AgentRunnerService;

public class SystemServiceNames
{
    public final static ServiceName<AgentRunnerService> AGENT_RUNNER_SERVICE = ServiceName.newServiceName("broker.agentrunner", AgentRunnerService.class);
}

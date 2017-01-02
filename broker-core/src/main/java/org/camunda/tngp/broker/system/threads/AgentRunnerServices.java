package org.camunda.tngp.broker.system.threads;

import org.camunda.tngp.util.agent.AgentRunnerService;

public interface AgentRunnerServices
{

    AgentRunnerService networkReceiverAgentRunnerService();

    AgentRunnerService networkSenderAgentRunnerService();

    AgentRunnerService logAppenderAgentRunnerService();

    AgentRunnerService logStreamProcessorAgentRunnerService();

    AgentRunnerService conductorAgentRunnerSerive();

}

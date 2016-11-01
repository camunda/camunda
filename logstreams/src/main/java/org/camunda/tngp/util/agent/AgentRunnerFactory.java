package org.camunda.tngp.util.agent;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;

public interface AgentRunnerFactory
{
    AgentRunner createAgentRunner(Agent agent);
}

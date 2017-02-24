package org.camunda.tngp.util.agent;

import org.agrona.concurrent.Agent;

public interface AgentRunnerService extends AutoCloseable
{
    void run(Agent agent);

    void remove(Agent agent);
}

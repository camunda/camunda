package org.camunda.tngp.broker.system.threads;

import org.agrona.concurrent.Agent;

public interface AgentRunnerService
{
    void runConductorAgent(Agent agent);

    void runLogAgent(Agent agent);

    void runNetworkingAgent(Agent agent);

    void runWorkerAgent(Agent agent);

    void removeConductorAgent(Agent agent);

    void removeNetworkingAgent(Agent agent);

    void removeLogAgent(Agent agent);

    void removeWorkerAgent(Agent agent);
}

package org.camunda.tngp.broker.system.threads;

import uk.co.real_logic.agrona.concurrent.Agent;

public interface AgentRunnerService
{
    public void runConductorAgent(Agent agent);

    public void runIoAgent(Agent agent);

    public void runWorkerAgent(Agent agent);

    public void removeConductorAgent(Agent agent);

    public void removeIoAgent(Agent agent);

    public void removeWorkerAgent(Agent agent);
}

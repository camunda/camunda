package org.camunda.tngp.broker.system.threads;

import uk.co.real_logic.agrona.concurrent.Agent;

public interface AgentRunnerService
{
    public void runConductorAgent(Agent agent);

    public void runLogAgent(Agent agent);

    public void runNetworkingAgent(Agent agent);

    public void runWorkerAgent(Agent agent);

    public void removeConductorAgent(Agent agent);

    public void removeNetworkingAgent(Agent agent);

    public void removeLogAgent(Agent agent);

    public void removeWorkerAgent(Agent agent);
}

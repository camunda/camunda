package org.camunda.tngp.broker.system.threads;

import java.util.concurrent.CopyOnWriteArrayList;

import uk.co.real_logic.agrona.concurrent.Agent;

public class AgentGroup implements Agent
{
    protected final CopyOnWriteArrayList<Agent> agents = new CopyOnWriteArrayList<>();
    protected final String roleName;

    public AgentGroup(String groupName)
    {
        this.roleName = groupName;
    }

    public void addAgent(Agent a)
    {
        agents.add(a);
    }

    public int doWork() throws Exception
    {
        int sum = 0;
        for (int i = 0; i < agents.size(); i++)
        {
            sum += agents.get(i).doWork();
        }

        return sum;
    }

    public void onClose()
    {
        for (final Agent agent : agents)
        {
            removeAgent(agent);
        }
    }

    public String roleName()
    {
        return roleName;
    }

    public void removeAgent(Agent a)
    {
        a.onClose();
        agents.remove(a);
    }

}

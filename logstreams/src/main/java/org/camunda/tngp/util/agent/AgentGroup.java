package org.camunda.tngp.util.agent;

import java.util.ArrayList;
import java.util.Arrays;

import org.agrona.concurrent.Agent;

public class AgentGroup implements Agent
{
    protected volatile Agent[] agents = new Agent[0];

    protected final String roleName;

    public AgentGroup(String groupName)
    {
        this.roleName = groupName;
    }

    public synchronized void addAgent(Agent a)
    {
        final int currentLength = agents.length;
        final int newLength = currentLength + 1;

        final Agent[] newArray = new Agent[newLength];
        System.arraycopy(agents, 0, newArray, 0, currentLength);

        newArray[currentLength] = a;

        agents = newArray; // volatile store
    }

    public int doWork() throws Exception
    {
        final Agent[] agents = this.agents; // volatile load

        int sum = 0;

        for (int i = 0; i < agents.length; i++)
        {
            sum += agents[i].doWork();
        }

        return sum;
    }

    public synchronized void onClose()
    {
        for (Agent agent : agents)
        {
            try
            {
                agent.onClose();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        agents = new Agent[0];
    }

    public String roleName()
    {
        return roleName;
    }

    public synchronized void removeAgent(Agent a)
    {
        try
        {
            a.onClose();
        }
        finally
        {
            final Agent[] agents = this.agents;

            final ArrayList<Agent> list = new ArrayList<>(Arrays.asList(agents));
            list.remove(a);
            final Agent[] newArray = list.toArray(new Agent[list.size()]);

            this.agents = newArray; // volatile store
        }
    }

}

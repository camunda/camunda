/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.test.util.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.junit.rules.ExternalResource;

public class ControllableAgentRunnerService extends ExternalResource implements AgentRunnerService
{
    protected AtomicBoolean isRunning = new AtomicBoolean(false);

    protected final List<Agent> agents = new ArrayList<>();

    private Thread runningThread;

    protected int workCount = 0;

    @Override
    protected void before() throws Throwable
    {
        start();
    }

    @Override
    protected void after()
    {
        try
        {
            close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    protected void start()
    {
        if (isRunning.compareAndSet(false, true))
        {
            runningThread = new Thread(() ->
            {
                while (isRunning.get())
                {
                    workCount = 0;

                    final ArrayList<Agent> agentList = new ArrayList<>(agents);
                    for (int i = 0; i < agentList.size() && isRunning.get(); i++)
                    {
                        final Agent agent = agentList.get(i);
                        if (agent != null)
                        {
                            try
                            {
                                workCount += agent.doWork();
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }, "controllable-agent-runner-service");

            runningThread.start();
        }
    }

    public void waitUntilDone()
    {
        while (isRunning.get() && workCount > 0)
        {
            // waiting
        }
    }

    @Override
    public void close() throws Exception
    {
        if (isRunning.compareAndSet(true, false))
        {
            for (Agent agent : agents)
            {
                agent.onClose();
            }

            while (runningThread.isAlive())
            {
                // waiting
            }
        }
    }

    @Override
    public synchronized void run(Agent agent)
    {
        agents.add(agent);
    }

    @Override
    public synchronized void remove(Agent agent)
    {
        agents.remove(agent);
    }

}

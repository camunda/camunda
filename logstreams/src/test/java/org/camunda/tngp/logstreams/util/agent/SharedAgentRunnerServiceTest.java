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
package org.camunda.tngp.logstreams.util.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.camunda.tngp.util.agent.AgentRunnerFactory;
import org.camunda.tngp.util.agent.SharedAgentRunnerService;
import org.camunda.tngp.util.agent.SimpleAgentRunnerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SharedAgentRunnerServiceTest
{
    private static final String AGENT_NAME = "shared-agent";

    private MockAgent mockAgent1;
    private MockAgent mockAgent2;

    private AgentRunnerFactory agentRunnerFactory;

    private SharedAgentRunnerService agentRunnerService;

    @Before
    public void init() throws Exception
    {
        mockAgent1 = new MockAgent("agent-1");
        mockAgent2 = new MockAgent("agent-2");

        agentRunnerFactory = new SimpleAgentRunnerFactory();
    }

    @After
    public void cleanUp() throws Exception
    {
        agentRunnerService.close();

        waitUntilAgendThreadsAreEnded();
    }

    @Test
    public void shouldRunAgentsOnSharedThread() throws Exception
    {
        agentRunnerService = new SharedAgentRunnerService(agentRunnerFactory, AGENT_NAME, 1);

        agentRunnerService.run(mockAgent1);
        agentRunnerService.run(mockAgent2);

        assertThat(getRunningAgentThreadCount()).isEqualTo(1);

        mockAgent1.waitUntilInvocation();
        mockAgent2.waitUntilInvocation();
    }

    @Test
    public void shouldRunAgentsOnDedicatedThreads() throws Exception
    {
        agentRunnerService = new SharedAgentRunnerService(agentRunnerFactory, AGENT_NAME, 2);

        agentRunnerService.run(mockAgent1);
        agentRunnerService.run(mockAgent2);

        assertThat(getRunningAgentThreadCount()).isEqualTo(2);

        mockAgent1.waitUntilInvocation();
        mockAgent2.waitUntilInvocation();
    }

    @Test
    public void shouldRemoveAgent()
    {
        agentRunnerService = new SharedAgentRunnerService(agentRunnerFactory, AGENT_NAME, 1);

        agentRunnerService.run(mockAgent1);
        agentRunnerService.run(mockAgent2);

        agentRunnerService.remove(mockAgent1);

        assertThat(mockAgent1.isClosed()).isTrue();
        assertThat(mockAgent2.isClosed()).isFalse();
    }

    @Test
    public void shouldNotCloseAgentOnRemoveIfNotAdded()
    {
        agentRunnerService = new SharedAgentRunnerService(agentRunnerFactory, AGENT_NAME, 1);

        agentRunnerService.run(mockAgent2);

        agentRunnerService.remove(mockAgent1);

        assertThat(mockAgent1.isClosed()).isFalse();
        assertThat(mockAgent2.isClosed()).isFalse();
    }

    @Test
    public void shouldCloseAgents() throws Exception
    {
        agentRunnerService = new SharedAgentRunnerService(agentRunnerFactory, AGENT_NAME, 2);

        agentRunnerService.run(mockAgent1);
        agentRunnerService.run(mockAgent2);

        agentRunnerService.close();

        assertThat(mockAgent1.isClosed()).isTrue();
        assertThat(mockAgent2.isClosed()).isTrue();

        waitUntilAgendThreadsAreEnded();
    }

    private long getRunningAgentThreadCount()
    {
        final Set<Thread> threads = Thread.getAllStackTraces().keySet();
        return threads.stream().filter(thread -> thread.getName().startsWith(AGENT_NAME)).count();
    }

    private void waitUntilAgendThreadsAreEnded()
    {
        while (getRunningAgentThreadCount() > 0)
        {
            // wait until all threads are ended
        }
    }

}

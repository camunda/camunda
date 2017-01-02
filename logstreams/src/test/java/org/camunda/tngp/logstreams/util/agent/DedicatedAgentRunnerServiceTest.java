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

import org.camunda.tngp.util.agent.DedicatedAgentRunnerService;
import org.camunda.tngp.util.agent.SimpleAgentRunnerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DedicatedAgentRunnerServiceTest
{
    private static final String NAME_AGENT_1 = "agent-1";
    private static final String NAME_AGENT_2 = "agent-2";

    private MockAgent mockAgent1;
    private MockAgent mockAgent2;

    private DedicatedAgentRunnerService agentRunnerService;

    @Before
    public void init()
    {
        mockAgent1 = new MockAgent(NAME_AGENT_1);
        mockAgent2 = new MockAgent(NAME_AGENT_2);

        agentRunnerService = new DedicatedAgentRunnerService(new SimpleAgentRunnerFactory());
    }

    @After
    public void cleanUp() throws Exception
    {
        agentRunnerService.close();

        waitUntilAgendThreadIsEnded(NAME_AGENT_1);
        waitUntilAgendThreadIsEnded(NAME_AGENT_2);
    }

    @Test
    public void shouldRunAgentsOnDedicatedThreads() throws Exception
    {
        agentRunnerService.run(mockAgent1);
        agentRunnerService.run(mockAgent2);

        assertThat(hasRunningAgentThread(NAME_AGENT_1)).isTrue();
        assertThat(hasRunningAgentThread(NAME_AGENT_2)).isTrue();

        mockAgent1.waitUntilInvocation();
        mockAgent2.waitUntilInvocation();
    }

    @Test
    public void shouldRemoveAgent()
    {
        agentRunnerService.run(mockAgent1);
        agentRunnerService.run(mockAgent2);

        agentRunnerService.remove(mockAgent1);

        assertThat(mockAgent1.isClosed()).isTrue();
        assertThat(mockAgent2.isClosed()).isFalse();

        waitUntilAgendThreadIsEnded(NAME_AGENT_1);

        assertThat(hasRunningAgentThread(NAME_AGENT_2)).isTrue();
    }

    @Test
    public void shouldNotCloseAgentOnRemoveIfNotAdded()
    {
        agentRunnerService.run(mockAgent2);

        agentRunnerService.remove(mockAgent1);

        assertThat(mockAgent1.isClosed()).isFalse();
        assertThat(mockAgent2.isClosed()).isFalse();
    }

    @Test
    public void shouldCloseAgents() throws Exception
    {
        agentRunnerService.run(mockAgent1);
        agentRunnerService.run(mockAgent2);

        agentRunnerService.close();

        assertThat(mockAgent1.isClosed()).isTrue();
        assertThat(mockAgent2.isClosed()).isTrue();

        waitUntilAgendThreadIsEnded(NAME_AGENT_1);
        waitUntilAgendThreadIsEnded(NAME_AGENT_2);
    }

    private boolean hasRunningAgentThread(String agentName)
    {
        final Set<Thread> threads = Thread.getAllStackTraces().keySet();
        return threads.stream().anyMatch(thread -> thread.getName().equals(agentName));
    }

    private void waitUntilAgendThreadIsEnded(String agentName)
    {
        while (hasRunningAgentThread(agentName))
        {
            // wait until all threads are ended
        }
    }

}

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
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.agent.CompositeAgentRunnerServiceFactory;
import org.camunda.tngp.util.agent.SimpleAgentRunnerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CompositeAgentRunnerServiceFactoryTest
{
    private MockAgent mockAgent1;
    private MockAgent mockAgent2;

    private AgentRunnerFactory agentRunnerFactory;

    private CompositeAgentRunnerServiceFactory agentRunnerServiceFactory;

    private AgentRunnerService agentRunnerServiceGroup1;
    private AgentRunnerService agentRunnerServiceGroup2;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void init() throws Exception
    {
        mockAgent1 = new MockAgent("agent-1");
        mockAgent2 = new MockAgent("agent-2");

        agentRunnerFactory = new SimpleAgentRunnerFactory();

        agentRunnerServiceFactory = new CompositeAgentRunnerServiceFactory(agentRunnerFactory, "agent-group-1", "agent-group-2");

        agentRunnerServiceGroup1 = agentRunnerServiceFactory.createAgentRunnerService("agent-group-1");
        agentRunnerServiceGroup2 = agentRunnerServiceFactory.createAgentRunnerService("agent-group-2");
    }

    @After
    public void cleanUp() throws Exception
    {
        agentRunnerServiceGroup1.close();
        agentRunnerServiceGroup2.close();

        waitUntilAgendThreadIsEnded();
    }

    @Test
    public void shouldRunAgentsInSameGroups() throws Exception
    {
        agentRunnerServiceGroup1.run(mockAgent1);
        agentRunnerServiceGroup1.run(mockAgent2);

        assertThat(getRunningAgentThreadCount()).isEqualTo(1);

        mockAgent1.waitUntilInvocation();
        mockAgent2.waitUntilInvocation();
    }

    @Test
    public void shouldRunAgentsInDifferentGroups() throws Exception
    {
        agentRunnerServiceGroup1.run(mockAgent1);
        agentRunnerServiceGroup2.run(mockAgent2);

        assertThat(getRunningAgentThreadCount()).isEqualTo(1);

        mockAgent1.waitUntilInvocation();
        mockAgent2.waitUntilInvocation();
    }

    @Test
    public void shouldRemoveAgent() throws Exception
    {
        agentRunnerServiceGroup1.run(mockAgent1);
        agentRunnerServiceGroup2.run(mockAgent2);

        mockAgent1.waitUntilInvocation();

        agentRunnerServiceGroup1.remove(mockAgent1);

        final int invocationCount = mockAgent1.getInvocationCount();
        while (mockAgent2.getInvocationCount() <= invocationCount)
        {
            // agent-2 is always invoked after agent-1 until he is removed
        }

        assertThat(mockAgent1.getInvocationCount()).isEqualTo(invocationCount);
    }

    @Test
    public void shouldNotRemoveAgentIfNotAdded() throws Exception
    {
        agentRunnerServiceGroup1.run(mockAgent1);
        agentRunnerServiceGroup2.run(mockAgent2);

        mockAgent1.waitUntilInvocation();

        // try to remove from other group
        agentRunnerServiceGroup2.remove(mockAgent1);

        final int invocationCount = mockAgent1.getInvocationCount();
        while (mockAgent2.getInvocationCount() <= invocationCount)
        {
            // agent-2 is always invoked after agent-1
        }

        assertThat(mockAgent1.getInvocationCount()).isGreaterThan(invocationCount);
    }

    @Test
    public void shouldCloseAgentsIfAllAgentRunnerServicesAreClosed() throws Exception
    {
        agentRunnerServiceGroup1.run(mockAgent1);
        agentRunnerServiceGroup2.run(mockAgent2);

        agentRunnerServiceGroup1.close();

        assertThat(mockAgent1.isClosed()).isFalse();
        assertThat(mockAgent2.isClosed()).isFalse();

        agentRunnerServiceGroup2.close();

        assertThat(mockAgent1.isClosed()).isTrue();
        assertThat(mockAgent2.isClosed()).isTrue();

        waitUntilAgendThreadIsEnded();
    }

    @Test
    public void shouldFailToCreateServiceIfAgentNotExist()
    {
        thrown.expect(IllegalArgumentException.class);

        agentRunnerServiceFactory.createAgentRunnerService("not-existing");
    }

    @Test
    public void shouldFailToCreateFactoryIfNoAgentAreGiven()
    {
        thrown.expect(RuntimeException.class);

        new CompositeAgentRunnerServiceFactory(agentRunnerFactory);
    }

    private long getRunningAgentThreadCount()
    {
        final Set<Thread> threads = Thread.getAllStackTraces().keySet();
        return threads.stream().filter(thread -> thread.getName().contains("agent-group")).count();
    }

    private void waitUntilAgendThreadIsEnded()
    {
        while (getRunningAgentThreadCount() > 0)
        {
            // wait until all threads are ended
        }
    }

}

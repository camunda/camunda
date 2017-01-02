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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.util.agent.AgentGroup;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AgentGroupTest
{
    @Mock
    private Agent mockAgent;

    @Mock
    private Agent anotherMockAgent;

    @Before
    public void init()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGetRoleName()
    {
        final AgentGroup agentGroup = new AgentGroup("test");

        assertThat(agentGroup.roleName()).isEqualTo("test");
    }

    @Test
    public void shouldInvokeAddedAgents() throws Exception
    {
        final AgentGroup agentGroup = new AgentGroup("test");

        agentGroup.addAgent(mockAgent);
        agentGroup.addAgent(anotherMockAgent);

        agentGroup.doWork();

        verify(mockAgent, times(1)).doWork();
        verify(anotherMockAgent, times(1)).doWork();
    }

    @Test
    public void shouldNotInvokeRemovedAgent() throws Exception
    {
        final AgentGroup agentGroup = new AgentGroup("test");

        agentGroup.addAgent(mockAgent);
        agentGroup.addAgent(anotherMockAgent);

        agentGroup.removeAgent(mockAgent);

        agentGroup.doWork();

        verify(mockAgent, never()).doWork();
        verify(anotherMockAgent).doWork();
    }

    @Test
    public void shouldCloseAgentOnRemove() throws Exception
    {
        final AgentGroup agentGroup = new AgentGroup("test");

        agentGroup.addAgent(mockAgent);
        agentGroup.removeAgent(mockAgent);

        verify(mockAgent).onClose();
    }

    @Test
    public void shouldNotCloseAgentOnRemoveIfNotAdded() throws Exception
    {
        final AgentGroup agentGroup = new AgentGroup("test");

        agentGroup.addAgent(mockAgent);

        agentGroup.removeAgent(anotherMockAgent);

        verify(mockAgent, never()).onClose();
        verify(anotherMockAgent, never()).onClose();
    }

    @Test
    public void shouldCloseAgents()
    {
        final AgentGroup agentGroup = new AgentGroup("test");

        agentGroup.addAgent(mockAgent);
        agentGroup.addAgent(anotherMockAgent);

        agentGroup.onClose();

        verify(mockAgent).onClose();
        verify(anotherMockAgent).onClose();
    }

}

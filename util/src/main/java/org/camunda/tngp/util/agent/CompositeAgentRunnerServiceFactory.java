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
package org.camunda.tngp.util.agent;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.CompositeAgent;
import org.camunda.tngp.util.EnsureUtil;

/**
 * Provides {@link AgentRunnerService}s for agent groups which runs their agents together with other groups on a single thread.
 */
public class CompositeAgentRunnerServiceFactory
{
    protected final AgentRunner agentRunner;

    protected final List<AgentGroup> agentGroups;

    public CompositeAgentRunnerServiceFactory(AgentRunnerFactory agentRunnerFactory, String... agentNames)
    {
        EnsureUtil.ensureGreaterThan("agent names", agentNames.length, 0);

        agentGroups = Stream.of(agentNames).map(AgentGroup::new).collect(Collectors.toList());

        agentRunner = agentRunnerFactory.createAgentRunner(new CompositeAgent(agentGroups));

        AgentRunner.startOnThread(agentRunner);
    }

    public AgentRunnerService createAgentRunnerService(String agentName)
    {
        final AgentGroup agentGroup = agentGroups.stream()
                .filter(agent -> agent.roleName().equals(agentName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("found no agent with name: " + agentName));

        return new CompositeAgentRunnerService(agentGroup);
    }

    class CompositeAgentRunnerService implements AgentRunnerService
    {
        private final AgentGroup agentGroup;

        CompositeAgentRunnerService(AgentGroup agentGroup)
        {
            this.agentGroup = agentGroup;
        }

        @Override
        public void close() throws Exception
        {
            agentGroups.remove(agentGroup);

            if (agentGroups.isEmpty())
            {
                agentRunner.close();
            }
        }

        @Override
        public void run(Agent agent)
        {
            agentGroup.addAgent(agent);
        }

        @Override
        public void remove(Agent agent)
        {
            agentGroup.removeAgent(agent);
        }
    }

}

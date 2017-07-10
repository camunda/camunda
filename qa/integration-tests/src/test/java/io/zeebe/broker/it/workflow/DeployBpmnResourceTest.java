/**
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.model.bpmn.Bpmn;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.WorkflowTopicClient;
import io.zeebe.client.workflow.cmd.DeploymentResult;
import io.zeebe.client.workflow.cmd.WorkflowDefinition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class DeployBpmnResourceTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Test
    public void shouldDeployModelInstance()
    {
        // given
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // when
        final DeploymentResult result = workflowService.deploy()
            .bpmnModelInstance(Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .endEvent()
                    .done())
            .execute();

        // then
        assertThat(result.isDeployed()).isTrue();
        assertThat(result.getKey()).isGreaterThan(0);
        assertThat(result.getDeployedWorkflows()).hasSize(1);

        final WorkflowDefinition deployedWorkflow = result.getDeployedWorkflows().get(0);
        assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo("process");
        assertThat(deployedWorkflow.getVersion()).isEqualTo(1);
    }

    @Test
    public void shouldNotDeployUnparsableModel()
    {
        // given
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // when
        final DeploymentResult result = workflowService.deploy()
                .resourceString("Foooo")
                .execute();

        // then
        assertThat(result.isDeployed()).isFalse();
        assertThat(result.getDeployedWorkflows()).hasSize(0);
        assertThat(result.getErrorMessage()).contains("Failed to read BPMN model");
    }

    @Test
    public void shouldNotDeployInvalidModel()
    {
        // given
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // when
        final DeploymentResult result = workflowService.deploy()
            .bpmnModelInstance(Bpmn.createExecutableProcess("no-start-event").done()) // does not have a start event
            .execute();

        // then
        assertThat(result.isDeployed()).isFalse();
        assertThat(result.getDeployedWorkflows()).hasSize(0);
        assertThat(result.getErrorMessage()).contains("no-start-event").contains("The process must contain at least one none start event.");
    }

    @Test
    public void shouldNotDeployNonExecutableModel()
    {
        // given
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // when
        final DeploymentResult result = workflowService.deploy()
            .bpmnModelInstance(Bpmn.createProcess()
                    .startEvent()
                    .endEvent()
                    .done())
            .execute();

        // then
        assertThat(result.isDeployed()).isFalse();
        assertThat(result.getDeployedWorkflows()).hasSize(0);
        assertThat(result.getErrorMessage()).contains("BPMN model must contain at least one executable process");
    }

}

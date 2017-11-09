/*
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

import static io.zeebe.broker.it.util.TopicEventRecorder.wfEvent;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;

import java.io.InputStream;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.WorkflowsClient;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.event.*;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CreateDeploymentTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();
    public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(eventRecorder);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldDeployWorkflowModel()
    {
        // given
        final WorkflowsClient workflowService = clientRule.workflows();

        final io.zeebe.model.bpmn.instance.WorkflowDefinition workflow = Bpmn.createExecutableWorkflow("process")
               .startEvent()
               .endEvent()
               .done();
        // when
        final DeploymentEvent result = workflowService
                .deploy(clientRule.getDefaultTopic())
                .addWorkflowModel(workflow, "workflow.bpmn")
                .execute();

        // then
        assertThat(result.getMetadata().getKey()).isGreaterThan(0);
        assertThat(result.getResources()).hasSize(1);

        final DeploymentResource deployedResource = result.getResources().get(0);
        assertThat(deployedResource.getResource()).isEqualTo(Bpmn.convertToString(workflow).getBytes(UTF_8));
        assertThat(deployedResource.getResourceType()).isEqualTo(ResourceType.BPMN_XML);
        assertThat(deployedResource.getResourceName()).isEqualTo("workflow.bpmn");

        assertThat(result.getDeployedWorkflows()).hasSize(1);

        final WorkflowDefinition deployedWorkflow = result.getDeployedWorkflows().get(0);
        assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo("process");
        assertThat(deployedWorkflow.getVersion()).isEqualTo(1);

        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("CREATED")));

        final WorkflowEvent workflowEvent = eventRecorder.getSingleWorkflowEvent(wfEvent("CREATED"));
        assertThat(workflowEvent.getBpmnProcessId()).isEqualTo("process");
        assertThat(workflowEvent.getVersion()).isEqualTo(1);
        assertThat(workflowEvent.getDeploymentKey()).isEqualTo(result.getMetadata().getKey());
        assertThat(workflowEvent.getBpmnXml()).isEqualTo(Bpmn.convertToString(workflow));
    }

    @Test
    public void shouldDeployMultipleResources()
    {
        // given
        final WorkflowsClient workflowService = clientRule.workflows();

        // when
        final DeploymentEvent result = workflowService
                .deploy(clientRule.getDefaultTopic())
                .addWorkflowModel(Bpmn.createExecutableWorkflow("model1").startEvent().done(), "model1.bpmn")
                .addWorkflowModel(Bpmn.createExecutableWorkflow("model2").startEvent().done(), "model2.bpmn")
                .execute();

        // then
        assertThat(result.getResources())
            .hasSize(2)
            .extracting(DeploymentResource::getResourceName)
            .contains("model1.bpmn", "model2.bpmn");

        assertThat(result.getDeployedWorkflows())
            .hasSize(2)
            .extracting(WorkflowDefinition::getBpmnProcessId)
            .contains("model1", "model2");

        assertThat(eventRecorder.hasWorkflowEvent(w -> w.getBpmnProcessId().equals("model1"))).isTrue();
        assertThat(eventRecorder.hasWorkflowEvent(w -> w.getBpmnProcessId().equals("model2"))).isTrue();
    }

    @Test
    public void shouldNotDeployUnparsableModel()
    {
        // given
        final WorkflowsClient workflowService = clientRule.workflows();

        // then
        exception.expect(ClientCommandRejectedException.class);
        exception.expectMessage(containsString("Failed to deploy resource 'invalid.bpmn'"));

        // when
        workflowService.deploy(clientRule.getDefaultTopic())
                .addResourceStringUtf8("Foooo", "invalid.bpmn")
                .execute();
    }

    @Test
    public void shouldNotDeployInvalidModel()
    {
        // given
        final WorkflowsClient workflowService = clientRule.workflows();

        // then
        exception.expect(ClientCommandRejectedException.class);
        exception.expectMessage(containsString("The process must contain at least one none start event."));

        // when
        workflowService.deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(Bpmn.createExecutableWorkflow("no-start-event").done(), "invalid.bpmn") // does not have a start event
            .execute();
    }

    @Test
    public void shouldNotDeployNonExecutableModel()
    {
        // given
        final WorkflowsClient workflowService = clientRule.workflows();

        final io.zeebe.model.bpmn.instance.WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("not-executable")
                .startEvent()
                .endEvent()
                .done();

        final ProcessImpl workflowImpl = (ProcessImpl) workflowDefinition.getWorkflows().iterator().next();
        workflowImpl.setExecutable(false);

        // then
        exception.expect(ClientCommandRejectedException.class);
        exception.expectMessage(containsString("BPMN model must contain at least one executable process"));

        // when
        workflowService.deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(workflowDefinition, "workflow.bpmn")
            .execute();
    }

    @Test
    public void shouldNotDeployIfNoResourceIsAdded()
    {
        // given
        final WorkflowsClient workflowService = clientRule.workflows();

        // then
        exception.expect(ClientCommandRejectedException.class);
        exception.expectMessage(containsString("Deployment doesn't contain a resource to deploy."));

        // when
        workflowService.deploy(clientRule.getDefaultTopic())
            .execute();
    }

    @Test
    public void shouldDeployYamlWorkflow()
    {
        // given
        final WorkflowsClient workflowService = clientRule.workflows();

        // when
        final DeploymentEvent result = workflowService
                .deploy(clientRule.getDefaultTopic())
                .addResourceFromClasspath("workflows/simple-workflow.yaml")
                .execute();

        // then
        assertThat(result.getMetadata().getKey()).isGreaterThan(0);

        assertThat(result.getResources()).hasSize(1);

        final DeploymentResource deployedResource = result.getResources().get(0);
        assertThat(deployedResource.getResourceType()).isEqualTo(ResourceType.YAML_WORKFLOW);
        assertThat(deployedResource.getResourceName()).isEqualTo("workflows/simple-workflow.yaml");

        assertThat(result.getDeployedWorkflows()).hasSize(1);

        final WorkflowDefinition deployedWorkflow = result.getDeployedWorkflows().get(0);
        assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo("yaml-workflow");
        assertThat(deployedWorkflow.getVersion()).isEqualTo(1);

        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("CREATED")));

        final InputStream yamlStream = getClass().getResourceAsStream("/workflows/simple-workflow.yaml");
        final io.zeebe.model.bpmn.instance.WorkflowDefinition workflowDefinition = Bpmn.readFromYamlStream(yamlStream);
        final String bpmnXml = Bpmn.convertToString(workflowDefinition);

        final WorkflowEvent workflowEvent = eventRecorder.getSingleWorkflowEvent(wfEvent("CREATED"));
        assertThat(workflowEvent.getBpmnProcessId()).isEqualTo("yaml-workflow");
        assertThat(workflowEvent.getVersion()).isEqualTo(1);
        assertThat(workflowEvent.getDeploymentKey()).isEqualTo(result.getMetadata().getKey());
        assertThat(workflowEvent.getBpmnXml()).isEqualTo(bpmnXml);
    }

}

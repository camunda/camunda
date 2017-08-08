/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow;

import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.*;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_PARTITION_ID;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_TOPIC_NAME;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.PROP_WORKFLOW_BPMN_XML;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.workflowEvents;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.clientapi.*;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CreateDeploymentTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldCreateDeployment()
    {
        // given charset
        final BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .topicName(DEFAULT_TOPIC_NAME)
                .partitionId(0)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put("bpmnXml", bpmnXml(modelInstance))
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.position()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getTopicName()).isEqualTo(DEFAULT_TOPIC_NAME);
        assertThat(resp.partitionId()).isEqualTo(DEFAULT_PARTITION_ID);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "DEPLOYMENT_CREATED");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnDeployedWorkflowDefinitions()
    {
        // given
        final BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        ExecuteCommandResponse resp = apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(0)
            .eventType(EventType.DEPLOYMENT_EVENT)
            .command()
                .put(PROP_STATE, "CREATE_DEPLOYMENT")
                .put("bpmnXml", bpmnXml(modelInstance))
            .done()
            .sendAndAwait();

        // then
        List<Map<String, Object>> deployedWorkflows = (List<Map<String, Object>>) resp.getEvent().get("deployedWorkflows");
        assertThat(deployedWorkflows).hasSize(1);
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process");
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_VERSION, 1);

        // when deploy the workflow definition a second time
        resp = apiRule.createCmdRequest()
                .topicName(DEFAULT_TOPIC_NAME)
                .partitionId(0)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put("bpmnXml", bpmnXml(modelInstance))
                .done()
                .sendAndAwait();

        // then the workflow definition version is increased
        deployedWorkflows = (List<Map<String, Object>>) resp.getEvent().get("deployedWorkflows");
        assertThat(deployedWorkflows).hasSize(1);
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process");
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_VERSION, 2);
    }

    @Test
    public void shouldWriteWorkflowEvent()
    {
        // given
        final BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .topicName(DEFAULT_TOPIC_NAME)
                .partitionId(0)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put("bpmnXml", bpmnXml(modelInstance))
                .done()
                .sendAndAwait();

        // then
        final long deploymentKey = resp.key();
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "DEPLOYMENT_CREATED");

        final SubscribedEvent workflowEvent = apiRule.topic().receiveSingleEvent(workflowEvents());
        assertThat(workflowEvent.key()).isGreaterThanOrEqualTo(0L).isNotEqualTo(deploymentKey);
        assertThat(workflowEvent.event())
            .containsEntry(PROP_STATE, "CREATED")
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry("deploymentKey", deploymentKey)
            .containsEntry(PROP_WORKFLOW_BPMN_XML, bpmnXml(modelInstance));
    }



    @Test
    public void shouldRejectDeploymentIfNotValid()
    {
        // given
        final BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("process").done();

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .topicName(DEFAULT_TOPIC_NAME)
                .partitionId(0)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put("bpmnXml", bpmnXml(modelInstance))
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getTopicName()).isEqualTo(DEFAULT_TOPIC_NAME);
        assertThat(resp.partitionId()).isEqualTo(DEFAULT_PARTITION_ID);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "DEPLOYMENT_REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage")).contains("The process must contain at least one none start event.");
    }

    @Test
    public void shouldRejectDeploymentIfNotParsable()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .topicName(DEFAULT_TOPIC_NAME)
                .partitionId(0)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put("bpmnXml", "not a workflow".getBytes(UTF_8))
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getTopicName()).isEqualTo(DEFAULT_TOPIC_NAME);
        assertThat(resp.partitionId()).isEqualTo(DEFAULT_PARTITION_ID);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "DEPLOYMENT_REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage")).contains("Failed to deploy BPMN model");
    }

    private byte[] bpmnXml(final BpmnModelInstance modelInstance)
    {
        return Bpmn.convertToString(modelInstance).getBytes(UTF_8);
    }

}

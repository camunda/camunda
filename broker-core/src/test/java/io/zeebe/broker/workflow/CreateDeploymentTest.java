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
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.workflowEvents;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.*;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.workflow.data.ResourceType;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.clientapi.*;
import org.assertj.core.util.Files;
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
    public void shouldCreateDeploymentWithBpmnXml()
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resource", bpmnXml(definition))
                    .put("resourceType", ResourceType.BPMN_XML)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.position()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.partitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "DEPLOYMENT_CREATED");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnDeployedWorkflowDefinitions()
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        ExecuteCommandResponse resp = apiRule.createCmdRequest()
            .partitionId(Protocol.SYSTEM_PARTITION)
            .eventType(EventType.DEPLOYMENT_EVENT)
            .command()
                .put(PROP_STATE, "CREATE_DEPLOYMENT")
                .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                .put("resource", bpmnXml(definition))
                .put("resourceType", ResourceType.BPMN_XML)
            .done()
            .sendAndAwait();

        // then
        List<Map<String, Object>> deployedWorkflows = (List<Map<String, Object>>) resp.getEvent().get("deployedWorkflows");
        assertThat(deployedWorkflows).hasSize(1);
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process");
        assertThat(deployedWorkflows.get(0)).containsEntry(PROP_WORKFLOW_VERSION, 1);

        // when deploy the workflow definition a second time
        resp = apiRule.createCmdRequest()
            .partitionId(Protocol.SYSTEM_PARTITION)
            .eventType(EventType.DEPLOYMENT_EVENT)
            .command()
                .put(PROP_STATE, "CREATE_DEPLOYMENT")
                .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                .put("resource", bpmnXml(definition))
                .put("resourceType", ResourceType.BPMN_XML)
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
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .endEvent()
            .done();

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resource", bpmnXml(definition))
                    .put("resourceType", ResourceType.BPMN_XML)
                .done()
                .sendAndAwait();

        // then
        final long deploymentKey = resp.key();
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "DEPLOYMENT_CREATED");

        final SubscribedEvent workflowEvent = apiRule.topic().receiveSingleEvent(workflowEvents("CREATED"));
        assertThat(workflowEvent.key()).isGreaterThanOrEqualTo(0L).isNotEqualTo(deploymentKey);
        assertThat(workflowEvent.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry("deploymentKey", deploymentKey)
            .containsEntry("bpmnXml", bpmnXml(definition));
    }

    @Test
    public void shouldRejectDeploymentIfTopicNotExists()
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .endEvent()
                .done();

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put("topicName", "not-exist")
                    .put("resource", bpmnXml(definition))
                    .put("resourceType", ResourceType.BPMN_XML)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "DEPLOYMENT_REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage")).isEmpty();
    }

    @Test
    public void shouldRejectDeploymentIfNotValid()
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process").done();

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resource", bpmnXml(definition))
                    .put("resourceType", ResourceType.BPMN_XML)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "DEPLOYMENT_REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage")).contains("The process must contain at least one none start event.");
    }

    @Test
    public void shouldRejectDeploymentIfNotParsable()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resource", "not a workflow".getBytes(UTF_8))
                    .put("resourceType", ResourceType.BPMN_XML)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "DEPLOYMENT_REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage")).contains("Failed to deploy BPMN model");
    }

    @Test
    public void shouldRejectDeploymentIfConditionIsInvalid()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("workflow")
                                     .startEvent()
                                     .exclusiveGateway()
                                     .sequenceFlow(s -> s.condition("foobar"))
                                         .endEvent()
                                     .sequenceFlow(s -> s.defaultFlow())
                                         .endEvent()
                                         .done();

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resource", bpmnXml(workflowDefinition))
                    .put("resourceType", ResourceType.BPMN_XML)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "DEPLOYMENT_REJECTED");
        assertThat((String) resp.getEvent().get("errorMessage")).contains("The condition 'foobar' is not valid");
    }

    @Test
    public void shouldCreateDeploymentWithYamlWorfklow() throws Exception
    {
        // given
        final File yamlFile = new File(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
        final String yamlWorkflow = Files.contentOf(yamlFile, UTF_8);

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_STATE, "CREATE_DEPLOYMENT")
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resource", yamlWorkflow.getBytes(UTF_8))
                    .put("resourceType", ResourceType.YAML_WORKFLOW)
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.getEvent()).containsEntry(PROP_STATE, "DEPLOYMENT_CREATED");

        final SubscribedEvent workflowEvent = apiRule.topic().receiveSingleEvent(workflowEvents("CREATED"));
        assertThat(workflowEvent.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "yaml-workflow")
            .containsEntry("deploymentKey", resp.key())
            .containsEntry("bpmnXml", bpmnXml(Bpmn.readFromYamlFile(yamlFile)));
    }

    @Test
    public void shouldCreateWorkflowOnAllPartitions()
    {
        // given
        final int partitions = 3;

        apiRule.createTopic("test", partitions);
        final List<Integer> partitionIds = apiRule.getPartitionIds("test");

        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .endEvent()
                .done();

        // when
        apiRule.topic().deploy("test", definition);

        // then
        final List<Long> workflowKeys = new ArrayList<>();
        partitionIds.forEach(partitionId ->
        {
            final SubscribedEvent event = apiRule.topic(partitionId).receiveSingleEvent(workflowEvents("CREATED"));

            workflowKeys.add(event.key());
        });

        assertThat(workflowKeys)
            .hasSize(partitions)
            .containsOnly(workflowKeys.get(0));
    }

    private byte[] bpmnXml(final WorkflowDefinition definition)
    {
        return Bpmn.convertToString(definition).getBytes(UTF_8);
    }

}

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

import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.PROP_WORKFLOW_ACTIVITY_ID;
import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.PROP_WORKFLOW_INSTANCE_KEY;
import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.PROP_WORKFLOW_VERSION;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.assertj.core.util.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import io.zeebe.broker.system.workflow.repository.data.ResourceType;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import io.zeebe.test.util.MsgPackUtil;

public class WorkflowInstanceFunctionalTest
{
    private static final String PROP_JOB_TYPE = "type";
    private static final String PROP_JOB_RETRIES = "retries";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    private TestTopicClient testClient;

    @Before
    public void init()
    {
        testClient = apiRule.topic();
    }

    @Test
    public void shouldStartWorkflowInstanceAtNoneStartEvent()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent("foo")
                .endEvent()
                .done());

        // when
        final ExecuteCommandResponse response = testClient.createWorkflowInstanceWithResponse("process");

        // then
        final SubscribedRecord event = testClient.receiveEvents()
                .withIntent(WorkflowInstanceIntent.START_EVENT_OCCURRED)
                .getFirst();

        final long workflowInstanceKey = response.key();
        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.position()).isGreaterThanOrEqualTo(response.position());
        assertThat(event.position()).isGreaterThan(response.position());
        assertThat(event.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
    }

    @Test
    public void shouldTakeSequenceFlowFromStartEvent()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                    .startEvent()
                    .sequenceFlow("foo")
                    .endEvent()
                    .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedRecord event = testClient.receiveEvents()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .getFirst();

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
    }

    @Test
    public void shouldOccureEndEvent()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .endEvent("foo")
                .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedRecord event = testClient.receiveEvents()
            .withIntent(WorkflowInstanceIntent.END_EVENT_OCCURRED)
            .getFirst();

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
    }

    @Test
    public void shouldCompleteWorkflowInstance()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .endEvent()
                .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedRecord event = testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.COMPLETED)
            .getFirst();

        assertThat(event.key()).isEqualTo(workflowInstanceKey);
        assertThat(event.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "");
    }

    @Test
    public void shouldConsumeTokenIfEventHasNoOutgoingSequenceflow()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedRecord event = testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.COMPLETED)
            .getFirst();

        assertThat(event.key()).isEqualTo(workflowInstanceKey);
        assertThat(event.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "");
    }

    @Test
    public void shouldConsumeTokenIfActivityHasNoOutgoingSequenceflow()
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
                    .startEvent()
                    .serviceTask("foo", t -> t.taskType("bar"))
                    .done();

        testClient.deploy(definition);

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeJobOfType("bar");

        // then
        final SubscribedRecord event = testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.COMPLETED)
            .getFirst();

        assertThat(event.key()).isEqualTo(workflowInstanceKey);
        assertThat(event.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "");
    }

    @Test
    public void shouldActivateServiceTask()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                    .startEvent()
                    .serviceTask("foo", t -> t.taskType("bar"))
                    .endEvent()
                    .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedRecord event = testClient.receiveEvents()
            .withIntent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED)
            .getFirst();

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
    }

    @Test
    public void shouldCreateTaskWhenServiceTaskIsActivated()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                    .startEvent()
                    .serviceTask("foo", t -> t.taskType("bar").taskRetries(5))
                    .endEvent()
                    .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedRecord event = testClient.receiveCommands()
            .ofTypeJob()
            .withIntent(JobIntent.CREATE)
            .getFirst();

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.value())
            .containsEntry(PROP_JOB_TYPE, "bar")
            .containsEntry(PROP_JOB_RETRIES, 5);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldCreateJobWithWorkflowInstanceAndCustomHeaders()
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                    .startEvent()
                    .serviceTask("foo", t -> t.taskType("bar")
                                 .taskHeader("a", "b")
                                 .taskHeader("c", "d"))
                    .endEvent()
                    .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedRecord event = testClient.receiveCommands()
            .ofTypeJob()
            .withIntent(JobIntent.CREATE)
            .getFirst();

        final Map<String, Object> headers = (Map<String, Object>) event.value().get("headers");
        assertThat(headers)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry("workflowDefinitionVersion", 1)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo")
            .containsKey("activityInstanceKey");

        final Map<String, Object> customHeaders = (Map<String, Object>) event.value().get("customHeaders");
        assertThat(customHeaders)
            .containsEntry("a", "b")
            .containsEntry("c", "d");
    }

    @Test
    public void shouldCompleteServiceTaskWhenTaskIsCompleted()
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
                    .startEvent()
                    .serviceTask("foo", t -> t.taskType("bar"))
                    .endEvent()
                    .done();

        testClient.deploy(definition);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // when
        testClient.completeJobOfType("bar");

        // then
        final SubscribedRecord activityActivatedEvent = testClient.receiveEvents()
            .withIntent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED)
            .getFirst();

        final SubscribedRecord activityCompletedEvent = testClient.receiveEvents()
            .withIntent(WorkflowInstanceIntent.ACTIVITY_COMPLETED)
            .getFirst();

        assertThat(activityCompletedEvent.key()).isEqualTo(activityActivatedEvent.key());
        assertThat(activityCompletedEvent.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
    }

    @Test
    public void shouldSpitOnExclusiveGateway()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("workflow")
                .startEvent()
                .exclusiveGateway("xor")
                .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                    .endEvent("a")
                .sequenceFlow("s2", s -> s.condition("$.foo >= 5 && $.foo < 10"))
                    .endEvent("b")
                .sequenceFlow("s3", s -> s.defaultFlow())
                    .endEvent("c")
                .done();

        testClient.deploy(workflowDefinition);

        final long workflowInstance1 = testClient.createWorkflowInstance("workflow", asMsgPack("foo", 4));
        final long workflowInstance2 = testClient.createWorkflowInstance("workflow", asMsgPack("foo", 8));
        final long workflowInstance3 = testClient.createWorkflowInstance("workflow", asMsgPack("foo", 12));

        SubscribedRecord endEvent = receiveWorkflowInstanceEvent(workflowInstance1, WorkflowInstanceIntent.END_EVENT_OCCURRED);
        assertThat(endEvent.value()).containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "a");

        endEvent = receiveWorkflowInstanceEvent(workflowInstance2, WorkflowInstanceIntent.END_EVENT_OCCURRED);
        assertThat(endEvent.value()).containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "b");

        endEvent = receiveWorkflowInstanceEvent(workflowInstance3, WorkflowInstanceIntent.END_EVENT_OCCURRED);
        assertThat(endEvent.value()).containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "c");
    }

    @Test
    public void shouldJoinOnExclusiveGateway()
    {
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("workflow")
                .startEvent()
                .exclusiveGateway("split")
                .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                    .exclusiveGateway("joinRequest")
                    .continueAt("split")
                .sequenceFlow("s2", s -> s.defaultFlow())
                    .joinWith("joinRequest")
                .endEvent("end")
                .done();

        testClient.deploy(workflowDefinition);

        final long workflowInstance1 = testClient.createWorkflowInstance("workflow", asMsgPack("foo", 4));
        final long workflowInstance2 = testClient.createWorkflowInstance("workflow", asMsgPack("foo", 8));

        receiveWorkflowInstanceEvent(workflowInstance1, WorkflowInstanceIntent.COMPLETED);
        receiveWorkflowInstanceEvent(workflowInstance2, WorkflowInstanceIntent.COMPLETED);

        List<String> takenSequenceFlows = testClient.receiveEvents()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .filter(r -> (Long) r.value().get("workflowInstanceKey") == workflowInstance1)
            .limit(3)
            .map(s -> (String) s.value().get("activityId"))
            .collect(Collectors.toList());
        assertThat(takenSequenceFlows).contains("s1");

        takenSequenceFlows = testClient.receiveEvents()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .filter(r -> (Long) r.value().get("workflowInstanceKey") == workflowInstance2)
            .limit(3)
            .map(s -> (String) s.value().get("activityId"))
            .collect(Collectors.toList());
        assertThat(takenSequenceFlows).contains("s2");
    }

    @Test
    public void testWorkflowInstanceStatesWithServiceTask()
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
                    .startEvent("a")
                    .serviceTask("b", t -> t.taskType("foo"))
                    .endEvent("c")
                    .done();

        testClient.deploy(definition);

        testClient.createWorkflowInstance("process");

        // when
        testClient.completeJobOfType("foo");

        // then
        final List<SubscribedRecord> workflowEvents = testClient
                .receiveRecords()
                .ofTypeWorkflowInstance()
                .limit(11)
                .collect(Collectors.toList());

        assertThat(workflowEvents).extracting(e -> e.intent()).containsExactly(
                WorkflowInstanceIntent.CREATE,
                WorkflowInstanceIntent.CREATED,
                WorkflowInstanceIntent.START_EVENT_OCCURRED,
                WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
                WorkflowInstanceIntent.ACTIVITY_READY,
                WorkflowInstanceIntent.ACTIVITY_ACTIVATED,
                WorkflowInstanceIntent.ACTIVITY_COMPLETING,
                WorkflowInstanceIntent.ACTIVITY_COMPLETED,
                WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
                WorkflowInstanceIntent.END_EVENT_OCCURRED,
                WorkflowInstanceIntent.COMPLETED);
    }

    @Test
    public void testWorkflowInstanceStatesWithExclusiveGateway()
    {
        // given
        final WorkflowDefinition workflowDefinition = Bpmn.createExecutableWorkflow("workflow")
                .startEvent()
                .exclusiveGateway("xor")
                .sequenceFlow("s1", s -> s.condition("$.foo < 5"))
                    .endEvent("a")
                .sequenceFlow("s2", s -> s.defaultFlow())
                    .endEvent("b")
                .done();

        testClient.deploy(workflowDefinition);

        // when
        testClient.createWorkflowInstance("workflow", MsgPackUtil.asMsgPack("foo", 4));

        // then
        final List<SubscribedRecord> workflowEvents = testClient
                .receiveRecords()
                .ofTypeWorkflowInstance()
                .limit(8)
                .collect(Collectors.toList());

        assertThat(workflowEvents).extracting(e -> e.intent()).containsExactly(
                WorkflowInstanceIntent.CREATE,
                WorkflowInstanceIntent.CREATED,
                WorkflowInstanceIntent.START_EVENT_OCCURRED,
                WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
                WorkflowInstanceIntent.GATEWAY_ACTIVATED,
                WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
                WorkflowInstanceIntent.END_EVENT_OCCURRED,
                WorkflowInstanceIntent.COMPLETED);
    }

    @Test
    public void shouldCreateAndCompleteInstanceOfYamlWorkflow() throws Exception
    {
        // given
        final File yamlFile = new File(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
        final String yamlWorkflow = Files.contentOf(yamlFile, UTF_8);

        final Map<String, Object> deploymentResource = new HashMap<>();
        deploymentResource.put("resource", yamlWorkflow.getBytes(UTF_8));
        deploymentResource.put("resourceType", ResourceType.YAML_WORKFLOW);
        deploymentResource.put("resourceName", "simple-workflow.yaml");

        final ExecuteCommandResponse deploymentResp = apiRule.createCmdRequest()
                .partitionId(Protocol.SYSTEM_PARTITION)
                .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
                .command()
                    .put("topicName", ClientApiRule.DEFAULT_TOPIC_NAME)
                    .put("resources", Collections.singletonList(deploymentResource))
                .done()
                .sendAndAwait();

        assertThat(deploymentResp.intent()).isEqualTo(DeploymentIntent.CREATED);

        final long workflowInstanceKey = testClient.createWorkflowInstance("yaml-workflow");

        // when
        testClient.completeJobOfType("foo");
        testClient.completeJobOfType("bar");

        // then
        final SubscribedRecord event = testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.COMPLETED)
            .getFirst();

        assertThat(event.key()).isEqualTo(workflowInstanceKey);
        assertThat(event.value())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "yaml-workflow")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "");
    }

    private SubscribedRecord receiveWorkflowInstanceEvent(long key, Intent intent)
    {
        return testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(intent)
            .filter(r -> (Long) r.value().get("workflowInstanceKey") == key)
            .findFirst()
            .get();
    }

}

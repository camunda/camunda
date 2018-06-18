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

import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.*;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.system.workflow.repository.data.ResourceType;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import io.zeebe.test.util.MsgPackUtil;
import java.io.File;
import java.net.URISyntaxException;
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

public class WorkflowInstanceFunctionalTest {
  private static final String PROP_JOB_TYPE = "type";
  private static final String PROP_JOB_RETRIES = "retries";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private TestTopicClient testClient;

  @Before
  public void init() {
    testClient = apiRule.topic();
  }

  @Test
  public void shouldStartWorkflowInstanceAtNoneStartEvent() {
    // given
    testClient.deploy(Bpmn.createExecutableWorkflow("process").startEvent("foo").endEvent().done());

    // when
    final ExecuteCommandResponse response =
        testClient.createWorkflowInstanceWithResponse("process");

    // then
    final SubscribedRecord workflowCreateCmd =
        testClient.receiveFirstWorkflowInstanceCommand(WorkflowInstanceIntent.CREATE);
    final SubscribedRecord startEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.START_EVENT_OCCURRED);
    final long workflowInstanceKey = response.key();

    assertThat(response.sourceRecordPosition()).isEqualTo(workflowCreateCmd.position());
    assertThat(response.position()).isGreaterThan(workflowCreateCmd.position());

    assertThat(startEvent.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    // created and start startEvent is written in batch
    assertThat(startEvent.sourceRecordPosition()).isEqualTo(workflowCreateCmd.position());
    assertThat(startEvent.position()).isGreaterThan(response.position());
    assertThat(startEvent.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 1)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
  }

  @Test
  public void shouldTakeSequenceFlowFromStartEvent() {
    // given
    testClient.deploy(
        Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .sequenceFlow("foo")
            .endEvent()
            .done());

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    // then
    final SubscribedRecord startEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.START_EVENT_OCCURRED);
    final SubscribedRecord sequenceFlow =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);

    assertThat(sequenceFlow.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    assertThat(sequenceFlow.sourceRecordPosition()).isEqualTo(startEvent.position());
    assertThat(sequenceFlow.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 1)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
  }

  @Test
  public void shouldOccureEndEvent() {
    // given
    testClient.deploy(Bpmn.createExecutableWorkflow("process").startEvent().endEvent("foo").done());

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    // then
    final SubscribedRecord sequenceFlow =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);
    final SubscribedRecord event =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.END_EVENT_OCCURRED);

    assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    assertThat(event.sourceRecordPosition()).isEqualTo(sequenceFlow.position());
    assertThat(event.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 1)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
  }

  @Test
  public void shouldCompleteWorkflowInstance() {
    // given
    testClient.deploy(Bpmn.createExecutableWorkflow("process").startEvent().endEvent().done());

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    // then
    final SubscribedRecord endEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.END_EVENT_OCCURRED);
    final SubscribedRecord completedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.COMPLETED);

    assertThat(completedEvent.key()).isEqualTo(workflowInstanceKey);
    assertThat(completedEvent.sourceRecordPosition()).isEqualTo(endEvent.position());
    assertThat(completedEvent.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 1)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "");
  }

  @Test
  public void shouldConsumeTokenIfEventHasNoOutgoingSequenceflow() {
    // given
    testClient.deploy(Bpmn.createExecutableWorkflow("process").startEvent().done());

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    // then
    final SubscribedRecord startEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.START_EVENT_OCCURRED);
    final SubscribedRecord completedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.COMPLETED);

    assertThat(completedEvent.key()).isEqualTo(workflowInstanceKey);
    assertThat(completedEvent.sourceRecordPosition()).isEqualTo(startEvent.position());
    assertThat(completedEvent.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 1)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "");
  }

  @Test
  public void shouldConsumeTokenIfActivityHasNoOutgoingSequenceflow() {
    // given
    final WorkflowDefinition definition =
        Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("foo", t -> t.taskType("bar"))
            .done();
    testClient.deploy(definition);
    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    // when
    testClient.completeJobOfType("bar");

    // then
    final SubscribedRecord activityCompleted =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);
    final SubscribedRecord completedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.COMPLETED);

    assertThat(completedEvent.key()).isEqualTo(workflowInstanceKey);
    assertThat(completedEvent.sourceRecordPosition()).isEqualTo(activityCompleted.position());
    assertThat(completedEvent.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 1)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "");
  }

  @Test
  public void shouldActivateServiceTask() {
    // given
    testClient.deploy(
        Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("foo", t -> t.taskType("bar"))
            .endEvent()
            .done());

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    // then
    final SubscribedRecord activityReady =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_READY);
    final SubscribedRecord activatedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);

    assertThat(activatedEvent.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    assertThat(activatedEvent.sourceRecordPosition()).isEqualTo(activityReady.position());
    assertThat(activatedEvent.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 1)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
  }

  @Test
  public void shouldCreateTaskWhenServiceTaskIsActivated() {
    // given
    testClient.deploy(
        Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("foo", t -> t.taskType("bar").taskRetries(5))
            .endEvent()
            .done());

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    // then
    final SubscribedRecord activityActivated =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);
    final SubscribedRecord createJobCmd = testClient.receiveFirstJobCommand(JobIntent.CREATE);

    assertThat(createJobCmd.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    assertThat(createJobCmd.sourceRecordPosition()).isEqualTo(activityActivated.position());
    assertThat(createJobCmd.value())
        .containsEntry(PROP_JOB_TYPE, "bar")
        .containsEntry(PROP_JOB_RETRIES, 5);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldCreateJobWithWorkflowInstanceAndCustomHeaders() {
    // given
    testClient.deploy(
        Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("foo", t -> t.taskType("bar").taskHeader("a", "b").taskHeader("c", "d"))
            .endEvent()
            .done());

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    // then
    final SubscribedRecord event = testClient.receiveFirstJobCommand(JobIntent.CREATE);

    final Map<String, Object> headers = (Map<String, Object>) event.value().get("headers");
    assertThat(headers)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry("workflowDefinitionVersion", 1)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo")
        .containsKey("activityInstanceKey");

    final Map<String, Object> customHeaders =
        (Map<String, Object>) event.value().get("customHeaders");
    assertThat(customHeaders).containsEntry("a", "b").containsEntry("c", "d");
  }

  @Test
  public void shouldCompleteServiceTaskWhenTaskIsCompleted() {
    // given
    final WorkflowDefinition definition =
        Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("foo", t -> t.taskType("bar"))
            .endEvent()
            .done();

    testClient.deploy(definition);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process");

    // when
    testClient.completeJobOfType("bar");

    // then
    final SubscribedRecord activityActivatedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);
    final SubscribedRecord jobCompleted = testClient.receiveFirstJobEvent(JobIntent.COMPLETED);
    final SubscribedRecord activityCompleting =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETING);
    final SubscribedRecord activityCompletedEvent =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

    assertThat(activityCompleting.sourceRecordPosition()).isEqualTo(jobCompleted.position());
    assertThat(activityCompletedEvent.key()).isEqualTo(activityActivatedEvent.key());
    assertThat(activityCompletedEvent.sourceRecordPosition())
        .isEqualTo(activityCompleting.position());
    assertThat(activityCompletedEvent.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
        .containsEntry(PROP_WORKFLOW_VERSION, 1)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
  }

  @Test
  public void shouldSpitOnExclusiveGateway() {
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("workflow")
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

    final long workflowInstance1 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 4));
    final long workflowInstance2 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 8));
    final long workflowInstance3 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 12));

    SubscribedRecord endEvent =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstance1, WorkflowInstanceIntent.END_EVENT_OCCURRED);
    assertThat(endEvent.value()).containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "a");

    endEvent =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstance2, WorkflowInstanceIntent.END_EVENT_OCCURRED);
    assertThat(endEvent.value()).containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "b");

    endEvent =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstance3, WorkflowInstanceIntent.END_EVENT_OCCURRED);
    assertThat(endEvent.value()).containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "c");
  }

  @Test
  public void shouldJoinOnExclusiveGateway() {
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("workflow")
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

    final long workflowInstance1 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 4));
    final long workflowInstance2 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 8));

    testClient.receiveFirstWorkflowInstanceEvent(
        workflowInstance1, WorkflowInstanceIntent.COMPLETED);
    testClient.receiveFirstWorkflowInstanceEvent(
        workflowInstance2, WorkflowInstanceIntent.COMPLETED);

    List<String> takenSequenceFlows =
        testClient
            .receiveEvents()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .filter(r -> (Long) r.value().get("workflowInstanceKey") == workflowInstance1)
            .limit(3)
            .map(s -> (String) s.value().get("activityId"))
            .collect(Collectors.toList());
    assertThat(takenSequenceFlows).contains("s1").doesNotContain("s2");

    takenSequenceFlows =
        testClient
            .receiveEvents()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .filter(r -> (Long) r.value().get("workflowInstanceKey") == workflowInstance2)
            .limit(3)
            .map(s -> (String) s.value().get("activityId"))
            .collect(Collectors.toList());
    assertThat(takenSequenceFlows).contains("s2").doesNotContain("s1");
  }

  @Test
  public void shouldSetSourceRecordPositionCorrectOnJoinXor() {
    // given
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("workflow")
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

    // when
    final long workflowInstance1 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 4));

    // then
    testClient.receiveFirstWorkflowInstanceEvent(
        workflowInstance1, WorkflowInstanceIntent.COMPLETED);

    List<SubscribedRecord> sequenceFlows =
        testClient
            .receiveEvents()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .limit(3)
            .collect(Collectors.toList());

    List<SubscribedRecord> gateWays =
        testClient
            .receiveEvents()
            .withIntent(WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(gateWays.get(0).sourceRecordPosition()).isEqualTo(sequenceFlows.get(0).position());
    assertThat(sequenceFlows.get(1).value().get("activityId")).isEqualTo("s1");
    assertThat(gateWays.get(1).sourceRecordPosition()).isEqualTo(sequenceFlows.get(1).position());

    // when
    final long workflowInstance2 =
        testClient.createWorkflowInstance("workflow", asMsgPack("foo", 8));

    // then
    testClient.receiveFirstWorkflowInstanceEvent(
        workflowInstance2, WorkflowInstanceIntent.COMPLETED);

    sequenceFlows =
        testClient
            .receiveEvents()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .filter(r -> (Long) r.value().get("workflowInstanceKey") == workflowInstance2)
            .limit(3)
            .collect(Collectors.toList());

    gateWays =
        testClient
            .receiveEvents()
            .withIntent(WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .filter(r -> (Long) r.value().get("workflowInstanceKey") == workflowInstance2)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(gateWays.get(0).sourceRecordPosition()).isEqualTo(sequenceFlows.get(0).position());
    assertThat(sequenceFlows.get(1).value().get("activityId")).isEqualTo("s2");
    assertThat(gateWays.get(1).sourceRecordPosition()).isEqualTo(sequenceFlows.get(1).position());
  }

  @Test
  public void testWorkflowInstanceStatesWithServiceTask() {
    // given
    final WorkflowDefinition definition =
        Bpmn.createExecutableWorkflow("process")
            .startEvent("a")
            .serviceTask("b", t -> t.taskType("foo"))
            .endEvent("c")
            .done();

    testClient.deploy(definition);

    testClient.createWorkflowInstance("process");

    // when
    testClient.completeJobOfType("foo");

    // then
    final List<SubscribedRecord> workflowEvents =
        testClient.receiveRecords().ofTypeWorkflowInstance().limit(11).collect(Collectors.toList());

    assertThat(workflowEvents)
        .extracting(e -> e.intent())
        .containsExactly(
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
  public void testWorkflowInstanceStatesWithExclusiveGateway() {
    // given
    final WorkflowDefinition workflowDefinition =
        Bpmn.createExecutableWorkflow("workflow")
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
    final List<SubscribedRecord> workflowEvents =
        testClient.receiveRecords().ofTypeWorkflowInstance().limit(8).collect(Collectors.toList());

    assertThat(workflowEvents)
        .extracting(e -> e.intent())
        .containsExactly(
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
  public void shouldCreateAndCompleteInstanceOfYamlWorkflow() throws URISyntaxException {
    // given
    final File yamlFile =
        new File(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
    final String yamlWorkflow = Files.contentOf(yamlFile, UTF_8);

    final Map<String, Object> deploymentResource = new HashMap<>();
    deploymentResource.put("resource", yamlWorkflow.getBytes(UTF_8));
    deploymentResource.put("resourceType", ResourceType.YAML_WORKFLOW);
    deploymentResource.put("resourceName", "simple-workflow.yaml");

    final ExecuteCommandResponse deploymentResp =
        apiRule
            .createCmdRequest()
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
    final SubscribedRecord event =
        testClient
            .receiveEvents()
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

  /**
   * Scenario: When system partition stream processor is reprocessing after restart, it may not be
   * able to return a workflow that it already returned successfully before restart.
   */
  @Test
  public void shouldReprocessWorkflowInstanceRecordsWhenWorkflowIsTemporarilyUnavailable() {
    // given
    // make a couple of deployments to ensure that the deployment stream processor will need some
    // time for reprocessing
    final int numDeployments = 100;
    for (int i = 0; i < numDeployments; i++) {
      testClient.deploy(
          Bpmn.createExecutableWorkflow("process")
              .startEvent()
              .serviceTask("foo", t -> t.taskType("bar"))
              .endEvent()
              .done());
    }

    testClient.createWorkflowInstance("process");

    brokerRule.stopBroker();
    brokerRule.purgeSnapshots();

    // when
    brokerRule.startBroker();

    // then I can still start workflow instance (i.e. stream processor did not crash
    final long newWorkflowInstancekey = testClient.createWorkflowInstance("process");
    assertThat(newWorkflowInstancekey).isGreaterThan(0);
  }
}

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
package io.zeebe.broker.workflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeMappingType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestPartitionClient;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ParallelGatewayTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance FORK_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .parallelGateway("fork")
          .serviceTask("task1", b -> b.zeebeTaskType("type1"))
          .endEvent("end1")
          .moveToNode("fork")
          .serviceTask("task2", b -> b.zeebeTaskType("type2"))
          .endEvent("end2")
          .done();

  private static final BpmnModelInstance FORK_JOIN_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .parallelGateway("fork")
          .sequenceFlowId("flow1")
          .parallelGateway("join")
          .endEvent("end")
          .moveToNode("fork")
          .sequenceFlowId("flow2")
          .connectTo("join")
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private TestPartitionClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partition();
  }

  @Test
  public void shouldActivateTasksOnParallelBranches() {
    // given
    testClient.deploy(FORK_PROCESS);

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final List<SubscribedRecord> taskEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .filter(e -> isServiceTaskInProcess((String) e.value().get("activityId"), FORK_PROCESS))
            .limit(2)
            .collect(Collectors.toList());

    assertThat(taskEvents).hasSize(2);
    assertThat(taskEvents)
        .extracting(e -> e.value().get("activityId"))
        .containsExactlyInAnyOrder("task1", "task2");
    assertThat(taskEvents.get(0).key()).isNotEqualTo(taskEvents.get(1).key());
  }

  @Test
  public void shouldCompleteScopeWhenAllPathsCompleted() {
    // given
    testClient.deploy(FORK_PROCESS);
    testClient.createWorkflowInstance(PROCESS_ID);
    testClient.completeJobOfType("type1");

    // when
    testClient.completeJobOfType("type2");

    // then
    final List<SubscribedRecord> completedEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .limit(3)
            .collect(Collectors.toList());

    assertThat(completedEvents)
        .extracting(e -> e.value().get("activityId"))
        .containsExactly("task1", "task2", PROCESS_ID);
  }

  @Test
  public void shouldCompleteScopeWithMultipleTokensOnSamePath() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .exclusiveGateway("join")
            .endEvent("end")
            .moveToNode("fork")
            .connectTo("join")
            .done();

    testClient.deploy(process);

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final SubscribedRecord completedEvent =
        testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    final List<SubscribedRecord> workflowInstanceEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .limit(r -> r.position() == completedEvent.position())
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(e -> e.value().get("activityId"), e -> e.intent())
        .containsSubsequence(
            tuple("end", WorkflowInstanceIntent.END_EVENT_OCCURRED),
            tuple("end", WorkflowInstanceIntent.END_EVENT_OCCURRED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldPropagatePayloadOnSplit() {
    // given
    testClient.deploy(FORK_PROCESS);
    final byte[] payload = BufferUtil.bufferAsArray(MsgPackUtil.asMsgPack("key", "val"));

    // when
    testClient.createWorkflowInstance(PROCESS_ID, payload);

    // then
    final List<SubscribedRecord> taskEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .filter(e -> isServiceTaskInProcess((String) e.value().get("activityId"), FORK_PROCESS))
            .limit(2)
            .collect(Collectors.toList());

    assertThat(taskEvents)
        .extracting(e -> e.value().get("payload"))
        .allSatisfy(p -> p.equals(payload));
  }

  @Test
  public void shouldPassThroughParallelGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .sequenceFlowId("flow1")
            .parallelGateway("fork")
            .sequenceFlowId("flow2")
            .endEvent("end")
            .done();

    testClient.deploy(process);

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final SubscribedRecord completedEvent =
        testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    final List<SubscribedRecord> workflowInstanceEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .limit(r -> r.position() == completedEvent.position())
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(e -> e.value().get("activityId"), e -> e.intent())
        .containsSequence(
            tuple("fork", WorkflowInstanceIntent.GATEWAY_ACTIVATED),
            tuple("flow2", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("end", WorkflowInstanceIntent.END_EVENT_OCCURRED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETING));
  }

  @Test
  public void shouldCompleteScopeOnParallelGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .sequenceFlowId("flow1")
            .parallelGateway("fork")
            .done();

    testClient.deploy(process);

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final SubscribedRecord completedEvent =
        testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    final List<SubscribedRecord> workflowInstanceEvents =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .limit(r -> r.position() == completedEvent.position())
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(e -> e.value().get("activityId"), e -> e.intent())
        .containsSequence(
            tuple("fork", WorkflowInstanceIntent.GATEWAY_ACTIVATED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETING));
  }

  @Test
  public void shouldMergeParallelBranches() {
    // given
    testClient.deploy(FORK_JOIN_PROCESS);

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final List<SubscribedRecord> events =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .limit(
                r ->
                    r.key() == workflowInstanceKey
                        && WorkflowInstanceIntent.ELEMENT_COMPLETED == r.intent())
            .collect(Collectors.toList());

    assertThat(events)
        .extracting(e -> e.value().get("activityId"), e -> e.intent())
        .containsSubsequence(
            tuple("flow1", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("join", WorkflowInstanceIntent.GATEWAY_ACTIVATED))
        .containsSubsequence(
            tuple("flow2", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("join", WorkflowInstanceIntent.GATEWAY_ACTIVATED))
        .containsOnlyOnce(tuple("join", WorkflowInstanceIntent.GATEWAY_ACTIVATED));
  }

  @Test
  public void shouldOnlyTriggerGatewayWhenAllBranchesAreActivated() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .exclusiveGateway("exclusiveJoin")
            .moveToLastGateway()
            .connectTo("exclusiveJoin")
            .sequenceFlowId("joinFlow1")
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask("waitState", b -> b.zeebeTaskType("type"))
            .sequenceFlowId("joinFlow2")
            .connectTo("join")
            .endEvent()
            .done();

    testClient.deploy(process);

    testClient.createWorkflowInstance(PROCESS_ID);

    // waiting until we have signalled the first incoming sequence flow twice
    // => this should not trigger the gateway yet
    testClient
        .receiveEvents()
        .ofTypeWorkflowInstance()
        .limit(r -> "joinFlow1".equals(r.value().get("activityId")))
        .limit(2)
        .skip(1)
        .findFirst();

    // when
    // we complete the job
    testClient.completeJobOfType("type");

    // then
    final List<SubscribedRecord> events =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .limit(
                r ->
                    "join".equals(r.value().get("activityId"))
                        && WorkflowInstanceIntent.GATEWAY_ACTIVATED == r.intent())
            .collect(Collectors.toList());

    assertThat(events)
        .extracting(e -> e.value().get("activityId"), e -> e.intent())
        .containsSubsequence(
            tuple("joinFlow1", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("joinFlow1", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("joinFlow2", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("join", WorkflowInstanceIntent.GATEWAY_ACTIVATED));
  }

  @Test
  public void shouldMergeAndSplitInOneGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .parallelGateway("join-fork")
            .moveToNode("fork")
            .connectTo("join-fork")
            .serviceTask("task1", b -> b.zeebeTaskType("type1"))
            .moveToLastGateway()
            .serviceTask("task2", b -> b.zeebeTaskType("type2"))
            .done();

    testClient.deploy(process);

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final List<SubscribedRecord> elementInstances =
        testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .filter(r -> r.intent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .limit(3)
            .collect(Collectors.toList());

    assertThat(elementInstances)
        .extracting(e -> e.value().get("activityId"))
        .contains(PROCESS_ID, "task1", "task2");
  }

  @Test
  public void shouldMergePayloadsWithInstructions() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .sequenceFlow(b -> b.payloadMapping("$.key1", "$.mappedKey1"))
            .parallelGateway("join")
            .moveToNode("fork")
            .sequenceFlow(b -> b.payloadMapping("$.key2", "$.mappedKey2"))
            .connectTo("join")
            .endEvent()
            .done();

    testClient.deploy(process);

    final String payload = "{'key1': 'val1', 'key2': 'val2'}";

    // when
    testClient.createWorkflowInstance(PROCESS_ID, MsgPackUtil.asMsgPack(payload));

    // then
    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withActivityId(PROCESS_ID)
            .getFirst();

    final String actualPayload = completedEvent.getValue().getPayload();
    final String expectedPayload =
        "{'key1': 'val1', 'key2': 'val2', 'mappedKey1': 'val1', 'mappedKey2': 'val2'}";

    JsonUtil.assertEquality(actualPayload, expectedPayload);
  }

  @Test
  public void shouldMergePayloadsWithCollectInstructions() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .sequenceFlow(b -> b.payloadMapping("$.key1", "$.array", ZeebeMappingType.COLLECT))
            .parallelGateway("join")
            .moveToNode("fork")
            .sequenceFlow(b -> b.payloadMapping("$.key2", "$.array", ZeebeMappingType.COLLECT))
            .connectTo("join")
            .endEvent()
            .done();

    testClient.deploy(process);

    final String payload = "{'key1': 'val1', 'key2': 'val2'}";

    // when
    testClient.createWorkflowInstance(PROCESS_ID, MsgPackUtil.asMsgPack(payload));

    // then
    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withActivityId(PROCESS_ID)
            .getFirst();

    final String actualPayload = completedEvent.getValue().getPayload();
    final JsonNode jsonPayload = JsonUtil.asJsonNode(actualPayload);

    final JsonNode arrayValue = jsonPayload.get("array");
    assertThat(arrayValue).isNotNull();
    assertThat(arrayValue.isArray()).isTrue();
    assertThat(arrayValue.elements())
        .hasSize(2)
        .extracting(n -> n.textValue())
        .containsExactlyInAnyOrder("val1", "val2");
  }

  /** In case no mapping is defined */
  @Test
  public void shouldMergePayloads() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask("task1", b -> b.zeebeTaskType("task1"))
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask("task2", b -> b.zeebeTaskType("task2"))
            .connectTo("join")
            .endEvent()
            .done();

    testClient.deploy(modelInstance);
    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("task1", MsgPackUtil.asMsgPack("{'key1': 'val1'}"));
    testClient.completeJobOfType("task2", MsgPackUtil.asMsgPack("{'key2': 'val2'}"));

    // then
    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withActivityId(PROCESS_ID)
            .getFirst();

    final String actualPayload = completedEvent.getValue().getPayload();
    final String expectedPayload = "{'key1': 'val1', 'key2': 'val2'}";

    JsonUtil.assertEquality(actualPayload, expectedPayload);
  }

  @Test
  public void shouldMergeNullValueIfMappingHasNoResult() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .sequenceFlow(b -> b.payloadMapping("$.notAValidKey", "$.newKey"))
            .parallelGateway("join")
            .moveToNode("fork")
            .connectTo("join")
            .endEvent()
            .done();

    testClient.deploy(modelInstance);

    // when
    testClient.createWorkflowInstance(PROCESS_ID, MsgPackUtil.asMsgPack("{'key': 'val'}"));

    // then
    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withActivityId(PROCESS_ID)
            .getFirst();

    final String actualPayload = completedEvent.getValue().getPayload();
    JsonUtil.assertEquality(actualPayload, "{'key': 'val', 'newKey': null}");
  }

  @Test
  public void shouldCollectNullValueIfMappingHasNoResult() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .sequenceFlow(
                b -> b.payloadMapping("$.notAValidKey", "$.arr", ZeebeMappingType.COLLECT))
            .parallelGateway("join")
            .moveToNode("fork")
            .connectTo("join")
            .endEvent()
            .done();

    testClient.deploy(modelInstance);

    // when
    testClient.createWorkflowInstance(PROCESS_ID, MsgPackUtil.asMsgPack("{'key': 'val'}"));

    // then
    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withActivityId(PROCESS_ID)
            .getFirst();

    final String actualPayload = completedEvent.getValue().getPayload();
    JsonUtil.assertEquality(actualPayload, "{'key': 'val', 'arr': [null]}");
  }

  private static boolean isServiceTaskInProcess(String activityId, BpmnModelInstance process) {
    return process
        .getModelElementsByType(ServiceTask.class)
        .stream()
        .anyMatch(t -> t.getId().equals(activityId));
  }
}

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
package io.zeebe.broker.workflow.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Assertions;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.record.value.job.Headers;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class EmbeddedSubProcessTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance ONE_TASK_SUBPROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .sequenceFlowId("flow1")
          .subProcess("subProcess")
          .embeddedSubProcess()
          .startEvent("subProcessStart")
          .sequenceFlowId("subProcessFlow1")
          .serviceTask("subProcessTask", b -> b.zeebeTaskType("type"))
          .sequenceFlowId("subProcessFlow2")
          .endEvent("subProcessEnd")
          .subProcessDone()
          .sequenceFlowId("flow2")
          .endEvent("end")
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldCreateJobForServiceTaskInEmbeddedSubprocess() {
    // given
    testClient.deploy(ONE_TASK_SUBPROCESS);
    final byte[] payload = BufferUtil.bufferAsArray(MsgPackUtil.asMsgPack("key", "val"));

    // when
    testClient.createWorkflowInstance(PROCESS_ID, payload);

    // then
    final Record<JobRecordValue> jobCreatedEvent =
        testClient.receiveFirstJobEvent(JobIntent.CREATED);
    MsgPackUtil.assertEquality(payload, jobCreatedEvent.getValue().getPayload());

    final Headers headers = jobCreatedEvent.getValue().getHeaders();
    Assertions.assertThat(headers).hasElementId("subProcessTask");
  }

  @Test
  public void shouldGenerateEventStream() {
    // given
    testClient.deploy(ONE_TASK_SUBPROCESS);
    final byte[] payload = BufferUtil.bufferAsArray(MsgPackUtil.asMsgPack("key", "val"));

    // when
    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID, payload);

    // then
    testClient.receiveJobs().getFirst();

    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceEvents =
        testClient
            .receiveWorkflowInstances()
            .limit(
                r ->
                    r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED
                        && "subProcessTask".equals(r.getValue().getElementId()))
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(e -> e.getMetadata().getIntent(), e -> e.getValue().getElementId())
        .containsExactly(
            tuple(WorkflowInstanceIntent.CREATE, ""),
            tuple(WorkflowInstanceIntent.ELEMENT_READY, PROCESS_ID),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, PROCESS_ID),
            tuple(WorkflowInstanceIntent.START_EVENT_OCCURRED, "start"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "flow1"),
            tuple(WorkflowInstanceIntent.ELEMENT_READY, "subProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "subProcess"),
            tuple(WorkflowInstanceIntent.START_EVENT_OCCURRED, "subProcessStart"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "subProcessFlow1"),
            tuple(WorkflowInstanceIntent.ELEMENT_READY, "subProcessTask"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "subProcessTask"));

    final Record<WorkflowInstanceRecordValue> subProcessReady = workflowInstanceEvents.get(5);
    assertThat(subProcessReady.getValue().getScopeInstanceKey()).isEqualTo(workflowInstanceKey);

    final Record<WorkflowInstanceRecordValue> subProcessTaskReady = workflowInstanceEvents.get(9);
    assertThat(subProcessTaskReady.getValue().getScopeInstanceKey())
        .isEqualTo(subProcessReady.getKey());
  }

  @Test
  public void shouldCompleteEmbeddedSubProcess() {
    // given
    testClient.deploy(ONE_TASK_SUBPROCESS);
    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("type");

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceEvents =
        testClient
            .receiveWorkflowInstances()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(e -> e.getMetadata().getIntent(), e -> e.getValue().getElementId())
        .containsExactly(
            tuple(WorkflowInstanceIntent.CREATE, ""),
            tuple(WorkflowInstanceIntent.ELEMENT_READY, PROCESS_ID),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, PROCESS_ID),
            tuple(WorkflowInstanceIntent.START_EVENT_OCCURRED, "start"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "flow1"),
            tuple(WorkflowInstanceIntent.ELEMENT_READY, "subProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "subProcess"),
            tuple(WorkflowInstanceIntent.START_EVENT_OCCURRED, "subProcessStart"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "subProcessFlow1"),
            tuple(WorkflowInstanceIntent.ELEMENT_READY, "subProcessTask"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "subProcessTask"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "subProcessTask"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "subProcessTask"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "subProcessFlow2"),
            tuple(WorkflowInstanceIntent.END_EVENT_OCCURRED, "subProcessEnd"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "subProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "subProcess"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "flow2"),
            tuple(WorkflowInstanceIntent.END_EVENT_OCCURRED, "end"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, PROCESS_ID),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, PROCESS_ID));
  }

  @Test
  public void shouldRunServiceTaskAfterEmbeddedSubProcess() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess()
            .embeddedSubProcess()
            .startEvent()
            .endEvent()
            .subProcessDone()
            .serviceTask("task", b -> b.zeebeTaskType("type"))
            .endEvent()
            .done();

    testClient.deploy(model);

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final Record<JobRecordValue> jobCreatedEvent =
        testClient.receiveFirstJobEvent(JobIntent.CREATED);

    final Headers headers = jobCreatedEvent.getValue().getHeaders();
    Assertions.assertThat(headers).hasElementId("task");
  }

  @Test
  public void shouldApplyInputMappings() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("subProces", b -> b.zeebeInput("$.key", "$.mappedKey"))
            .embeddedSubProcess()
            .startEvent()
            .serviceTask("task", b -> b.zeebeTaskType("type"))
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();

    final byte[] payload = BufferUtil.bufferAsArray(MsgPackUtil.asMsgPack("key", "val"));
    final byte[] expectedMappedPayload =
        BufferUtil.bufferAsArray(MsgPackUtil.asMsgPack("mappedKey", "val"));

    testClient.deploy(model);

    // when
    testClient.createWorkflowInstance(PROCESS_ID, payload);

    // then
    final Record<JobRecordValue> jobCreatedEvent =
        testClient.receiveFirstJobEvent(JobIntent.CREATED);
    MsgPackUtil.assertEquality(expectedMappedPayload, jobCreatedEvent.getValue().getPayload());
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("subProces", b -> b.zeebeOutput("$.key", "$.mappedKey"))
            .embeddedSubProcess()
            .startEvent()
            .serviceTask("task", b -> b.zeebeTaskType("type"))
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();

    final byte[] payload = BufferUtil.bufferAsArray(MsgPackUtil.asMsgPack("key", "val"));
    final byte[] expectedMappedPayload =
        BufferUtil.bufferAsArray(MsgPackUtil.asMsgPack("mappedKey", "val"));

    testClient.deploy(model);
    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("type", payload);

    // then
    final Record<WorkflowInstanceRecordValue> instanceCompletedEvent =
        testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    MsgPackUtil.assertEquality(
        expectedMappedPayload, instanceCompletedEvent.getValue().getPayload());
  }

  @Test
  public void shouldApplyBothMappings() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subProcess", b -> b.zeebeInput("$.key", "$.foo").zeebeOutput("$.foo", "$.key"))
            .embeddedSubProcess()
            .startEvent()
            .serviceTask("task", b -> b.zeebeTaskType("type"))
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();

    final byte[] payload = BufferUtil.bufferAsArray(MsgPackUtil.asMsgPack("key", "val"));
    final byte[] otherPayload = BufferUtil.bufferAsArray(MsgPackUtil.asMsgPack("foo", "val2"));

    final byte[] expectedMappedPayload =
        BufferUtil.bufferAsArray(MsgPackUtil.asMsgPack("key", "val2"));

    testClient.deploy(model);
    testClient.createWorkflowInstance(PROCESS_ID, payload);

    // when
    testClient.completeJobOfType("type", otherPayload);

    // then
    final Record<WorkflowInstanceRecordValue> instanceCompletedEvent =
        testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);

    MsgPackUtil.assertEquality(
        expectedMappedPayload, instanceCompletedEvent.getValue().getPayload());
  }

  @Test
  public void shouldCompleteNestedSubProcess() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("outerSubProcess")
            .embeddedSubProcess()
            .startEvent()
            .subProcess("innerSubProcess")
            .embeddedSubProcess()
            .startEvent()
            .serviceTask("task", b -> b.zeebeTaskType("type"))
            .endEvent()
            .subProcessDone()
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();
    testClient.deploy(model);
    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("type");

    // then
    testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);

    final List<String> elementFilter = Arrays.asList("innerSubProcess", "outerSubProcess", "task");

    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceEvents =
        testClient
            .receiveWorkflowInstances()
            .filter(r -> elementFilter.contains(r.getValue().getElementId()))
            .limit(12)
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(e -> e.getMetadata().getIntent(), e -> e.getValue().getElementId())
        .containsExactly(
            tuple(WorkflowInstanceIntent.ELEMENT_READY, "outerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "outerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_READY, "innerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "innerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_READY, "task"),
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATED, "task"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "task"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "task"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "innerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "innerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETING, "outerSubProcess"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "outerSubProcess"));
  }
}

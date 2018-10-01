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

import static io.zeebe.exporter.record.Assertions.assertThat;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_ACTIVITY_ID;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_INSTANCE_KEY;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_VERSION;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.CANCEL;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestPartitionClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CancelWorkflowInstanceTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType("test").zeebeTaskRetries(5))
          .endEvent()
          .done();

  private static final BpmnModelInstance SUB_PROCESS_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .subProcess("subProcess")
          .embeddedSubProcess()
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType("test").zeebeTaskRetries(5))
          .endEvent()
          .subProcessDone()
          .endEvent()
          .done();

  private static final BpmnModelInstance FORK_PROCESS;

  static {
    final AbstractFlowNodeBuilder<?, ?> builder =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .parallelGateway("fork")
            .serviceTask("task1", b -> b.zeebeTaskType("type1"))
            .endEvent("end1")
            .moveToNode("fork");

    FORK_PROCESS =
        builder.serviceTask("task2", b -> b.zeebeTaskType("type2")).endEvent("end2").done();
  }

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private TestPartitionClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partition();
  }

  @Test
  public void shouldCancelWorkflowInstance() {
    // given
    testClient.deploy(WORKFLOW);
    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);
    testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    final SubscribedRecord cancelWorkflow =
        testClient.receiveFirstWorkflowInstanceCommand(WorkflowInstanceIntent.CANCEL);

    assertThat(response.sourceRecordPosition()).isEqualTo(cancelWorkflow.position());
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.CANCELING);

    final SubscribedRecord workflowInstanceCanceledEvent =
        testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    assertThat(workflowInstanceCanceledEvent.key()).isEqualTo(workflowInstanceKey);
    assertThat(workflowInstanceCanceledEvent.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, PROCESS_ID)
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, PROCESS_ID);

    final List<SubscribedRecord> workflowEvents =
        testClient
            .receiveRecords()
            .ofTypeWorkflowInstance()
            .skipUntil(r -> r.intent() == WorkflowInstanceIntent.CANCEL)
            .limit(6)
            .collect(Collectors.toList());

    assertThat(workflowEvents)
        .extracting(e -> e.value().get("activityId"), e -> e.intent())
        .containsExactly(
            tuple(null, WorkflowInstanceIntent.CANCEL),
            tuple(PROCESS_ID, WorkflowInstanceIntent.CANCELING),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldCancelWorkflowInstanceWithEmbeddedSubProcess() {
    // given
    testClient.deploy(SUB_PROCESS_WORKFLOW);
    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);
    testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    cancelWorkflowInstance(workflowInstanceKey);

    // then
    final List<SubscribedRecord> workflowEvents =
        testClient
            .receiveRecords()
            .ofTypeWorkflowInstance()
            .skipUntil(r -> r.intent() == WorkflowInstanceIntent.CANCEL)
            .limit(8)
            .collect(Collectors.toList());

    assertThat(workflowEvents)
        .extracting(e -> e.value().get("activityId"), e -> e.intent())
        .containsExactly(
            tuple(null, WorkflowInstanceIntent.CANCEL),
            tuple(PROCESS_ID, WorkflowInstanceIntent.CANCELING),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("subProcess", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple("subProcess", WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldCancelActivityInstance() {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);

    final SubscribedRecord activityActivatedEvent =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.CANCELING);

    final SubscribedRecord activityTerminatedEvent =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_TERMINATED);

    assertThat(activityTerminatedEvent.key()).isEqualTo(activityActivatedEvent.key());
    assertThat(activityTerminatedEvent.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, PROCESS_ID)
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "task");
  }

  @Test
  public void shouldCancelWorkflowInstanceWithParallelExecution() {
    // given
    testClient.deploy(FORK_PROCESS);
    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);
    testClient.receiveElementInState("task1", WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    testClient.receiveElementInState("task2", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    cancelWorkflowInstance(workflowInstanceKey);

    // then
    final List<SubscribedRecord> workflowEvents =
        testClient
            .receiveRecords()
            .ofTypeWorkflowInstance()
            .skipUntil(r -> r.intent() == WorkflowInstanceIntent.CANCEL)
            .limit(
                r ->
                    r.key() == workflowInstanceKey
                        && r.intent() == WorkflowInstanceIntent.ELEMENT_TERMINATED)
            .collect(Collectors.toList());

    final List<SubscribedRecord> terminatedElements =
        workflowEvents
            .stream()
            .filter(r -> r.intent() == WorkflowInstanceIntent.ELEMENT_TERMINATED)
            .collect(Collectors.toList());

    assertThat(terminatedElements).hasSize(3);
    assertThat(terminatedElements.subList(0, 2))
        .extracting(r -> r.value().get("activityId"))
        .contains("task1", "task2");

    final SubscribedRecord processTerminatedEvent = terminatedElements.get(2);
    assertThat(processTerminatedEvent.value().get("activityId")).isEqualTo(PROCESS_ID);
  }

  @Test
  public void shouldCancelIntermediateCatchEvent() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess("wf")
            .startEvent()
            .intermediateCatchEvent("catch-event")
            .message(b -> b.name("msg").zeebeCorrelationKey("$.id"))
            .done());

    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("id", "123"));

    final SubscribedRecord catchEventEntered =
        testClient.receiveElementInState("catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.CANCELING);

    final SubscribedRecord activityTerminatingEvent =
        testClient.receiveElementInState("catch-event", WorkflowInstanceIntent.ELEMENT_TERMINATING);
    final SubscribedRecord activityTerminatedEvent =
        testClient.receiveElementInState("catch-event", WorkflowInstanceIntent.ELEMENT_TERMINATED);

    assertThat(activityTerminatedEvent.key()).isEqualTo(catchEventEntered.key());
    assertThat(activityTerminatedEvent.sourceRecordPosition())
        .isEqualTo(activityTerminatingEvent.position());
    assertThat(activityTerminatedEvent.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "wf")
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "catch-event");
  }

  @Test
  public void shouldCancelJobForActivity() {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);

    final SubscribedRecord jobCreatedEvent =
        testClient.receiveEvents().ofTypeJob().withIntent(JobIntent.CREATED).getFirst();

    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.CANCELING);

    final SubscribedRecord terminateActivity =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_TERMINATING);
    final SubscribedRecord jobCancelCmd = testClient.receiveFirstJobCommand(JobIntent.CANCEL);
    final SubscribedRecord jobCanceledEvent = testClient.receiveFirstJobEvent(JobIntent.CANCELED);

    assertThat(jobCanceledEvent.key()).isEqualTo(jobCreatedEvent.key());
    assertThat(jobCancelCmd.sourceRecordPosition()).isEqualTo(terminateActivity.position());
    assertThat(jobCanceledEvent.sourceRecordPosition()).isEqualTo(jobCancelCmd.position());

    @SuppressWarnings("unchecked")
    final Map<String, Object> headers =
        (Map<String, Object>) jobCanceledEvent.value().get("headers");
    assertThat(headers)
        .containsEntry("workflowInstanceKey", workflowInstanceKey)
        .containsEntry("bpmnProcessId", PROCESS_ID)
        .containsEntry("workflowDefinitionVersion", 1L)
        .containsEntry("activityId", "task");
  }

  @Test
  public void shouldRejectCancelNonExistingWorkflowInstance() {
    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(-1L);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Workflow instance is not running");

    final SubscribedRecord cancelCommand = testClient.receiveFirstWorkflowInstanceCommand(CANCEL);
    final SubscribedRecord cancelRejection =
        testClient
            .receiveRejections()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.CANCEL)
            .getFirst();

    assertThat(cancelRejection).isNotNull();
    assertThat(cancelRejection.sourceRecordPosition()).isEqualTo(cancelCommand.position());
  }

  @Test
  public void shouldRejectCancelCompletedWorkflowInstance() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done());

    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);

    testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);

    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Workflow instance is not running");

    final SubscribedRecord cancelCommand = testClient.receiveFirstWorkflowInstanceCommand(CANCEL);
    final SubscribedRecord cancelRejection =
        testClient
            .receiveRejections()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.CANCEL)
            .getFirst();

    assertThat(cancelRejection).isNotNull();
    assertThat(cancelRejection.sourceRecordPosition()).isEqualTo(cancelCommand.position());
  }

  @Test
  public void shouldRejectCancelAlreadyCanceledWorkflowInstance() {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);
    cancelWorkflowInstance(workflowInstanceKey);

    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Workflow instance is not running");
  }

  @Test
  public void shouldWriteEntireEventOnCancel() {
    // given
    testClient.deploy(WORKFLOW);
    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);
    final Record<WorkflowInstanceRecordValue> activatedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withActivityId(PROCESS_ID)
            .getFirst();

    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    MsgPackUtil.assertEqualityExcluding(
        response.getRawValue(), activatedEvent.getValue().toJson(), "payload");

    final Record<WorkflowInstanceRecordValue> cancelingEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.CANCELING)
            .withActivityId(PROCESS_ID)
            .getFirst();

    assertThat(cancelingEvent.getValue()).isEqualTo(activatedEvent.getValue());
  }

  private ExecuteCommandResponse cancelWorkflowInstance(final long workflowInstanceKey) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCEL)
        .key(workflowInstanceKey)
        .command()
        .done()
        .sendAndAwait();
  }
}

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
package io.zeebe.broker.engine;

import static io.zeebe.broker.engine.JobAssert.PROCESS_ID;
import static io.zeebe.broker.engine.JobAssert.assertJobHeaders;
import static io.zeebe.broker.engine.WorkflowAssert.assertWorkflowInstanceRecord;
import static io.zeebe.exporter.api.record.Assertions.assertThat;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.CANCEL;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
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
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CancelWorkflowInstanceTest {

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
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldCancelWorkflowInstance() {
    // given
    testClient.deploy(WORKFLOW);
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();
    testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.getIntent()).isEqualTo(WorkflowInstanceIntent.ELEMENT_TERMINATING);

    final Record<WorkflowInstanceRecordValue> workflowInstanceCanceledEvent =
        testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    assertThat(workflowInstanceCanceledEvent.getKey()).isEqualTo(workflowInstanceKey);
    assertWorkflowInstanceRecord(workflowInstanceKey, workflowInstanceCanceledEvent);

    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        testClient
            .receiveWorkflowInstances()
            .skipUntil(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.CANCEL)
            .limit(5)
            .collect(Collectors.toList());

    assertThat(workflowEvents)
        .hasSize(5)
        .extracting(e -> e.getValue().getElementId(), e -> e.getMetadata().getIntent())
        .containsSequence(
            tuple("", WorkflowInstanceIntent.CANCEL),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldNotCancelElementInstance() {
    // given
    testClient.deploy(WORKFLOW);
    testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));
    final Record<WorkflowInstanceRecordValue> task =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(task.getKey());

    // then
    assertThat(response.getIntent()).isEqualTo(CANCEL);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(response.getRejectionReason())
        .isEqualTo(
            "Expected to cancel a workflow instance with key '"
                + task.getKey()
                + "', but no such workflow was found");
  }

  @Test
  public void shouldCancelWorkflowInstanceWithEmbeddedSubProcess() {
    // given
    testClient.deploy(SUB_PROCESS_WORKFLOW);
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();
    testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    cancelWorkflowInstance(workflowInstanceKey);

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        testClient
            .receiveWorkflowInstances()
            .skipUntil(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.CANCEL)
            .limit(7)
            .collect(Collectors.toList());

    assertThat(workflowEvents)
        .hasSize(7)
        .extracting(e -> e.getValue().getElementId(), e -> e.getMetadata().getIntent())
        .containsSequence(
            tuple("", WorkflowInstanceIntent.CANCEL),
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

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();

    final Record<WorkflowInstanceRecordValue> activityActivatedEvent =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.getIntent()).isEqualTo(WorkflowInstanceIntent.ELEMENT_TERMINATING);

    final Record<WorkflowInstanceRecordValue> activityTerminatedEvent =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_TERMINATED);

    assertThat(activityTerminatedEvent.getKey()).isEqualTo(activityActivatedEvent.getKey());
    assertWorkflowInstanceRecord(workflowInstanceKey, "task", activityTerminatedEvent);
  }

  @Test
  public void shouldCancelWorkflowInstanceWithParallelExecution() {
    // given
    testClient.deploy(FORK_PROCESS);
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();
    testClient.receiveElementInState("task1", WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    testClient.receiveElementInState("task2", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    cancelWorkflowInstance(workflowInstanceKey);

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        testClient
            .receiveWorkflowInstances()
            .skipUntil(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.CANCEL)
            .limit(
                r ->
                    r.getKey() == workflowInstanceKey
                        && r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_TERMINATED)
            .collect(Collectors.toList());

    final List<Record<WorkflowInstanceRecordValue>> terminatedElements =
        workflowEvents.stream()
            .filter(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_TERMINATED)
            .collect(Collectors.toList());

    assertThat(terminatedElements).hasSize(3);
    assertThat(terminatedElements.subList(0, 2))
        .extracting(r -> r.getValue().getElementId())
        .contains("task1", "task2");

    final Record<WorkflowInstanceRecordValue> processTerminatedEvent = terminatedElements.get(2);
    assertThat(processTerminatedEvent.getValue().getElementId()).isEqualTo(PROCESS_ID);
  }

  @Test
  public void shouldCancelIntermediateCatchEvent() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent("catch-event")
            .message(b -> b.name("msg").zeebeCorrelationKey("id"))
            .done());

    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("id", "123")))
            .getInstanceKey();

    testClient.receiveElementInState("catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.getIntent()).isEqualTo(WorkflowInstanceIntent.ELEMENT_TERMINATING);

    final Record<WorkflowInstanceRecordValue> terminatedEvent =
        testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    assertWorkflowInstanceRecord(workflowInstanceKey, PROCESS_ID, terminatedEvent);
  }

  @Test
  public void shouldCancelJobForActivity() {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();

    final Record<JobRecordValue> jobCreatedEvent =
        testClient.receiveJobs().withIntent(JobIntent.CREATED).getFirst();

    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.getIntent()).isEqualTo(WorkflowInstanceIntent.ELEMENT_TERMINATING);

    final Record<WorkflowInstanceRecordValue> terminateActivity =
        testClient.receiveElementInState("task", WorkflowInstanceIntent.ELEMENT_TERMINATING);
    final Record<JobRecordValue> jobCancelCmd = testClient.receiveFirstJobCommand(JobIntent.CANCEL);
    final Record<JobRecordValue> jobCanceledEvent =
        testClient.receiveFirstJobEvent(JobIntent.CANCELED);

    assertThat(jobCanceledEvent.getKey()).isEqualTo(jobCreatedEvent.getKey());
    assertThat(jobCancelCmd.getSourceRecordPosition()).isEqualTo(terminateActivity.getPosition());
    assertThat(jobCanceledEvent.getSourceRecordPosition()).isEqualTo(jobCancelCmd.getPosition());
    assertJobHeaders(workflowInstanceKey, jobCanceledEvent);
  }

  @Test
  public void shouldRejectCancelNonExistingWorkflowInstance() {
    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(-1L);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(response.getRejectionReason())
        .isEqualTo(
            "Expected to cancel a workflow instance with key '-1', but no such workflow was found");

    final Record<WorkflowInstanceRecordValue> cancelCommand =
        testClient.receiveFirstWorkflowInstanceCommand(CANCEL);
    final Record<WorkflowInstanceRecordValue> cancelRejection =
        testClient
            .receiveWorkflowInstances()
            .onlyCommandRejections()
            .withIntent(WorkflowInstanceIntent.CANCEL)
            .getFirst();

    assertThat(cancelRejection).isNotNull();
    assertThat(cancelRejection.getSourceRecordPosition()).isEqualTo(cancelCommand.getPosition());
  }

  @Test
  public void shouldRejectCancelCompletedWorkflowInstance() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done());

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();

    testClient.receiveElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);

    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(response.getRejectionReason())
        .isEqualTo(
            "Expected to cancel a workflow instance with key '"
                + workflowInstanceKey
                + "', but no such workflow was found");

    final Record<WorkflowInstanceRecordValue> cancelCommand =
        testClient.receiveFirstWorkflowInstanceCommand(CANCEL);
    final Record<WorkflowInstanceRecordValue> cancelRejection =
        testClient
            .receiveWorkflowInstances()
            .onlyCommandRejections()
            .withIntent(WorkflowInstanceIntent.CANCEL)
            .getFirst();

    assertThat(cancelRejection).isNotNull();
    assertThat(cancelRejection.getSourceRecordPosition()).isEqualTo(cancelCommand.getPosition());
  }

  @Test
  public void shouldRejectCancelAlreadyCanceledWorkflowInstance() {
    // given
    testClient.deploy(WORKFLOW);

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();
    cancelWorkflowInstance(workflowInstanceKey);

    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(response.getRejectionReason())
        .isEqualTo(
            "Expected to cancel a workflow instance with key '"
                + workflowInstanceKey
                + "', but no such workflow was found");
  }

  @Test
  public void shouldWriteEntireEventOnCancel() {
    // given
    testClient.deploy(WORKFLOW);
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID)).getInstanceKey();
    final Record<WorkflowInstanceRecordValue> activatedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId(PROCESS_ID)
            .getFirst();

    // when
    final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

    // then
    MsgPackUtil.assertEqualityExcluding(
        response.getRawValue(), activatedEvent.getValue().toJson(), "variables");

    final Record<WorkflowInstanceRecordValue> cancelingEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_TERMINATING)
            .withElementId(PROCESS_ID)
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

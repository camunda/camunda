/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ProcessInstanceBusinessIdExpressionContextTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String BUSINESS_ID = "order-123";
  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldResolveBusinessIdInInputMapping() {
    // given
    final var processId = "pi-ctx-bid-input-mapping";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("input-mapping-job")
                            .zeebeInputExpression("camunda.processInstance.businessId", "piBid"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    RecordingExporter.jobRecords(JobIntent.CREATED).withProcessInstanceKey(pi).await();
    final var job =
        ENGINE.jobs().withType("input-mapping-job").activate().getValue().getJobs().getFirst();
    assertThat(job.getVariables()).containsEntry("piBid", BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInOutputMapping() {
    // given
    final var processId = "pi-ctx-bid-output-mapping";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task1",
                    t ->
                        t.zeebeJobType("output-mapping-job-1")
                            .zeebeOutputExpression("camunda.processInstance.businessId", "piBid"))
                .serviceTask("task2", t -> t.zeebeJobType("output-mapping-job-2"))
                .endEvent()
                .done())
        .deploy();

    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // when
    ENGINE.job().ofInstance(pi).withType("output-mapping-job-1").complete();

    // then
    RecordingExporter.jobRecords(JobIntent.CREATED).withProcessInstanceKey(pi).await();
    final var job2 =
        ENGINE.jobs().withType("output-mapping-job-2").activate().getValue().getJobs().getFirst();
    assertThat(job2.getVariables()).containsEntry("piBid", BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInSequenceFlowCondition() {
    // given
    final var processId = "pi-ctx-bid-sequence-condition";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .exclusiveGateway("gateway")
                .conditionExpression("camunda.processInstance.businessId = \"order-123\"")
                .serviceTask("taken", t -> t.zeebeJobType("taken-job"))
                .endEvent()
                .moveToLastExclusiveGateway()
                .defaultFlow()
                .serviceTask("default", t -> t.zeebeJobType("default-job"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(pi)
                .withElementId("taken")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveBusinessIdInJobTypeExpression() {
    // given
    final var processId = "pi-ctx-bid-job-type";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task", t -> t.zeebeJobTypeExpression("camunda.processInstance.businessId"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    assertThat(job.getType()).isEqualTo(BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInJobRetriesExpression() {
    // given
    final var processId = "pi-ctx-bid-job-retries";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("retries-job")
                            .zeebeJobRetriesExpression(
                                "if camunda.processInstance.businessId = \"order-123\" then 3 else 1"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    assertThat(job.getRetries()).isEqualTo(3);
  }

  @Test
  public void shouldResolveBusinessIdInUserTaskAssigneeExpression() {
    // given
    final var processId = "pi-ctx-bid-assignee";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeAssigneeExpression("camunda.processInstance.businessId")
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    Assertions.assertThat(userTask).hasAssignee(BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInUserTaskCandidateGroupsExpression() {
    // given
    final var processId = "pi-ctx-bid-candidate-groups";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeCandidateGroupsExpression("[camunda.processInstance.businessId]")
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    Assertions.assertThat(userTask).hasCandidateGroupsList(BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInUserTaskCandidateUsersExpression() {
    // given
    final var processId = "pi-ctx-bid-candidate-users";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeCandidateUsersExpression("[camunda.processInstance.businessId]")
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    Assertions.assertThat(userTask).hasCandidateUsersList(BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInExecutionListenerTypeExpression() {
    // given
    final var processId = "pi-ctx-bid-exec-listener";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("exec-listener-task-job")
                            .zeebeExecutionListener(
                                el ->
                                    el.start()
                                        .typeExpression("camunda.processInstance.businessId")))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var listenerJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(pi)
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .getFirst()
            .getValue();
    assertThat(listenerJob.getType()).isEqualTo(BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInTaskListenerTypeExpression() {
    // given
    final var processId = "pi-ctx-bid-task-listener";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeTaskListener(
                    l -> l.completing().typeExpression("camunda.processInstance.businessId"))
                .endEvent()
                .done())
        .deploy();

    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // when
    ENGINE.userTask().ofInstance(pi).complete();

    // then
    final var listenerJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(pi)
            .withJobKind(JobKind.TASK_LISTENER)
            .getFirst()
            .getValue();
    assertThat(listenerJob.getType()).isEqualTo(BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInMultiInstanceInputCollection() {
    // given
    final var processId = "pi-ctx-bid-mi-collection";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("mi-collection-job")
                            .multiInstance(
                                m ->
                                    m.sequential()
                                        .zeebeInputCollectionExpression(
                                            "[camunda.processInstance.businessId]")
                                        .zeebeInputElement("element")))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(pi)
        .withType("mi-collection-job")
        .await();
    final var job =
        ENGINE.jobs().withType("mi-collection-job").activate().getValue().getJobs().getFirst();
    assertThat(job.getVariables()).containsEntry("element", BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInUserTaskDueDateExpression() {
    // given
    final var processId = "pi-ctx-bid-due-date";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeDueDateExpression(
                    "if camunda.processInstance.businessId = \"order-123\" then \"2040-01-01T00:00Z\" else null")
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    assertThat(userTask.getDueDate()).isNotEmpty();
  }

  @Test
  public void shouldResolveBusinessIdInUserTaskFollowUpDateExpression() {
    // given
    final var processId = "pi-ctx-bid-follow-up-date";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeFollowUpDateExpression(
                    "if camunda.processInstance.businessId = \"order-123\" then \"2040-01-01T00:00Z\" else null")
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    assertThat(userTask.getFollowUpDate()).isNotEmpty();
  }

  @Test
  public void shouldResolveBusinessIdInUserTaskPriorityExpression() {
    // given
    final var processId = "pi-ctx-bid-priority";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeTaskPriorityExpression(
                    "if camunda.processInstance.businessId = \"order-123\" then 50 else 1")
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    Assertions.assertThat(userTask).hasPriority(50);
  }

  @Test
  public void shouldResolveBusinessIdInMultiInstanceCompletionCondition() {
    // given
    final var processId = "pi-ctx-bid-mi-condition";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("mi-condition-job")
                            .multiInstance(
                                m ->
                                    m.sequential()
                                        .zeebeInputCollectionExpression("[1, 2, 3]")
                                        .zeebeInputElement("element")
                                        .completionCondition(
                                            "= numberOfCompletedInstances = 1 and camunda.processInstance.businessId = \"order-123\"")))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();
    ENGINE.job().ofInstance(pi).withType("mi-condition-job").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(pi)
                .withElementId("task")
                .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveBusinessIdInMessageCorrelationKeyExpression() {
    // given
    final var processId = "pi-ctx-bid-msg-corr-key";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("msg-catch")
                .message(
                    m ->
                        m.name("test-message")
                            .zeebeCorrelationKeyExpression("camunda.processInstance.businessId"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var subscription =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    assertThat(subscription.getCorrelationKey()).isEqualTo(BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInMessageNameExpression() {
    // given
    final var processId = "pi-ctx-bid-msg-name";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("msg-catch")
                .message(
                    m ->
                        m.nameExpression("camunda.processInstance.businessId")
                            .zeebeCorrelationKeyExpression("\"fixedKey\""))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var subscription =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    assertThat(subscription.getMessageName()).isEqualTo(BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInSignalNameExpression() {
    // given
    final var processId = "pi-ctx-bid-signal-name";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("signal-catch")
                .signal(s -> s.nameExpression("camunda.processInstance.businessId"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    final var subscription =
        RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
            .valueFilter(v -> v.getProcessInstanceKey() == pi)
            .getFirst()
            .getValue();
    assertThat(subscription.getSignalName()).isEqualTo(BUSINESS_ID);
  }

  @Test
  public void shouldResolveBusinessIdInErrorCodeExpression() {
    // given
    final var processId = "pi-ctx-bid-error-code";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "subprocess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .endEvent(
                                "throw-error",
                                e -> e.errorExpression("camunda.processInstance.businessId")))
                .boundaryEvent("caught", b -> b.error())
                .endEvent("boundary-end")
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(pi)
                .limitToProcessInstanceCompleted()
                .onlyEvents())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldResolveBusinessIdInEscalationCodeExpression() {
    // given
    final var processId = "pi-ctx-bid-escalation-code";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "subprocess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .endEvent(
                                "throw-escalation",
                                e -> e.escalationExpression("camunda.processInstance.businessId")))
                .boundaryEvent("caught", b -> b.escalation())
                .endEvent("boundary-end")
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(pi)
                .limitToProcessInstanceCompleted()
                .onlyEvents())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldResolveBusinessIdInIntermediateTimerDurationExpression() {
    // given
    final var processId = "pi-ctx-bid-timer-intermediate";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "timer",
                    c ->
                        c.timerWithDurationExpression(
                            "if camunda.processInstance.businessId = \"order-123\" then \"PT0S\" else \"PT1H\""))
                .endEvent("end")
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(pi)
                .withElementId("end")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveBusinessIdInTimerBoundaryDurationExpression() {
    // given
    final var processId = "pi-ctx-bid-timer-boundary";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("timer-boundary-job"))
                .boundaryEvent(
                    "timer-boundary",
                    b ->
                        b.timerWithDurationExpression(
                            "if camunda.processInstance.businessId = \"order-123\" then \"PT0S\" else \"PT1H\""))
                .endEvent("boundary-end")
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(pi)
                .withElementId("boundary-end")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveBusinessIdInConditionalIntermediateCatchEventExpression() {
    // given
    final var processId = "pi-ctx-bid-conditional";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("conditional-catch")
                .condition(
                    c ->
                        c.condition(
                            "= camunda.processInstance.businessId = \"order-123\" and trigger = true"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();
    ENGINE.variables().ofScope(pi).withDocument(Map.of("trigger", true)).update();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(pi)
                .withElementId("conditional-catch")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveBusinessIdInCallActivityProcessIdExpression() {
    // given
    final var parentProcessId = "pi-ctx-bid-call-activity";
    final var childProcessId = "child";
    final var parent =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(
                "call",
                c ->
                    c.zeebeProcessIdExpression(
                        "if camunda.processInstance.businessId = \"order-123\" then \"child\" else \"x\""))
            .endEvent()
            .done();
    final var child = Bpmn.createExecutableProcess(childProcessId).startEvent().endEvent().done();
    ENGINE
        .deployment()
        .withXmlResource("pi-ctx-bid-call-activity-parent.bpmn", parent)
        .withXmlResource("pi-ctx-bid-call-activity-child.bpmn", child)
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(parentProcessId).withBusinessId(BUSINESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(childProcessId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveBusinessIdInBusinessRuleTaskDecisionIdExpression() {
    // given
    final var processId = "pi-ctx-bid-brt";
    final var decisionDmn =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                     id="pi-ctx-bid-brt-drg" name="pi-ctx-bid-brt-drg"
                     namespace="http://camunda.org/schema/1.0/dmn">
          <decision id="decision" name="Decision">
            <literalExpression>
              <text>"result"</text>
            </literalExpression>
          </decision>
        </definitions>
        """;
    ENGINE
        .deployment()
        .withXmlResource(
            decisionDmn.getBytes(StandardCharsets.UTF_8), "pi-ctx-bid-brt-decision.dmn")
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .businessRuleTask(
                    "task",
                    t ->
                        t.zeebeCalledDecisionIdExpression(
                                "if camunda.processInstance.businessId = \"order-123\" then \"decision\" else \"x\"")
                            .zeebeResultVariable("result"))
                .endEvent()
                .done())
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    assertThat(
            RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.EVALUATED)
                .withDecisionId("decision")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveBusinessIdInsideEmbeddedSubprocess() {
    // given
    final var processId = "pi-ctx-bid-subprocess-scope";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "subprocess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .serviceTask(
                                "sub-task",
                                t ->
                                    t.zeebeJobType("subprocess-scope-bid-job")
                                        .zeebeInputExpression(
                                            "camunda.processInstance.businessId", "piBid"))
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi =
        ENGINE.processInstance().ofBpmnProcessId(processId).withBusinessId(BUSINESS_ID).create();

    // then
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(pi)
        .withType("subprocess-scope-bid-job")
        .await();
    final var job =
        ENGINE
            .jobs()
            .withType("subprocess-scope-bid-job")
            .activate()
            .getValue()
            .getJobs()
            .getFirst();
    assertThat(job.getVariables()).containsEntry("piBid", BUSINESS_ID);
  }

  @Test
  public void shouldResolveToCallingBusinessIdInCallerScope() {
    // given
    final var parentProcessId = "pi-ctx-bid-caller-scope";
    final var childProcessId = "child-caller";
    final var parent =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .serviceTask(
                "caller-task",
                t ->
                    t.zeebeJobType("caller-scope-bid-job")
                        .zeebeInputExpression("camunda.processInstance.businessId", "callerBid"))
            .callActivity("call", c -> c.zeebeProcessId(childProcessId))
            .endEvent()
            .done();
    final var child = Bpmn.createExecutableProcess(childProcessId).startEvent().endEvent().done();
    ENGINE
        .deployment()
        .withXmlResource("caller-bid-parent.bpmn", parent)
        .withXmlResource("caller-bid-child.bpmn", child)
        .deploy();

    // when
    final long parentPi =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withBusinessId(BUSINESS_ID)
            .create();

    // then
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(parentPi)
        .withType("caller-scope-bid-job")
        .await();
    final var job =
        ENGINE.jobs().withType("caller-scope-bid-job").activate().getValue().getJobs().getFirst();
    assertThat(job.getVariables()).containsEntry("callerBid", BUSINESS_ID);
  }

  @Test
  public void shouldResolveToParentBusinessIdInCalledScope() {
    // given
    final var parentProcessId = "pi-ctx-bid-called-scope";
    final var childProcessId = "child-called";
    final var parent =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId(childProcessId))
            .endEvent()
            .done();
    final var child =
        Bpmn.createExecutableProcess(childProcessId)
            .startEvent()
            .serviceTask(
                "child-task",
                t ->
                    t.zeebeJobType("called-scope-bid-job")
                        .zeebeInputExpression("camunda.processInstance.businessId", "childBid"))
            .endEvent()
            .done();
    ENGINE
        .deployment()
        .withXmlResource("called-bid-parent.bpmn", parent)
        .withXmlResource("called-bid-child.bpmn", child)
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(parentProcessId).withBusinessId(BUSINESS_ID).create();

    // then
    final long childPi =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnProcessId(childProcessId)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(childPi)
        .withType("called-scope-bid-job")
        .await();
    final var job =
        ENGINE.jobs().withType("called-scope-bid-job").activate().getValue().getJobs().getFirst();
    assertThat(job.getVariables()).containsEntry("childBid", BUSINESS_ID);
  }

  @Test
  public void shouldReturnNullForBusinessIdInTimerStartEventDurationExpression() {
    // given
    final var processId = "pi-ctx-bid-timer-start-duration";

    // when
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    // no process instance at a timer start event, so the businessId is null ->
                    // PT1S (a real businessId -> PT1H)
                    .timerWithDurationExpression(
                        "if camunda.processInstance.businessId = null then \"PT1S\" else \"PT1H\"")
                    .endEvent()
                    .done())
            .deploy();
    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();
    final long now = ENGINE.getClock().getCurrentTimeInMillis();

    // then
    final var timer =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst()
            .getValue();
    assertThat(timer.getDueDate()).isLessThan(now + 60_000L);
  }

  @Test
  public void shouldReturnNullForBusinessIdInTimerStartEventCycleExpression() {
    // given
    final var processId = "pi-ctx-bid-timer-start-cycle";

    // when
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    // no process instance at a timer start event, so the businessId is null ->
                    // R1/PT1S (a real businessId -> R1/PT1H)
                    .timerWithCycleExpression(
                        "if camunda.processInstance.businessId = null then \"R1/PT1S\" else \"R1/PT1H\"")
                    .endEvent()
                    .done())
            .deploy();
    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();
    final long now = ENGINE.getClock().getCurrentTimeInMillis();

    // then
    final var timer =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst()
            .getValue();
    assertThat(timer.getDueDate()).isLessThan(now + 60_000L);
  }

  @Test
  public void shouldReturnNullForBusinessIdInTimerStartEventDateExpression() {
    // given
    final var processId = "pi-ctx-bid-timer-start-date";

    // when
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .timerWithDateExpression(
                        "if camunda.processInstance.businessId = null then \"2040-01-01T00:00:00Z\" else \"2020-01-01T00:00:00Z\"")
                    .endEvent()
                    .done())
            .deploy();
    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // then
    final var timer =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst()
            .getValue();
    assertThat(timer.getDueDate()).isEqualTo(Instant.parse("2040-01-01T00:00:00Z").toEpochMilli());
  }

  @Test
  public void shouldAcceptBusinessIdExpressionAtDeploymentTime() {
    // given / when
    final var processId = "pi-ctx-bid-validation";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "task", t -> t.zeebeJobTypeExpression("camunda.processInstance.businessId"))
                    .endEvent()
                    .done())
            .deploy();

    // then
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldReturnNullForBusinessIdWhenNotSet() {
    // given
    final var processId = "pi-ctx-bid-unset";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .exclusiveGateway("gateway")
                .conditionExpression("camunda.processInstance.businessId = null")
                .serviceTask("null-branch", t -> t.zeebeJobType("null-branch-job"))
                .endEvent()
                .moveToLastExclusiveGateway()
                .defaultFlow()
                .serviceTask("set-branch", t -> t.zeebeJobType("set-branch-job"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(pi)
                .withElementId("null-branch")
                .exists())
        .isTrue();
  }
}

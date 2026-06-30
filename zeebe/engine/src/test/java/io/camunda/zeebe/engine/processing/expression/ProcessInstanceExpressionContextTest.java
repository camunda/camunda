/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.nio.charset.StandardCharsets;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ProcessInstanceExpressionContextTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldResolveProcessInstanceKeyInInputMapping() {
    // given
    final var processId = "pi-ctx-input-mapping";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("input-mapping-job")
                            .zeebeInputExpression("camunda.processInstance.key", "piKey"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    RecordingExporter.jobRecords(JobIntent.CREATED).withProcessInstanceKey(pi).await();
    final var job =
        ENGINE.jobs().withType("input-mapping-job").activate().getValue().getJobs().getFirst();
    assertThat(job.getVariables()).containsEntry("piKey", pi);
  }

  @Test
  public void shouldResolveProcessInstanceKeyInOutputMapping() {
    // given
    final var processId = "pi-ctx-output-mapping";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task1",
                    t ->
                        t.zeebeJobType("output-mapping-job-1")
                            .zeebeOutputExpression("camunda.processInstance.key", "piKey"))
                .serviceTask("task2", t -> t.zeebeJobType("output-mapping-job-2"))
                .endEvent()
                .done())
        .deploy();

    final long pi = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(pi).withType("output-mapping-job-1").complete();

    // then
    final var job2 =
        ENGINE.jobs().withType("output-mapping-job-2").activate().getValue().getJobs().getFirst();
    assertThat(job2.getVariables()).containsEntry("piKey", pi);
  }

  @Test
  public void shouldResolveProcessInstanceKeyInSequenceFlowCondition() {
    // given
    final var processId = "pi-ctx-sequence-condition";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .exclusiveGateway("gateway")
                .conditionExpression("camunda.processInstance.key > 0")
                .serviceTask("taken", t -> t.zeebeJobType("taken-job"))
                .endEvent()
                .moveToLastExclusiveGateway()
                .defaultFlow()
                .serviceTask("default", t -> t.zeebeJobType("default-job"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(pi)
                .withElementId("taken")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveProcessInstanceKeyInJobTypeExpression() {
    // given
    final var processId = "pi-ctx-job-type";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task", t -> t.zeebeJobTypeExpression("string(camunda.processInstance.key)"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    assertThat(job.getType()).isEqualTo(String.valueOf(pi));
  }

  @Test
  public void shouldResolveProcessInstanceKeyInJobRetriesExpression() {
    // given
    final var processId = "pi-ctx-job-retries";
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
                                "if camunda.processInstance.key > 0 then 3 else 1"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    assertThat(job.getRetries()).isEqualTo(3);
  }

  @Test
  public void shouldResolveProcessInstanceKeyInCallActivityProcessIdExpression() {
    // given
    final var parentProcessId = "pi-ctx-call-activity";
    final var childProcessId = "child";
    final var parent =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(
                "call",
                c ->
                    c.zeebeProcessIdExpression(
                        "if camunda.processInstance.key > 0 then \"child\" else \"x\""))
            .endEvent()
            .done();
    final var child = Bpmn.createExecutableProcess(childProcessId).startEvent().endEvent().done();
    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parent)
        .withXmlResource("child.bpmn", child)
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(childProcessId)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveProcessInstanceKeyInBusinessRuleTaskDecisionIdExpression() {
    // given
    final var processId = "pi-ctx-brt";
    final var decisionDmn =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                     id="pi-ctx-brt-drg" name="pi-ctx-brt-drg"
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
        .withXmlResource(decisionDmn.getBytes(StandardCharsets.UTF_8), "pi-ctx-brt-decision.dmn")
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .businessRuleTask(
                    "task",
                    t ->
                        t.zeebeCalledDecisionIdExpression(
                                "if camunda.processInstance.key > 0 then \"decision\" else \"x\"")
                            .zeebeResultVariable("result"))
                .endEvent()
                .done())
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.decisionEvaluationRecords(DecisionEvaluationIntent.EVALUATED)
                .withDecisionId("decision")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResolveProcessInstanceKeyInUserTaskAssigneeExpression() {
    // given
    final var processId = "pi-ctx-assignee";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeAssigneeExpression("string(camunda.processInstance.key)")
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    Assertions.assertThat(userTask).hasAssignee(String.valueOf(pi));
  }

  @Test
  public void shouldResolveProcessInstanceKeyInUserTaskCandidateGroupsExpression() {
    // given
    final var processId = "pi-ctx-candidate-groups";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeCandidateGroupsExpression("[string(camunda.processInstance.key)]")
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    Assertions.assertThat(userTask).hasCandidateGroupsList(String.valueOf(pi));
  }

  @Test
  public void shouldResolveProcessInstanceKeyInUserTaskCandidateUsersExpression() {
    // given
    final var processId = "pi-ctx-candidate-users";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeCandidateUsersExpression("[string(camunda.processInstance.key)]")
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    Assertions.assertThat(userTask).hasCandidateUsersList(String.valueOf(pi));
  }

  @Test
  public void shouldResolveProcessInstanceKeyInUserTaskDueDateExpression() {
    // given
    final var processId = "pi-ctx-due-date";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeDueDateExpression(
                    "if camunda.processInstance.key > 0 then \"2040-01-01T00:00Z\" else null")
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    assertThat(userTask.getDueDate()).isNotEmpty();
  }

  @Test
  public void shouldResolveProcessInstanceKeyInUserTaskFollowUpDateExpression() {
    // given
    final var processId = "pi-ctx-follow-up-date";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeFollowUpDateExpression(
                    "if camunda.processInstance.key > 0 then \"2040-01-01T00:00Z\" else null")
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    assertThat(userTask.getFollowUpDate()).isNotEmpty();
  }

  @Test
  public void shouldResolveProcessInstanceKeyInUserTaskPriorityExpression() {
    // given
    final var processId = "pi-ctx-priority";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeTaskPriorityExpression("if camunda.processInstance.key > 0 then 50 else 1")
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(pi)
            .getFirst()
            .getValue();
    Assertions.assertThat(userTask).hasPriority(50);
  }
}

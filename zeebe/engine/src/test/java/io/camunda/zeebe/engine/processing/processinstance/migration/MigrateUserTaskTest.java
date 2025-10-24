/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateUserTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteElementMigratedEventForUserTaskWithJobWorkerImplementation() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .userTask()
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.USER_TASK)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasVersion(1)
        .hasElementId("B");
  }

  @Test
  public void shouldWriteElementMigratedEventForUserTaskWithNativeUserTaskImplementation() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    // method reference doesn't help readability in this builder
    @SuppressWarnings("Convert2MethodRef")
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A", u -> u.zeebeUserTask())
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B", u -> u.zeebeUserTask())
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasVersion(1)
        .describedAs("Expect that element id is left unchanged")
        .hasElementId("B");
  }

  @Test
  public void shouldWriteMigratedEventForUserTask() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withJsonClasspathResource("/form/test-form-1.form")
            .withJsonClasspathResource("/form/test-form-2.form")
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask(
                        "A",
                        u ->
                            u.zeebeUserTask()
                                .zeebeAssigneeExpression("user")
                                .zeebeCandidateUsersExpression("candidates")
                                .zeebeCandidateGroupsExpression("candidates")
                                .zeebeDueDateExpression("now() + duration(due)")
                                .zeebeFollowUpDateExpression("now() + duration(followup)")
                                .zeebeFormId("Form_0w7r08e"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask(
                        "B",
                        u ->
                            u.zeebeUserTask()
                                .zeebeAssigneeExpression("user2")
                                .zeebeCandidateUsersExpression("candidates2")
                                .zeebeCandidateGroupsExpression("candidates2")
                                .zeebeDueDateExpression("now() + duration(due2)")
                                .zeebeFollowUpDateExpression("now() + duration(followup2)")
                                .zeebeFormId("Form_6s1b76p"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.ofEntries(
                    Map.entry("user", "user"),
                    Map.entry("user2", "user2"),
                    Map.entry("candidates", List.of("candidates")),
                    Map.entry("candidates2", List.of("candidates2")),
                    Map.entry("due", "PT2H"),
                    Map.entry("due2", "PT20H"),
                    Map.entry("followup", "PT1H"),
                    Map.entry("followup2", "PT10H")))
            .create();

    // await user task assignment
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasProcessDefinitionVersion(1)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B")
        .describedAs(
            """
                Expect that the user task properties did not change even though they're different \
                in the target process. Re-evaluation of these expression is not enabled for this \
                migration""")
        .hasAssignee(userTask.getAssignee())
        .hasCandidateGroupsList(userTask.getCandidateGroupsList())
        .hasCandidateUsersList(userTask.getCandidateUsersList())
        .hasDueDate(userTask.getDueDate())
        .hasFollowUpDate(userTask.getFollowUpDate())
        .hasFormKey(userTask.getFormKey());
  }

  @Test
  public void shouldWriteMigratedEventForUserTaskWithoutVariables() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask(
                        "A",
                        u -> u.zeebeUserTask().zeebeInputExpression("taskVariable", "taskVariable"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask(
                        "B",
                        u ->
                            u.zeebeUserTask()
                                .zeebeInputExpression("taskVariable2", "taskVariable2"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.ofEntries(
                    Map.entry("taskVariable", "taskVariable"),
                    Map.entry("taskVariable2", "taskVariable2")))
            .create();

    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the variables are unset to avoid exceeding the max record size")
        .hasVariables(Map.of());
  }

  @Test
  public void shouldContinueFlowInTargetProcessForMigratedUserTask() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withJsonClasspathResource("/form/test-form-1.form")
            .withJsonClasspathResource("/form/test-form-2.form")
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask(
                        "A",
                        u ->
                            u.zeebeUserTask()
                                .zeebeAssigneeExpression("user")
                                .zeebeCandidateUsersExpression("candidates")
                                .zeebeCandidateGroupsExpression("candidates")
                                .zeebeDueDateExpression("now() + duration(due)")
                                .zeebeFollowUpDateExpression("now() + duration(followup)")
                                .zeebeFormId("Form_0w7r08e"))
                    .endEvent("source_process_end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask(
                        "B",
                        u ->
                            u.zeebeUserTask()
                                .zeebeAssigneeExpression("user2")
                                .zeebeCandidateUsersExpression("candidates2")
                                .zeebeCandidateGroupsExpression("candidates2")
                                .zeebeDueDateExpression("now() + duration(due2)")
                                .zeebeFollowUpDateExpression("now() + duration(followup2)")
                                .zeebeFormId("Form_6s1b76p"))
                    .endEvent("target_process_end")
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.ofEntries(
                    Map.entry("user", "user"),
                    Map.entry("user2", "user2"),
                    Map.entry("candidates", List.of("candidates")),
                    Map.entry("candidates2", List.of("candidates2")),
                    Map.entry("due", "PT2H"),
                    Map.entry("due2", "PT20H"),
                    Map.entry("followup", "PT1H"),
                    Map.entry("followup2", "PT10H")))
            .create();

    // await user task creation
    final var userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then we can do any operation on the user task again

    // Note that while the user task is migrated, it's properties did not change even though they're
    // different in the target process. Because re-evaluation of these expressions is not yet
    // supported.
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withKey(userTaskKey)
        .withAssignee("user2")
        .assign();
    ENGINE.userTask().ofInstance(processInstanceKey).withKey(userTaskKey).unassign();
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withKey(userTaskKey)
        .withAssignee("user3")
        .claim();

    // and finally complete the user task and continue the process
    ENGINE.userTask().ofInstance(processInstanceKey).withKey(userTaskKey).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_end")
                .findAny())
        .describedAs("Expect that the process instance is continued in the target process")
        .isPresent();
  }

  @Test
  public void shouldContinueListenerBlockingLifecycleTransition() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A", u -> u.zeebeUserTask().zeebeAssignee("frodo"))
                    .zeebeTaskListener(
                        l -> l.eventType(ZeebeTaskListenerEventType.completing).type("completing"))
                    .endEvent("source_process_end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B", u -> u.zeebeUserTask().zeebeAssignee("frodo"))
                    .zeebeTaskListener(
                        l -> l.eventType(ZeebeTaskListenerEventType.completing).type("completing"))
                    .endEvent("target_process_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    final var listenerJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withJobKind(JobKind.TASK_LISTENER)
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.MIGRATED)
                .withRecordKey(listenerJob.getKey())
                .getFirst()
                .getValue())
        .describedAs("Expect that the listener job is migrated to the target process")
        .hasElementId("B")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasProcessDefinitionVersion(1)
        .hasBpmnProcessId(targetProcessId);

    // and when
    ENGINE.job().withKey(listenerJob.getKey()).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_end")
                .findAny())
        .describedAs("Expect that the process instance is continued in the target process")
        .isPresent();
  }

  @Test
  public void shouldMigrateUserTaskWithIncident() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .zeebeFormId("invalidformid")
                    .zeebeUserTask()
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .zeebeUserTask()
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasVersion(1)
        .hasElementId("B");
  }
}

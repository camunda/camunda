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
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
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

  private static final String FORM_ID_1 = "Form_0w7r08e";
  private static final String TEST_FORM_1 = "/form/test-form-1.form";
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

  @Test
  public void shouldMigrateJobBasedUserTaskToZeebeUserTask() {
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
                        t ->
                            t.zeebeAssignee("user")
                                .zeebeCandidateGroups("foo, bar")
                                .zeebeCandidateUsers("oof, rab")
                                .zeebeDueDate("2023-03-02T16:35+02:00")
                                .zeebeFollowUpDate("2023-03-02T15:35+02:00")
                                .zeebeFormKey("test-form-1-key"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .zeebeUserTask()
                    .zeebeAssignee("userA")
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

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.BPMN_ELEMENT)
                .exists())
        .describedAs("Expect that the job is canceled for the job based user task")
        .isTrue();

    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B")
                .getFirst()
                .getValue())
        .describedAs("Expect that a new user task is created for the zeebe user task")
        .hasExternalFormReference("test-form-1-key")
        .hasCandidateGroupsList("foo", "bar")
        .hasCandidateUsersList("oof", "rab")
        .hasDueDate("2023-03-02T16:35+02:00")
        .hasFollowUpDate("2023-03-02T15:35+02:00")
        .hasNoChangedAttributes()
        .hasPriority(50); // default priority

    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B")
                .getFirst()
                .getValue())
        .describedAs("Expect that the user task is assigned")
        .hasExternalFormReference("test-form-1-key")
        .hasCandidateGroupsList("foo", "bar")
        .hasCandidateUsersList("oof", "rab")
        .hasDueDate("2023-03-02T16:35+02:00")
        .hasFollowUpDate("2023-03-02T15:35+02:00")
        .hasAssignee("\"userA\"")
        .hasPriority(50); // default priority
  }

  @Test
  public void shouldMigrateJobBasedUserTaskToZeebeUserTaskWithInternalForm() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withJsonClasspathResource(TEST_FORM_1)
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask(
                        "A",
                        t ->
                            t.zeebeAssignee("user")
                                .zeebeCandidateGroups("foo, bar")
                                .zeebeCandidateUsers("oof, rab")
                                .zeebeDueDate("2023-03-02T16:35+02:00")
                                .zeebeFollowUpDate("2023-03-02T15:35+02:00")
                                .zeebeFormId(FORM_ID_1))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .zeebeUserTask()
                    .zeebeTaskPriority("80")
                    .zeebeAssignee("userA")
                    .endEvent()
                    .done())
            .deploy();

    final FormMetadataValue form = deployment.getValue().getFormMetadata().getFirst();
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

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.BPMN_ELEMENT)
                .exists())
        .describedAs("Expect that the job is canceled for the job based user task")
        .isTrue();

    final UserTaskRecordValue migratedUserTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("B")
            .getFirst()
            .getValue();
    assertThat(migratedUserTask)
        .describedAs("Expect that a new user task is created for the zeebe user task")
        .hasExternalFormReference("")
        .hasFormKey(form.getFormKey())
        .hasCandidateGroupsList("foo", "bar")
        .hasCandidateUsersList("oof", "rab")
        .hasDueDate("2023-03-02T16:35+02:00")
        .hasFollowUpDate("2023-03-02T15:35+02:00")
        .hasNoChangedAttributes()
        .hasPriority(80);

    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B")
                .getFirst()
                .getValue())
        .describedAs("Expect that the user task is assigned")
        .describedAs("External form reference is not set for internal forms")
        .hasExternalFormReference("")
        .describedAs("Form key is set for internal forms")
        .hasFormKey(form.getFormKey())
        .hasCandidateGroupsList("foo", "bar")
        .hasCandidateUsersList("oof", "rab")
        .hasAssignee("\"userA\"")
        .hasDueDate("2023-03-02T16:35+02:00")
        .hasFollowUpDate("2023-03-02T15:35+02:00")
        .hasPriority(80);

    verifyFormOperationsWork(migratedUserTask.getUserTaskKey());
  }

  @Test
  public void shouldMigrateJobBasedUserTaskToZeebeUserTaskWithExternalForm() {
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
                        t ->
                            t.zeebeAssignee("user")
                                .zeebeCandidateGroups("foo, bar")
                                .zeebeCandidateUsers("oof, rab")
                                .zeebeDueDate("2023-03-02T16:35+02:00")
                                .zeebeFollowUpDate("2023-03-02T15:35+02:00")
                                .zeebeFormKey("https://my-form.com"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .zeebeUserTask()
                    .zeebeAssignee("userA")
                    .zeebeTaskPriority("80")
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

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.BPMN_ELEMENT)
                .exists())
        .describedAs("Expect that the job is canceled for the job based user task")
        .isTrue();

    final UserTaskRecordValue migratedUserTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("B")
            .getFirst()
            .getValue();
    assertThat(migratedUserTask)
        .describedAs(
            "Expect that a new user task with the right form reference is created for the zeebe user task")
        .hasExternalFormReference("https://my-form.com")
        .describedAs("Form key is not set for external forms")
        .hasFormKey(-1L)
        .hasCandidateGroupsList("foo", "bar")
        .hasCandidateUsersList("oof", "rab")
        .hasDueDate("2023-03-02T16:35+02:00")
        .hasFollowUpDate("2023-03-02T15:35+02:00")
        .hasNoChangedAttributes()
        .hasPriority(80); // target priority

    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B")
                .getFirst()
                .getValue())
        .describedAs("Expect that the user task is assigned and the external form reference is set")
        .hasExternalFormReference("https://my-form.com")
        .describedAs("Form key is not set for external forms")
        .hasFormKey(-1L)
        .hasCandidateGroupsList("foo", "bar")
        .hasCandidateUsersList("oof", "rab")
        .hasAssignee("\"userA\"")
        .hasDueDate("2023-03-02T16:35+02:00")
        .hasFollowUpDate("2023-03-02T15:35+02:00")
        .hasPriority(80); // target priority

    verifyFormOperationsWork(migratedUserTask.getUserTaskKey());
  }

  @Test
  public void shouldMigrateJobBasedUserTaskToZeebeUserTaskWithEmbeddedFormAndNoTargetForm() {
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
                        t ->
                            t.zeebeAssignee("user")
                                .zeebeCandidateGroups("foo, bar")
                                .zeebeCandidateUsers("oof, rab")
                                .zeebeDueDate("2023-03-02T16:35+02:00")
                                .zeebeFollowUpDate("2023-03-02T15:35+02:00")
                                .zeebeUserTaskForm("{}"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .zeebeUserTask()
                    .zeebeAssignee("userA")
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

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.BPMN_ELEMENT)
                .exists())
        .describedAs("Expect that the job is canceled for the job based user task")
        .isTrue();

    final UserTaskRecordValue migratedUserTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("B")
            .getFirst()
            .getValue();
    assertThat(migratedUserTask)
        .describedAs("Expect that a new user task is created for the zeebe user task")
        .hasExternalFormReference("")
        .hasFormKey(-1)
        .hasCandidateGroupsList("foo", "bar")
        .hasCandidateUsersList("oof", "rab")
        .hasDueDate("2023-03-02T16:35+02:00")
        .hasFollowUpDate("2023-03-02T15:35+02:00")
        .hasNoChangedAttributes()
        .hasPriority(50); // default priority

    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B")
                .getFirst()
                .getValue())
        .describedAs("Expect that the user task is assigned and internal form references are set")
        .hasExternalFormReference("")
        .hasFormKey(-1)
        .hasCandidateGroupsList("foo", "bar")
        .hasCandidateUsersList("oof", "rab")
        .hasDueDate("2023-03-02T16:35+02:00")
        .hasAssignee("\"userA\"")
        .hasFollowUpDate("2023-03-02T15:35+02:00")
        .hasPriority(50); // default priority

    verifyFormOperationsWork(migratedUserTask.getUserTaskKey());
  }

  @Test
  public void shouldMigrateJobBasedUserTaskToZeebeUserTaskAndNotCreateTaskListenerJob() {
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
                        t ->
                            t.zeebeAssignee("user")
                                .zeebeCandidateGroups("foo, bar")
                                .zeebeCandidateUsers("oof, rab")
                                .zeebeDueDate("2023-03-02T16:35+02:00")
                                .zeebeFollowUpDate("2023-03-02T15:35+02:00")
                                .zeebeFormKey("test-form-1-key"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .zeebeUserTask()
                    .zeebeAssignee("userA")
                    .zeebeTaskListener(l -> l.assigning().type("assignment_listener").retries("3"))
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

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.BPMN_ELEMENT)
                .exists())
        .describedAs("Expect that the job is canceled for the job based user task")
        .isTrue();

    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B")
                .getFirst()
                .getValue())
        .describedAs("Expect that a new user task is created for the zeebe user task")
        .hasExternalFormReference("test-form-1-key")
        .hasCandidateGroupsList("foo", "bar")
        .hasCandidateUsersList("oof", "rab")
        .hasDueDate("2023-03-02T16:35+02:00")
        .hasFollowUpDate("2023-03-02T15:35+02:00")
        .hasNoChangedAttributes()
        .hasPriority(50); // default priority

    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNING)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B")
                .getFirst()
                .getValue())
        .describedAs("Expect that the user task is assigned")
        .hasExternalFormReference("test-form-1-key")
        .hasCandidateGroupsList("foo", "bar")
        .hasCandidateUsersList("oof", "rab")
        .hasAssignee("\"userA\"")
        .hasDueDate("2023-03-02T16:35+02:00")
        .hasFollowUpDate("2023-03-02T15:35+02:00")
        .hasPriority(50); // default priority

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == UserTaskIntent.ASSIGNED)
                .jobRecords()
                .withIntent(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B")
                .withJobKind(JobKind.TASK_LISTENER)
                .withType("assignment_listener")
                .exists())
        .isFalse();
  }

  private static void verifyFormOperationsWork(final long userTaskKey) {
    // can be unassigned
    ENGINE.userTask().withKey(userTaskKey).unassign();
    assertThat(RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGN).getFirst().getValue())
        .hasUserTaskKey(userTaskKey)
        .hasAssignee("")
        .describedAs("Can unassign newly created task");
    // can be assigned
    ENGINE.userTask().withKey(userTaskKey).withAssignee("userB").assign();
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGN)
                .filter(
                    t ->
                        t.getValue().getAssignee().equals("userB")
                            && (t.getValue().getUserTaskKey() == userTaskKey))
                .exists())
        .describedAs("Can assign newly created task");
    // can be completed
    ENGINE.userTask().withKey(userTaskKey).complete();
    assertThat(RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED).getFirst().getValue())
        .hasUserTaskKey(userTaskKey)
        .describedAs("Can complete newly created task");
  }
}

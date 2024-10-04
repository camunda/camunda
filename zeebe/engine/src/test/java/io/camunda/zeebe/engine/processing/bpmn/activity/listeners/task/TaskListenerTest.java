/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.task;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class TaskListenerTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String FORM_ID_1 = "Form_0w7r08e";
  private static final String TEST_FORM_1 = "/form/test-form-1.form";
  private static final String USER_TASK_KEY_HEADER_NAME =
      Protocol.RESERVED_HEADER_NAME_PREFIX + "userTaskKey";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCompleteProcessWithCompleteTaskListenerJobs() {
    // given
    final BpmnModelInstance modelInstance =
        createProcessWithCompleteTaskListeners("listener_1", "listener_2", "listener_3");

    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withVariable("foo_var", "bar")
        .withAction("my_custom_action")
        .complete();

    completeJobs(processInstanceKey, "listener_1", "listener_2", "listener_3");

    // then
    assertThatProcessInstanceCompleted(processInstanceKey);
    assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.TASK_LISTENER)
                .withJobListenerEventType(JobListenerEventType.COMPLETE)
                .withIntent(JobIntent.COMPLETED)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(JobRecordValue::getType)
        .containsExactly("listener_1", "listener_2", "listener_3");

    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst())
        .extracting(Record::getValue)
        .satisfies(
            recordValue -> {
              assertThat(recordValue.getAction()).isEqualTo("my_custom_action");
              assertThat(recordValue.getVariables()).containsExactly(entry("foo_var", "bar"));
            });

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSubsequence(
            UserTaskIntent.COMPLETING,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.COMPLETED);
  }

  @Test
  public void shouldRetryTaskListenerWhenListenerJobFailed() {
    // given
    final BpmnModelInstance modelInstance =
        createProcessWithCompleteTaskListeners("listener_1", "listener_2");

    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when: fail listener job with retries
    ENGINE.job().ofInstance(processInstanceKey).withType("listener_1").withRetries(1).fail();
    // complete failed listener job
    ENGINE.job().ofInstance(processInstanceKey).withType("listener_1").complete();
    // complete remaining listeners
    ENGINE.job().ofInstance(processInstanceKey).withType("listener_2").complete();

    // then: assert the listener job was completed after the failure
    assertThat(records().betweenProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETING),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.FAILED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETED));

    assertThatProcessInstanceCompleted(processInstanceKey);
  }

  @Test
  public void shouldCreateIncidentForListenerWhenNoRetriesLeftAndProceedWithRemainingListeners() {
    // given
    final BpmnModelInstance modelInstance =
        createProcessWithCompleteTaskListeners("listener_1", "listener_2", "listener_3");

    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    ENGINE.job().ofInstance(processInstanceKey).withType("listener_1").complete();

    // when: fail 2nd listener job with no retries
    ENGINE.job().ofInstance(processInstanceKey).withType("listener_2").withRetries(0).fail();

    // then: incident created
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(incident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorType(ErrorType.TASK_LISTENER_NO_RETRIES)
        .hasErrorMessage("No more retries left.");

    // resolve incident & complete failed TL job
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("listener_2")
        .withRetries(1)
        .updateRetries();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // complete failed listener job
    ENGINE.job().ofInstance(processInstanceKey).withType("listener_2").complete();
    // complete remaining listeners
    ENGINE.job().ofInstance(processInstanceKey).withType("listener_3").complete();

    // assert the listener job was completed after the failure
    assertThat(records().betweenProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETING),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.FAILED),
            tuple(ValueType.INCIDENT, IncidentIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.RETRIES_UPDATED),
            tuple(ValueType.INCIDENT, IncidentIntent.RESOLVED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.COMPLETE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETE_TASK_LISTENER),
            tuple(ValueType.USER_TASK, UserTaskIntent.COMPLETED));

    assertThatProcessInstanceCompleted(processInstanceKey);
  }

  @Test
  public void shouldEvaluateExpressionsForTaskListeners() {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask(
                "my_user_task",
                t ->
                    t.zeebeUserTask()
                        .zeebeAssignee("foo")
                        .zeebeTaskListener(
                            l ->
                                l.complete()
                                    .typeExpression("\"listener_1_\"+my_var")
                                    .retriesExpression("5+3")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("my_var", "abc").create();

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    completeJobs(processInstanceKey, "listener_1_abc");

    // then
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.TASK_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .onlyEvents()
                .getFirst()
                .getValue())
        .satisfies(
            jobRecordValue -> {
              assertThat(jobRecordValue.getType()).isEqualTo("listener_1_abc");
              assertThat(jobRecordValue.getRetries()).isEqualTo(8);
            });
    assertThatProcessInstanceCompleted(processInstanceKey);
  }

  @Test
  public void shouldAllowSubsequentListenersHaveAccessProducedByThePreviousListener() {
    final BpmnModelInstance modelInstance =
        createProcessWithCompleteTaskListeners("listener_1", "listener_2");

    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("listener_1")
        .withVariable("listener_1_var", "foo")
        .complete();

    // then: `listener_1_var` variable accessible in subsequent TL
    final Optional<JobRecordValue> jobActivated =
        ENGINE.jobs().withType("listener_2").activate().getValue().getJobs().stream()
            .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
            .findFirst();

    assertThat(jobActivated)
        .hasValueSatisfying(
            job -> assertThat(job.getVariables()).contains(entry("listener_1_var", "foo")));
  }

  @Test
  public void shouldRestrictTaskListenerVariablesToOwningElementScope() {
    // given: deploy process with a user task having complete TL and service task following it
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask(
                "my_user_task",
                t ->
                    t.zeebeUserTask()
                        .zeebeAssignee("foo")
                        .zeebeTaskListener(l -> l.complete().type("my_listener")))
            .serviceTask(
                "subsequent_service_task", tb -> tb.zeebeJobType("subsequent_service_task"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when: complete TL job with a variable 'my_listener_var'
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("my_listener")
        .withVariable("my_listener_var", "bar")
        .complete();

    // then: assert the variable 'my_listener_var' is not accessible in the subsequent element
    final var subsequentServiceTaskJob =
        ENGINE.jobs().withType("subsequent_service_task").activate().getValue().getJobs().stream()
            .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
            .findFirst();

    assertThat(subsequentServiceTaskJob)
        .hasValueSatisfying(
            job -> assertThat(job.getVariables()).doesNotContainKey("my_listener_var"));
    ENGINE.job().ofInstance(processInstanceKey).withType("subsequent_service_task").complete();
  }

  @Test
  public void shouldAllowTaskListenerVariablesUseInUserTaskOutputMappings() {
    // given: deploy process with a user task having complete TL and service task following it
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask(
                "my_user_task",
                t ->
                    t.zeebeUserTask()
                        .zeebeAssignee("foo")
                        .zeebeTaskListener(l -> l.complete().type("my_listener"))
                        .zeebeOutput("=my_listener_var+\"_abc\"", "userTaskOutput"))
            .serviceTask(
                "subsequent_service_task", tb -> tb.zeebeJobType("subsequent_service_task"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when: complete TL job with a variable 'my_listener_var'
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("my_listener")
        .withVariable("my_listener_var", "bar")
        .complete();

    // then: assert the variable 'userTaskOutput' is accessible in the subsequent element
    final var subsequentServiceTaskJob =
        ENGINE.jobs().withType("subsequent_service_task").activate().getValue().getJobs().stream()
            .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
            .findFirst();

    assertThat(subsequentServiceTaskJob)
        .hasValueSatisfying(
            job -> assertThat(job.getVariables()).containsEntry("userTaskOutput", "bar_abc"));
    ENGINE.job().ofInstance(processInstanceKey).withType("subsequent_service_task").complete();
  }

  @Test
  public void shouldCreateTaskListenerJobWithUserTaskHeaders() {
    final var form = deployForm(TEST_FORM_1);
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask(
                "my_user_task",
                t ->
                    t.zeebeUserTask()
                        .zeebeAssignee("admin")
                        .zeebeCandidateUsers("user_A, user_B")
                        .zeebeCandidateGroups("group_A, group_C, group_F")
                        .zeebeFormId(FORM_ID_1)
                        .zeebeDueDate("2095-09-18T10:31:10+02:00")
                        .zeebeTaskListener(l -> l.complete().type("my_listener")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    final var userTaskRecordValue = ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // then
    final Optional<JobRecordValue> activatedListenerJob =
        ENGINE.jobs().withType("my_listener").activate().getValue().getJobs().stream()
            .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
            .findFirst();

    assertThat(activatedListenerJob)
        .hasValueSatisfying(
            job ->
                assertThat(job.getCustomHeaders())
                    .containsOnly(
                        entry(
                            Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
                            "[\"group_A\",\"group_C\",\"group_F\"]"),
                        entry(
                            Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME,
                            "[\"user_A\",\"user_B\"]"),
                        entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "admin"),
                        entry(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "2095-09-18T10:31:10+02:00"),
                        entry(
                            Protocol.USER_TASK_FORM_KEY_HEADER_NAME,
                            Objects.toString(form.getFormKey())),
                        entry(
                            USER_TASK_KEY_HEADER_NAME,
                            String.valueOf(userTaskRecordValue.getKey()))));
  }

  @Test
  public void shouldCreateCompleteTaskListenerJobWithRecentUserTaskDataAfterTaskUpdateInHeaders() {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask(
                "my_user_task",
                t ->
                    t.zeebeUserTask()
                        .zeebeAssignee("admin")
                        .zeebeCandidateUsers("user_A, user_B")
                        .zeebeCandidateGroups("group_A, group_C, group_F")
                        .zeebeDueDate("2085-09-21T11:22:33+02:00")
                        .zeebeFollowUpDate("2095-09-21T11:22:33+02:00")
                        .zeebeTaskListener(l -> l.complete().type("my_listener")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var changes =
        new UserTaskRecord()
            .setCandidateGroupsList(List.of("group_J", "group_R"))
            .setCandidateUsersList(List.of("user_T"))
            .setDueDate("2087-09-21T11:22:33+02:00")
            .setFollowUpDate("2097-09-21T11:22:33+02:00");

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).update(changes);
    final var userTaskRecordValue = ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // then
    final Optional<JobRecordValue> activatedListenerJob =
        ENGINE.jobs().withType("my_listener").activate().getValue().getJobs().stream()
            .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
            .findFirst();

    assertThat(activatedListenerJob)
        .hasValueSatisfying(
            job ->
                assertThat(job.getCustomHeaders())
                    .containsOnly(
                        entry(
                            Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
                            "[\"group_J\",\"group_R\"]"),
                        entry(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"user_T\"]"),
                        entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "admin"),
                        entry(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "2087-09-21T11:22:33+02:00"),
                        entry(
                            Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME,
                            "2097-09-21T11:22:33+02:00"),
                        entry(
                            USER_TASK_KEY_HEADER_NAME,
                            String.valueOf(userTaskRecordValue.getKey()))));
  }

  private void assertThatProcessInstanceCompleted(final long processInstanceKey) {
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private BpmnModelInstance createProcessWithCompleteTaskListeners(String... listenerTypes) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .userTask(
            "my_user_task",
            t -> {
              t.zeebeUserTask().zeebeAssignee("foo");
              for (String listenerType : listenerTypes) {
                t.zeebeTaskListener(l -> l.complete().type(listenerType));
              }
            })
        .endEvent()
        .done();
  }

  private void completeJobs(long processInstanceKey, String... jobTypes) {
    for (String jobType : jobTypes) {
      ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();
    }
  }

  private FormMetadataValue deployForm(final String formPath) {
    final var deploymentEvent = ENGINE.deployment().withJsonClasspathResource(formPath).deploy();

    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);

    final var formMetadata = deploymentEvent.getValue().getFormMetadata();
    assertThat(formMetadata).hasSize(1);
    return formMetadata.getFirst();
  }
}

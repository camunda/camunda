/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.USER_TASK_FORM_KEY_BPMN_LOCATION;
import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.USER_TASK_FORM_KEY_CAMUNDA_FORMS_FORMAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class UserTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String FOLLOW_UP_DATE = "2023-02-28T08:16:23+02:00";
  private static final String DUE_DATE = "2023-02-28T09:16:23+02:00";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance process() {
    return process(b -> {});
  }

  private static BpmnModelInstance process(final Consumer<UserTaskBuilder> consumer) {
    final var builder = Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask("task");

    consumer.accept(builder);

    return builder.endEvent().done();
  }

  @Test
  public void shouldActivateUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .limit(3))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsSequence(
            tuple(RecordType.COMMAND, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final Record<ProcessInstanceRecordValue> userTask =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    Assertions.assertThat(userTask.getValue())
        .hasElementId("task")
        .hasBpmnElementType(BpmnElementType.USER_TASK)
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldCreateJob() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<ProcessInstanceRecordValue> taskActivated =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(job.getValue())
        .hasType(Protocol.USER_TASK_JOB_TYPE)
        .hasRetries(1)
        .hasElementInstanceKey(taskActivated.getKey())
        .hasElementId(taskActivated.getValue().getElementId())
        .hasProcessDefinitionKey(taskActivated.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(taskActivated.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(taskActivated.getValue().getVersion());
  }

  @Test
  public void shouldCreateJobWithVariables() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeInputExpression("processVariable", "taskVariable")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("processVariable", "processValue")
            .create();

    // then
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    final Optional<JobRecordValue> jobRecordValue =
        ENGINE
            .jobs()
            .withType(Protocol.USER_TASK_JOB_TYPE)
            .withMaxJobsToActivate(Integer.MAX_VALUE)
            .withTimeout(200)
            .activate()
            .getValue()
            .getJobs()
            .stream()
            .filter(j -> j.getProcessInstanceKey() == processInstanceKey)
            .findFirst();

    assertThat(jobRecordValue)
        .hasValueSatisfying(
            v ->
                assertThat(v.getVariables())
                    .containsEntry("processVariable", "processValue")
                    .containsEntry("taskVariable", "processValue"));
  }

  @Test
  public void shouldCreateJobWithProcessInstanceAndCustomHeaders() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeTaskHeader("a", "b").zeebeTaskHeader("c", "d")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders).hasSize(2).containsEntry("a", "b").containsEntry("c", "d");
  }

  @Test
  public void shouldCreateJobWithFormKeyHeader() {
    // given
    final String formKey = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeUserTaskForm(formKey, "User Task Form")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(1)
        .containsEntry(
            Protocol.USER_TASK_FORM_KEY_HEADER_NAME,
            String.format(
                "%s:%s:%s",
                USER_TASK_FORM_KEY_CAMUNDA_FORMS_FORMAT,
                USER_TASK_FORM_KEY_BPMN_LOCATION,
                formKey));
  }

  @Test
  public void shouldCreateJobWithFormKeyHeaderAndCustomHeaders() {
    // given
    final String formKey = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t ->
                    t.zeebeUserTaskForm(formKey, "User Task Form")
                        .zeebeTaskHeader("a", "b")
                        .zeebeTaskHeader("c", "d")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(3)
        .containsEntry(
            Protocol.USER_TASK_FORM_KEY_HEADER_NAME,
            String.format(
                "%s:%s:%s",
                USER_TASK_FORM_KEY_CAMUNDA_FORMS_FORMAT, USER_TASK_FORM_KEY_BPMN_LOCATION, formKey))
        .containsEntry("a", "b")
        .containsEntry("c", "d");
  }

  @Test
  public void shouldCreateJobWithAssigneeHeader() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeAssignee("alice"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(1)
        .containsEntry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "alice");
  }

  @Test
  public void shouldCreateJobWithEvaluatedAssigneeExpressionHeader() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeAssigneeExpression("user"))).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("user", "alice"))
            .create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(1)
        .containsEntry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "alice");
  }

  @Test
  public void shouldCreateJobAndIgnoreEmptyEvaluatedAssigneeExpressionHeader() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeAssigneeExpression("user"))).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("user", ""))
            .create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders).hasSize(0).doesNotContainKey(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME);
  }

  @Test
  public void shouldCreateJobWithCandidateGroupsHeader() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeCandidateGroups("alice,bob"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(1)
        .containsEntry(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "[\"alice\",\"bob\"]");
  }

  @Test
  public void shouldCreateJobWithEvaluatedCandidateGroupsExpressionHeader() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeCandidateGroupsExpression("users")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{ \"users\": [\"alice\", \"bob\"] }")
            .create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(1)
        .containsEntry(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "[\"alice\",\"bob\"]");
  }

  @Test
  public void shouldCreateJobWithCandidateUsersHeader() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeCandidateUsers("jack,rose"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(1)
        .containsEntry(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"jack\",\"rose\"]");
  }

  @Test
  public void shouldCreateJobWithEvaluatedCandidateUsersExpressionHeader() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeCandidateUsersExpression("users")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{ \"users\": [\"jack\", \"rose\"] }")
            .create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(1)
        .containsEntry(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"jack\",\"rose\"]");
  }

  @Test
  public void shouldCreateJobWithDueDateHeader() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeDueDate(DUE_DATE))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(1)
        .containsEntry(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, DUE_DATE);
  }

  @Test
  public void shouldCreateJobWithEvaluatedDueDateExpressionHeader() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeDueDateExpression("dueDate"))).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("dueDate", DUE_DATE))
            .create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(1)
        .containsEntry(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, DUE_DATE);
  }

  @Test
  public void shouldCreateJobAndIgnoreEmptyDueDateHeader() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeDueDate(""))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders).hasSize(0).doesNotContainKey(Protocol.USER_TASK_DUE_DATE_HEADER_NAME);
  }

  @Test
  public void shouldCreateJobAndIgnoreEmptyEvaluatedDueDateExpressionHeader() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeDueDateExpression("=dueDate")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("dueDate", ""))
            .create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders).hasSize(0).doesNotContainKey(Protocol.USER_TASK_DUE_DATE_HEADER_NAME);
  }

  @Test
  public void shouldCreateJobAndIgnoreNullEvaluatedDueDateExpressionHeader() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeDueDateExpression("=null"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders).hasSize(0).doesNotContainKey(Protocol.USER_TASK_DUE_DATE_HEADER_NAME);
  }

  @Test
  public void shouldCreateJobWithFollowUpDateHeader() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeFollowUpDate(FOLLOW_UP_DATE))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(1)
        .containsEntry(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, FOLLOW_UP_DATE);
  }

  @Test
  public void shouldCreateJobWithEvaluatedFollowUpDateExpressionHeader() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeFollowUpDateExpression("followUpDate")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("followUpDate", FOLLOW_UP_DATE))
            .create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(1)
        .containsEntry(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, FOLLOW_UP_DATE);
  }

  @Test
  public void shouldCreateJobAndIgnoreEmptyFollowUpDateHeader() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeFollowUpDate(""))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(0)
        .doesNotContainKey(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME);
  }

  @Test
  public void shouldCreateJobAndIgnoreEmptyEvaluatedFollowUpDateExpressionHeader() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeFollowUpDateExpression("=followUpDate")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("followUpDate", ""))
            .create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(0)
        .doesNotContainKey(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME);
  }

  @Test
  public void shouldCreateJobAndIgnoreNullEvaluatedFollowUpDateExpressionHeader() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeFollowUpDateExpression("=null")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders)
        .hasSize(0)
        .doesNotContainKey(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME);
  }

  @Test
  public void shouldCompleteUserTask() {
    // given
    ENGINE.deployment().withXmlResource(process()).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType(Protocol.USER_TASK_JOB_TYPE).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldResolveIncidentsWhenTerminating() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeInputExpression("nonexisting_variable", "target")))
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("foo", 10).create();
    assertThat(
            RecordingExporter.incidentRecords().withProcessInstanceKey(processInstanceKey).limit(1))
        .extracting(Record::getIntent)
        .containsExactly(IncidentIntent.CREATED);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));

    assertThat(
            RecordingExporter.incidentRecords().withProcessInstanceKey(processInstanceKey).limit(2))
        .extracting(Record::getIntent)
        .containsExactly(IncidentIntent.CREATED, IncidentIntent.RESOLVED);
  }
}

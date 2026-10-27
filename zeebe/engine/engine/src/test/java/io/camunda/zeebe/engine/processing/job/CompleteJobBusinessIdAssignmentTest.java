/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.processinstance.BusinessIdValidator;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies late Business ID assignment via the {@code CompleteJob} command (ADR 0006, D11/D12) with
 * Business ID uniqueness disabled: the happy path with forward-only propagation and the shared
 * rejection matrix enforced atomically (the job is not completed on a failed assignment, including
 * when the instance already carries any Business ID). Rejection when uniqueness is enabled is
 * covered by {@link CompleteJobBusinessIdAssignmentWhenUniquenessEnabledTest}.
 */
public final class CompleteJobBusinessIdAssignmentTest {

  private static final String PROCESS_ID = "process";
  private static final String ASSIGN_JOB_TYPE = "t-assign";
  private static final String AFTER_JOB_TYPE = "t-after";

  private static final BpmnModelInstance TWO_TASK_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("assign", t -> t.zeebeJobType(ASSIGN_JOB_TYPE))
          .serviceTask("after", t -> t.zeebeJobType(AFTER_JOB_TYPE))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldAssignBusinessIdWhenCompletingJob() {
    // given
    engine.deployment().withXmlResource(TWO_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType(ASSIGN_JOB_TYPE)
        .withBusinessId("biz-1")
        .complete();

    // then: an ASSIGNED event is produced for the process instance
    final var assigned =
        RecordingExporter.processInstanceBusinessIdRecords(ProcessInstanceBusinessIdIntent.ASSIGNED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();
    assertThat(assigned.getBusinessId()).isEqualTo("biz-1");
    assertThat(assigned.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(assigned.getBpmnProcessId()).isEqualTo(PROCESS_ID);
  }

  @Test
  public void shouldPropagateAssignedBusinessIdForwardOnly() {
    // given
    engine.deployment().withXmlResource(TWO_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: the first job is completed with a business id to assign
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType(ASSIGN_JOB_TYPE)
        .withBusinessId("biz-1")
        .complete();

    // then: the job that existed at completion time keeps an empty business id (D8), while the job
    // created afterwards carries the newly assigned one
    assertThat(businessIdOfCompletedJob(processInstanceKey))
        .describedAs("job completed before/at assignment")
        .isEqualTo("");
    assertThat(businessIdOfCreatedJob(processInstanceKey, AFTER_JOB_TYPE))
        .describedAs("job created after assignment")
        .isEqualTo("biz-1");
  }

  @Test
  public void shouldNotWriteAssignedEventWhenCompletingWithoutBusinessId() {
    // given
    engine.deployment().withXmlResource(TWO_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: a regular completion without an assignment, followed by another regular completion used
    // as a barrier
    engine.job().ofInstance(processInstanceKey).withType(ASSIGN_JOB_TYPE).complete();
    engine.job().ofInstance(processInstanceKey).withType(AFTER_JOB_TYPE).complete();

    // then: no ASSIGNED event was ever produced, and the process completed normally
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .withValueType(ValueType.PROCESS_INSTANCE_BUSINESS_ID)
                .asList())
        .isEmpty();
  }

  @Test
  public void shouldRejectCompletionWhenInstanceAlreadyHasIdenticalBusinessId() {
    // given: an instance that already has the business id assigned
    engine.deployment().withXmlResource(TWO_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();

    // when: completing a job re-sending the identical business id
    final var rejection =
        engine
            .job()
            .ofInstance(processInstanceKey)
            .withType(ASSIGN_JOB_TYPE)
            .withBusinessId("biz-1")
            .expectRejection()
            .complete();

    // then: a Business ID is assigned once and never re-assigned, so even an identical value is
    // rejected and the whole completion fails atomically (ADR 0006, D3/D12)
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to assign a business id to process instance with key '%d', but it already has a business id assigned"
                .formatted(processInstanceKey));
  }

  @Test
  public void shouldRejectCompletionWhenInstanceAlreadyHasDifferentBusinessId() {
    // given: an instance that already carries a business id
    engine.deployment().withXmlResource(TWO_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();

    // when: completing a job while requesting a different business id
    final var rejection =
        engine
            .job()
            .ofInstance(processInstanceKey)
            .withType(ASSIGN_JOB_TYPE)
            .withBusinessId("biz-2")
            .expectRejection()
            .complete();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to assign a business id to process instance with key '%d', but it already has a business id assigned"
                .formatted(processInstanceKey));
  }

  @Test
  public void shouldRejectCompletionWhenBusinessIdExceedsMaxLength() {
    // given
    engine.deployment().withXmlResource(TWO_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final String tooLong = "b".repeat(BusinessIdValidator.MAX_BUSINESS_ID_LENGTH + 1);

    // when
    final var rejection =
        engine
            .job()
            .ofInstance(processInstanceKey)
            .withType(ASSIGN_JOB_TYPE)
            .withBusinessId(tooLong)
            .expectRejection()
            .complete();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to assign a business id to process instance with key '%d', but the business id exceeds the max length of %d"
                .formatted(processInstanceKey, BusinessIdValidator.MAX_BUSINESS_ID_LENGTH));
  }

  @Test
  public void shouldRejectCompletionWhenTargetIsChildProcessInstance() {
    // given: a call activity whose child has a job
    final String childProcessId = "childProcess";
    final String childJobType = "t-child";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("childTask", t -> t.zeebeJobType(childJobType))
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .deploy();
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long childJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(childJobType).getFirst().getKey();

    // when: completing the child's job while requesting an assignment
    final var rejection =
        engine
            .job()
            .withKey(childJobKey)
            .withType(childJobType)
            .withBusinessId("biz-1")
            .expectRejection()
            .complete();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("a business id can only be assigned to root process instances");
  }

  @Test
  public void shouldNotCompleteJobWhenAssignmentIsRejected() {
    // given
    engine.deployment().withXmlResource(TWO_TASK_PROCESS).deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final String tooLong = "b".repeat(BusinessIdValidator.MAX_BUSINESS_ID_LENGTH + 1);

    // when: a completion with an invalid assignment is rejected
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType(ASSIGN_JOB_TYPE)
        .withBusinessId(tooLong)
        .expectRejection()
        .complete();

    // then: the job was not completed (atomicity, D12) and can still be completed normally; the
    // process then finishes and no business id was ever assigned
    engine.job().ofInstance(processInstanceKey).withType(ASSIGN_JOB_TYPE).complete();
    engine.job().ofInstance(processInstanceKey).withType(AFTER_JOB_TYPE).complete();
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .withValueType(ValueType.PROCESS_INSTANCE_BUSINESS_ID)
                .asList())
        .isEmpty();
  }

  private String businessIdOfCompletedJob(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(ASSIGN_JOB_TYPE)
        .getFirst()
        .getValue()
        .getBusinessId();
  }

  private String businessIdOfCreatedJob(final long processInstanceKey, final String jobType) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(jobType)
        .getFirst()
        .getValue()
        .getBusinessId();
  }
}

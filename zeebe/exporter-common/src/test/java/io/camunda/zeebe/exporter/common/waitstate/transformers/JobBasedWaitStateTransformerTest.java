/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate.transformers;

import static io.camunda.zeebe.exporter.common.waitstate.WaitStateConfigs.JOB_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class JobBasedWaitStateTransformerTest {

  /** Parks the job until a secret it references is resolved. */
  private static final Set<JobIntent> SETS_THE_SECRET_WAIT_MARK =
      Set.of(JobIntent.SECRET_RESOLUTION_PARKED);

  /** Moves a parked job out of the secret wait, so clearing the mark is right. */
  private static final Set<JobIntent> ENDS_THE_SECRET_WAIT =
      Set.of(JobIntent.SECRET_RESOLUTION_RESUMED, JobIntent.SUSPENDED);

  /** Requires an activated job, which a parked job is not. */
  private static final Set<JobIntent> NEVER_ON_A_PARKED_JOB = Set.of(JobIntent.FAILED);

  /**
   * Can occur on a parked job without ending the secret wait. The update clears the mark, and the
   * engine appends a park event right after it for a job that is still parked, which sets it again.
   */
  private static final Set<JobIntent> FOLLOWED_BY_A_PARK_ON_A_PARKED_JOB =
      Set.of(JobIntent.RETRIES_UPDATED, JobIntent.MIGRATED);

  private final ProtocolFactory factory = new ProtocolFactory();
  private final JobBasedWaitStateTransformer transformer = new JobBasedWaitStateTransformer();

  @Test
  void shouldExtractDetailsFromJobCreatedRecord() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("payment-service")
            .withJobKind(JobKind.BPMN_ELEMENT)
            .withRetries(3)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("task-payment")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();

    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withKey(999L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(JobIntent.CREATED)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then — identity fields from WaitStateRelated
    assertThat(entry.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entry.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entry.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entry.getElementId()).isEqualTo("task-payment");
    assertThat(entry.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(entry.getPartitionId()).isEqualTo(record.getPartitionId());

    // then — classification set by config and extract
    assertThat(entry.getWaitStateType()).isEqualTo(WaitStateType.JOB);
    assertThat(entry.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);

    // then — job-specific details
    assertThat(entry.getDetails()).isInstanceOf(JobWaitStateDetails.class);
    final var details = (JobWaitStateDetails) entry.getDetails();
    assertThat(details.jobKey()).isEqualTo(999L);
    assertThat(details.jobType()).isEqualTo("payment-service");
    assertThat(details.jobKind()).isEqualTo(JobKind.BPMN_ELEMENT);
    assertThat(details.listenerEventType()).isNull();
    assertThat(details.retries()).isEqualTo(3);
    assertThat(details.waitingForSecretResolution()).isFalse();
  }

  @Test
  void shouldSetWaitingForSecretResolutionOnParkedIntent() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("secret-consumer")
            .withJobKind(JobKind.BPMN_ELEMENT)
            .withRetries(3)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("task-secret")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withKey(777L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(JobIntent.SECRET_RESOLUTION_PARKED)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then
    final var details = (JobWaitStateDetails) entry.getDetails();
    assertThat(details.waitingForSecretResolution()).isTrue();
    assertThat(details.jobType()).isEqualTo("secret-consumer");
  }

  @Test
  void shouldDecideWhatEveryUpdateIntentMeansForTheSecretWaitMark() {
    // given - the secret-wait mark is derived from each record's intent alone: a park sets it and
    // every other update intent clears it, so each one needs a decision on what it means for a job
    // parked in WAITING_FOR_SECRET_RESOLUTION
    final Set<Intent> classified = new HashSet<>();
    classified.addAll(SETS_THE_SECRET_WAIT_MARK);
    classified.addAll(ENDS_THE_SECRET_WAIT);
    classified.addAll(NEVER_ON_A_PARKED_JOB);
    classified.addAll(FOLLOWED_BY_A_PARK_ON_A_PARKED_JOB);

    // when / then
    assertThat(JOB_CONFIG.updateIntents())
        .describedAs(
            "a new job wait-state update intent clears the secret-wait mark; classify it by whether"
                + " it can occur while the job is WAITING_FOR_SECRET_RESOLUTION, and if it can,"
                + " have the engine park the job again right after it")
        .containsExactlyInAnyOrderElementsOf(classified);
  }

  @ParameterizedTest
  @MethodSource("updateIntentsOtherThanThePark")
  void shouldClearWaitingForSecretResolutionOnOtherUpdateIntents(final JobIntent intent) {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("secret-consumer")
            .withJobKind(JobKind.BPMN_ELEMENT)
            .withRetries(3)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withKey(777L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(intent)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then
    final var details = (JobWaitStateDetails) entry.getDetails();
    assertThat(details.waitingForSecretResolution()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldClearWaitingForSecretResolutionWhenAParkedJobIsSuspended() {
    // given - suspending a process instance moves its secret-parked jobs out of the secret wait
    // without a SECRET_RESOLUTION_RESUMED event, and resuming it makes them activatable again
    final Record<JobRecordValue> record =
        (Record<JobRecordValue>)
            (Record<?>)
                factory.generateRecord(
                    ValueType.JOB,
                    r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.SUSPENDED));

    // when
    final var entry = transformer.transform(record);

    // then - the suspension rewrites the details, so the job stops reporting a secret wait
    assertThat(transformer.triggersUpdate(record)).isTrue();
    assertThat(((JobWaitStateDetails) entry.getDetails()).waitingForSecretResolution()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = JobIntent.class,
      names = {"SECRET_RESOLUTION_PARKED", "SECRET_RESOLUTION_RESUMED"})
  @SuppressWarnings("unchecked")
  void shouldTriggerUpdateForSecretResolutionIntents(final JobIntent intent) {
    // given
    final Record<JobRecordValue> record =
        (Record<JobRecordValue>)
            (Record<?>)
                factory.generateRecord(
                    ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(intent));

    // when / then
    assertThat(transformer.triggersUpdate(record)).isTrue();
    assertThat(transformer.triggersAdd(record)).isFalse();
    assertThat(transformer.triggersRemoval(record)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldSupportJobCreatedEvent() {
    // given
    final Record<JobRecordValue> record =
        (Record<JobRecordValue>)
            (Record<?>)
                factory.generateRecord(
                    ValueType.JOB,
                    r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.CREATED));

    // when / then
    assertThat(transformer.supports(record)).isTrue();
    assertThat(transformer.triggersAdd(record)).isTrue();
    assertThat(transformer.triggersRemoval(record)).isFalse();
  }

  @Test
  void shouldRetainListenerEventTypeForExecutionListenerJob() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("exec-listener")
            .withJobKind(JobKind.EXECUTION_LISTENER)
            .withJobListenerEventType(JobListenerEventType.START)
            .withRetries(3)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("exec-el")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();

    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withKey(999L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(JobIntent.CREATED)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then
    final var details = (JobWaitStateDetails) entry.getDetails();
    assertThat(details.jobKind()).isEqualTo(JobKind.EXECUTION_LISTENER);
    assertThat(details.listenerEventType()).isEqualTo(JobListenerEventType.START);
  }

  @Test
  void shouldRetainListenerEventTypeForTaskListenerJob() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("task-listener")
            .withJobKind(JobKind.TASK_LISTENER)
            .withJobListenerEventType(JobListenerEventType.CREATING)
            .withRetries(3)
            .withElementType(BpmnElementType.USER_TASK)
            .withElementId("user-task-tl")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();

    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withKey(999L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(JobIntent.CREATED)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then
    final var details = (JobWaitStateDetails) entry.getDetails();
    assertThat(details.jobKind()).isEqualTo(JobKind.TASK_LISTENER);
    assertThat(details.listenerEventType()).isEqualTo(JobListenerEventType.CREATING);
  }

  @Test
  void shouldEmitNullListenerEventTypeForAdHocSubProcess() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("ahsp-job")
            .withJobKind(JobKind.AD_HOC_SUB_PROCESS)
            .withJobListenerEventType(JobListenerEventType.UNSPECIFIED)
            .withRetries(3)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .withElementId("ahsp")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();

    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withKey(999L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(JobIntent.CREATED)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then
    final var details = (JobWaitStateDetails) entry.getDetails();
    assertThat(details.jobKind()).isEqualTo(JobKind.AD_HOC_SUB_PROCESS);
    assertThat(details.listenerEventType()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldTriggerRemovalOnJobCompletedAndCanceled() {
    // given
    final Record<JobRecordValue> completed =
        (Record<JobRecordValue>)
            (Record<?>)
                factory.generateRecord(
                    ValueType.JOB,
                    r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.COMPLETED));
    final Record<JobRecordValue> canceled =
        (Record<JobRecordValue>)
            (Record<?>)
                factory.generateRecord(
                    ValueType.JOB,
                    r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.CANCELED));

    // when / then
    assertThat(transformer.triggersRemoval(completed)).isTrue();
    assertThat(transformer.triggersRemoval(canceled)).isTrue();
    assertThat(transformer.triggersAdd(completed)).isFalse();
    assertThat(transformer.triggersAdd(canceled)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = JobIntent.class,
      names = {"FAILED", "RETRIES_UPDATED"})
  @SuppressWarnings("unchecked")
  void shouldTriggerUpdateForSentinelRiskIntents(final JobIntent intent) {
    // given
    final Record<JobRecordValue> record =
        (Record<JobRecordValue>)
            (Record<?>)
                factory.generateRecord(
                    ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(intent));

    // when / then
    assertThat(transformer.triggersUpdate(record)).isTrue();
    assertThat(transformer.triggersAdd(record)).isFalse();
    assertThat(transformer.triggersRemoval(record)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = JobIntent.class,
      names = {"FAILED", "RETRIES_UPDATED"})
  @SuppressWarnings("unchecked")
  void shouldClearElementIdForSentinelRiskIntents(final JobIntent intent) {
    // given
    final Record<JobRecordValue> record =
        (Record<JobRecordValue>)
            (Record<?>)
                factory.generateRecord(
                    ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(intent));

    // when
    final var entry = transformer.transform(record);

    // then — elementId is null so update handlers preserve the stored value
    assertThat(entry.getElementId()).isNull();
  }

  @Test
  void shouldExtractRemainingRetriesFromJobFailedRecord() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("retry-service")
            .withJobKind(JobKind.BPMN_ELEMENT)
            .withJobListenerEventType(JobListenerEventType.UNSPECIFIED)
            .withRetries(1)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("retry-task")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();

    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withKey(888L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(JobIntent.FAILED)
                    .withValue(value));

    // when
    final var entry = transformer.transform(record);

    // then
    assertThat(entry.getDetails()).isInstanceOf(JobWaitStateDetails.class);
    final var details = (JobWaitStateDetails) entry.getDetails();
    assertThat(details.retries()).isEqualTo(1);
  }

  private static Stream<Intent> updateIntentsOtherThanThePark() {
    return JOB_CONFIG.updateIntents().stream()
        .filter(intent -> intent != JobIntent.SECRET_RESOLUTION_PARKED);
  }
}

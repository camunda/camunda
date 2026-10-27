/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class JobBasedWaitStateTransformerTest {

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
}

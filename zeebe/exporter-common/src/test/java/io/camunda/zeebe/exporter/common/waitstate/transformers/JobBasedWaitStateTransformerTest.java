/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate.transformers;

import static io.camunda.zeebe.exporter.common.waitstate.transformers.JobBasedWaitStateTransformer.DETAIL_JOB_KEY;
import static io.camunda.zeebe.exporter.common.waitstate.transformers.JobBasedWaitStateTransformer.DETAIL_JOB_KIND;
import static io.camunda.zeebe.exporter.common.waitstate.transformers.JobBasedWaitStateTransformer.DETAIL_JOB_TYPE;
import static io.camunda.zeebe.exporter.common.waitstate.transformers.JobBasedWaitStateTransformer.DETAIL_LISTENER_EVENT_TYPE;
import static io.camunda.zeebe.exporter.common.waitstate.transformers.JobBasedWaitStateTransformer.DETAIL_RETRIES;
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
            .withJobListenerEventType(JobListenerEventType.UNSPECIFIED)
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
    assertThat(entry.getDetails())
        .containsEntry(DETAIL_JOB_KEY, 999L)
        .containsEntry(DETAIL_JOB_TYPE, "payment-service")
        .containsEntry(DETAIL_JOB_KIND, JobKind.BPMN_ELEMENT.name())
        .containsEntry(DETAIL_LISTENER_EVENT_TYPE, JobListenerEventType.UNSPECIFIED.name())
        .containsEntry(DETAIL_RETRIES, 3);
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
}

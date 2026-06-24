/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class WaitStateTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  private final WaitStateTransformerConfig config =
      WaitStateTransformerConfig.of(ValueType.JOB)
          .withAddIntents(JobIntent.CREATED)
          .withRemoveIntents(JobIntent.COMPLETED);

  private final WaitStateTransformer<JobRecordValue> transformer =
      new WaitStateTransformer<>() {
        @Override
        public WaitStateTransformerConfig config() {
          return config;
        }

        @Override
        public void extract(final Record<JobRecordValue> record, final WaitStateEntry entry) {
          entry
              .setRootProcessInstanceKey(1L)
              .setProcessInstanceKey(2L)
              .setElementInstanceKey(record.getKey())
              .setElementId("job-element")
              .setElementType(BpmnElementType.SERVICE_TASK)
              .setWaitStateType(WaitStateType.JOB)
              .setDetails(null)
              .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
              .setPartitionId(record.getPartitionId());
        }
      };

  @Test
  void shouldDelegateSupportsToConfig() {
    // given
    final Record<JobRecordValue> supported =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.CREATED));
    final Record<JobRecordValue> unsupported =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.FAILED));

    // when / then
    assertThat(transformer.triggersAdd(supported)).isTrue();
    assertThat(transformer.triggersRemoval(supported)).isFalse();
    assertThat(transformer.triggersAdd(unsupported)).isFalse();
    assertThat(transformer.triggersRemoval(unsupported)).isFalse();
  }

  @Test
  void shouldDelegateTriggersAddToConfig() {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.CREATED));

    // when / then
    assertThat(transformer.triggersAdd(record)).isTrue();
    assertThat(transformer.triggersRemoval(record)).isFalse();
  }

  @Test
  void shouldDelegateTriggersRemovalToConfig() {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.COMPLETED));

    // when / then
    assertThat(transformer.triggersRemoval(record)).isTrue();
    assertThat(transformer.triggersAdd(record)).isFalse();
  }

  @Test
  void shouldExtractEntryFromRecord() {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.CREATED));

    // when
    final var entry = new WaitStateEntry();
    transformer.extract(record, entry);

    // then
    assertThat(entry).isNotNull();
    assertThat(entry.getElementInstanceKey()).isEqualTo(record.getKey());
    assertThat(entry.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
    assertThat(entry.getWaitStateType()).isEqualTo(WaitStateType.JOB);
    assertThat(entry.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(entry.getPartitionId()).isEqualTo(record.getPartitionId());
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateElementType;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WaitStateTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  private final WaitStateTransformerConfig config =
      WaitStateTransformerConfig.of(ValueType.JOB)
          .withAddIntents(JobIntent.CREATED)
          .withRemoveIntents(JobIntent.COMPLETED);

  private final WaitStateTransformer<RecordValue> transformer =
      new WaitStateTransformer<>() {
        @Override
        public WaitStateTransformerConfig config() {
          return config;
        }

        @Override
        public WaitStateEntry extract(final Record<RecordValue> record) {
          return new WaitStateEntry(
              1L,
              2L,
              record.getKey(),
              "job-element",
              WaitStateElementType.SERVICE_TASK,
              WaitStateType.JOB,
              Map.of(),
              TenantOwned.DEFAULT_TENANT_IDENTIFIER,
              record.getPartitionId());
        }
      };

  @Test
  void shouldDelegateSupportsToConfig() {
    // given
    final var supported =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.CREATED));
    final var unsupported =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.FAILED));

    // when / then
    assertThat(transformer.supports(supported)).isTrue();
    assertThat(transformer.supports(unsupported)).isFalse();
  }

  @Test
  void shouldDelegateTriggersAddToConfig() {
    // given
    final var record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.CREATED));

    // when / then
    assertThat(transformer.triggersAdd(record)).isTrue();
    assertThat(transformer.triggersRemoval(record)).isFalse();
  }

  @Test
  void shouldDelegateTriggersRemovalToConfig() {
    // given
    final var record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.COMPLETED));

    // when / then
    assertThat(transformer.triggersRemoval(record)).isTrue();
    assertThat(transformer.triggersAdd(record)).isFalse();
  }

  @Test
  void shouldExtractEntryFromRecord() {
    // given
    final var record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.CREATED));

    // when
    final var entry = transformer.extract(record);

    // then
    assertThat(entry).isNotNull();
    assertThat(entry.elementInstanceKey()).isEqualTo(record.getKey());
    assertThat(entry.elementType()).isEqualTo(WaitStateElementType.SERVICE_TASK);
    assertThat(entry.waitStateType()).isEqualTo(WaitStateType.JOB);
    assertThat(entry.tenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(entry.partitionId()).isEqualTo(record.getPartitionId());
  }
}

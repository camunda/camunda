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
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class WaitStateTransformerConfigTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Test
  void shouldSupportEventRecordWithAddIntent() {
    // given
    final var config =
        WaitStateTransformerConfig.of(ValueType.JOB).withAddIntents(JobIntent.CREATED);
    final var record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.CREATED));

    // when / then
    assertThat(config.triggersAdd(record)).isTrue();
    assertThat(config.triggersRemoval(record)).isFalse();
  }

  @Test
  void shouldSupportEventRecordWithRemoveIntent() {
    // given
    final var config =
        WaitStateTransformerConfig.of(ValueType.JOB).withRemoveIntents(JobIntent.COMPLETED);
    final var record =
        factory.generateRecord(
            ValueType.JOB, r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.COMPLETED));

    // when / then
    assertThat(config.triggersAdd(record)).isFalse();
    assertThat(config.triggersRemoval(record)).isTrue();
  }

  @Test
  void shouldNotSupportCommandRecord() {
    // given
    final var config =
        WaitStateTransformerConfig.of(ValueType.JOB).withAddIntents(JobIntent.CREATED);
    final var record =
        factory.generateRecord(
            ValueType.JOB,
            r -> r.withRecordType(RecordType.COMMAND).withIntent(JobIntent.COMPLETE));

    // when / then
    assertThat(config.triggersAdd(record)).isFalse();
    assertThat(config.triggersRemoval(record)).isFalse();
  }

  @Test
  void shouldNotSupportUnregisteredIntent() {
    // given
    final var config =
        WaitStateTransformerConfig.of(ValueType.JOB).withAddIntents(JobIntent.CREATED);
    final var record =
        factory.generateRecord(
            ValueType.JOB,
            r -> r.withRecordType(RecordType.EVENT).withIntent(JobIntent.RECURRED_AFTER_BACKOFF));

    // when / then
    assertThat(config.triggersAdd(record)).isFalse();
    assertThat(config.triggersRemoval(record)).isFalse();
  }

  @Test
  void shouldStartWithEmptyIntentSets() {
    // given / when
    final var config = WaitStateTransformerConfig.of(ValueType.JOB);

    // then
    assertThat(config.addIntents()).isEmpty();
    assertThat(config.removeIntents()).isEmpty();
  }

  @Test
  void shouldPreserveExistingIntentsWhenAddingNew() {
    // given
    final var config =
        WaitStateTransformerConfig.of(ValueType.JOB)
            .withAddIntents(JobIntent.CREATED)
            .withRemoveIntents(JobIntent.COMPLETED);

    // when / then
    assertThat(config.addIntents()).containsExactly(JobIntent.CREATED);
    assertThat(config.removeIntents()).containsExactly(JobIntent.COMPLETED);
    assertThat(config.valueType()).isEqualTo(ValueType.JOB);
  }

  @Test
  void shouldUseDefinedWaitStateType() {
    // given
    final var config =
        WaitStateTransformerConfig.of(ValueType.JOB).withWaitStateType(WaitStateType.JOB);

    // when / then
    assertThat(config.waitStateType()).isEqualTo(WaitStateType.JOB);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AnalyticsRecordFilterTest {

  private static final int TEST_PARTITION_ID = 1;
  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  private final AnalyticsRecordFilter filter =
      new AnalyticsRecordFilter(
          Set.of(ValueType.PROCESS_INSTANCE_CREATION, ValueType.PROCESS_INSTANCE),
          Set.of(ProcessInstanceCreationIntent.CREATED, ProcessInstanceIntent.ELEMENT_ACTIVATED),
          TEST_PARTITION_ID);

  @Test
  void shouldAcceptEventRecordType() {
    assertThat(filter.acceptType(RecordType.EVENT)).isTrue();
  }

  @Test
  void shouldRejectCommandRecordType() {
    assertThat(filter.acceptType(RecordType.COMMAND)).isFalse();
  }

  @Test
  void shouldRejectCommandRejectionRecordType() {
    assertThat(filter.acceptType(RecordType.COMMAND_REJECTION)).isFalse();
  }

  @Test
  void shouldAcceptAllRegisteredValueTypes() {
    assertThat(filter.acceptValue(ValueType.PROCESS_INSTANCE_CREATION)).isTrue();
    assertThat(filter.acceptValue(ValueType.PROCESS_INSTANCE)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ValueType.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"PROCESS_INSTANCE_CREATION", "PROCESS_INSTANCE"})
  void shouldRejectUnregisteredValueType(final ValueType type) {
    assertThat(filter.acceptValue(type)).isFalse();
  }

  @Test
  void shouldAcceptAllRegisteredIntents() {
    assertThat(filter.acceptIntent(ProcessInstanceCreationIntent.CREATED)).isTrue();
    assertThat(filter.acceptIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)).isTrue();
  }

  @Test
  void shouldRejectUnregisteredIntent() {
    assertThat(filter.acceptIntent(ProcessInstanceCreationIntent.CREATE)).isFalse();
  }

  @Test
  void shouldAcceptRecordFromLocalPartition() {
    // given
    final var record =
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE_CREATION,
            r ->
                r.withKey(Protocol.encodePartitionId(TEST_PARTITION_ID, 1))
                    .withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceCreationIntent.CREATED));

    // when / then
    assertThat(filter.acceptRecord(record)).isTrue();
  }

  @Test
  void shouldRejectRecordFromRemotePartition() {
    // given — key encodes partition 2, but exporter runs on partition 1
    final int remotePartition = 2;
    final var record =
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE_CREATION,
            r ->
                r.withKey(Protocol.encodePartitionId(remotePartition, 1))
                    .withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceCreationIntent.CREATED));

    // when / then
    assertThat(filter.acceptRecord(record)).isFalse();
  }
}

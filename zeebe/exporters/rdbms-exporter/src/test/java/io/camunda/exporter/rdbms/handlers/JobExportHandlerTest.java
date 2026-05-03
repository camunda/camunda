/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.db.rdbms.write.service.JobWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobExportHandlerTest {

  private static final Set<JobIntent> TEST_EXPORTABLE_INTENTS =
      EnumSet.of(
          JobIntent.CREATED,
          JobIntent.COMPLETED,
          JobIntent.TIMED_OUT,
          JobIntent.FAILED,
          JobIntent.RETRIES_UPDATED,
          JobIntent.CANCELED,
          JobIntent.ERROR_THROWN,
          JobIntent.MIGRATED);
  private final ProtocolFactory factory = new ProtocolFactory();
  @Mock private JobWriter jobWriter;
  @Captor private ArgumentCaptor<JobDbModel> jobDbModelCaptor;
  private JobExportHandler handler;

  private static Stream<JobIntent> exportableIntents() {
    return TEST_EXPORTABLE_INTENTS.stream();
  }

  private static Stream<JobIntent> nonExportableIntents() {
    return Stream.of(JobIntent.values()).filter(Predicate.not(TEST_EXPORTABLE_INTENTS::contains));
  }

  @BeforeEach
  void setUp() {
    handler = new JobExportHandler(jobWriter);
  }

  @ParameterizedTest(name = "Should export record with intent: {0}")
  @MethodSource("exportableIntents")
  void shouldExportRecord(final JobIntent intent) {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(ValueType.JOB, r -> r.withIntent(intent));

    // when / then
    assertThat(handler.canExport(record)).isTrue();
  }

  @ParameterizedTest(name = "Should not export record with unsupported intent: {0}")
  @MethodSource("nonExportableIntents")
  void shouldNotExportRecord(final JobIntent intent) {
    // given
    final Record<JobRecordValue> record =
        factory.generateRecord(ValueType.JOB, r -> r.withIntent(intent));

    // when / then
    assertThat(handler.canExport(record)).isFalse();
  }

  @Test
  void shouldMapProtocolDefaultPriorityAsZero() {
    // given
    final var recordValue =
        ImmutableJobRecordValue.builder().withJobKind(JobKind.BPMN_ELEMENT).build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CREATED)
                    .withValueType(ValueType.JOB)
                    .withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(jobWriter).create(jobDbModelCaptor.capture());
    assertThat(jobDbModelCaptor.getValue().priority()).isZero();
  }

  @Test
  void shouldMapPriorityOnCreate() {
    // given
    final var recordValue =
        ImmutableJobRecordValue.builder()
            .withJobKind(JobKind.BPMN_ELEMENT)
            .withPriority(42)
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CREATED)
                    .withValueType(ValueType.JOB)
                    .withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(jobWriter).create(jobDbModelCaptor.capture());
    assertThat(jobDbModelCaptor.getValue().priority()).isEqualTo(42);
  }

  @Test
  void shouldMapPriorityOnUpdate() {
    // given
    final var recordValue =
        ImmutableJobRecordValue.builder()
            .withJobKind(JobKind.BPMN_ELEMENT)
            .withPriority(99)
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.RETRIES_UPDATED)
                    .withValueType(ValueType.JOB)
                    .withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(jobWriter).update(jobDbModelCaptor.capture());
    assertThat(jobDbModelCaptor.getValue().priority()).isEqualTo(99);
  }
}

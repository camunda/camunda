/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MetricsExporterTest {

  @Test
  void shouldObserveJobLifetime() {
    // given
    final var metrics = new ExecutionLatencyMetrics();
    final var exporter = new MetricsExporter(metrics);
    exporter.open(new ExporterTestController());
    assertThat(metrics.getJobLifeTime().collect())
        .flatMap(x -> x.samples)
        .describedAs("Expected no metrics to be recorded at start of test")
        .isEmpty();

    // when
    exporter.export(
        ImmutableRecord.builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.JOB)
            .withIntent(JobIntent.CREATED)
            .withTimestamp(1651505728460L)
            .withKey(Protocol.encodePartitionId(1, 1))
            .build());

    // pass a job batch activated to simulate the full lifetime
    final var jobBatch = new JobBatchRecord();
    jobBatch.jobKeys().add().setValue(Protocol.encodePartitionId(1, 1));
    exporter.export(
        ImmutableRecord.builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.JOB_BATCH)
            .withIntent(JobBatchIntent.ACTIVATED)
            .withTimestamp(1651505728465L)
            .withKey(-1)
            .withValue(jobBatch)
            .build());
    exporter.export(
        ImmutableRecord.builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.JOB)
            .withIntent(JobIntent.COMPLETED)
            .withTimestamp(1651505729571L)
            .withKey(Protocol.encodePartitionId(1, 1))
            .build());

    // then
    assertThat(metrics.getJobLifeTime().collect())
        .flatMap(x -> x.samples)
        .filteredOn(s -> s.name.equals("zeebe_job_life_time_count"))
        .map(s -> s.value)
        .describedAs("Expected exactly 1 observed job_life_time sample counted")
        .containsExactly(1d);
    assertThat(metrics.getJobLifeTime().collect())
        .flatMap(x -> x.samples)
        .filteredOn(s -> s.name.equals("zeebe_job_life_time_bucket"))
        .filteredOn(s -> s.value == 1)
        .map(s -> s.labelValues)
        .describedAs("Expected exactly 1 observed job_life_time sample within bounds")
        .contains(List.of("0", "2.5"));
  }

  @Test
  void shouldCleanupProcessInstancesWithSameStartTime() {
    // given
    final var processCache = new TtlKeyCache();
    final var exporter =
        new MetricsExporter(new ExecutionLatencyMetrics(), processCache, new TtlKeyCache());
    final var controller = new ExporterTestController();
    exporter.open(controller);
    exporter.export(
        ImmutableRecord.<ProcessInstanceRecord>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.PROCESS_INSTANCE)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withTimestamp(1651505728460L)
            .withKey(Protocol.encodePartitionId(1, 1))
            .withValue(new ProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS))
            .build());
    exporter.export(
        ImmutableRecord.<ProcessInstanceRecord>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.PROCESS_INSTANCE)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withTimestamp(1651505728460L)
            .withKey(Protocol.encodePartitionId(1, 2))
            .withValue(new ProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS))
            .build());

    // when
    controller.runScheduledTasks(Duration.ofHours(1));

    // then
    assertThat(processCache.isEmpty()).isTrue();
  }

  @Test
  void shouldCleanupJobWithSameStartTime() {
    // given
    final var jobCache = new TtlKeyCache();
    final var exporter =
        new MetricsExporter(new ExecutionLatencyMetrics(), new TtlKeyCache(), jobCache);
    final var controller = new ExporterTestController();
    exporter.open(controller);
    exporter.export(
        ImmutableRecord.builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.JOB)
            .withIntent(JobIntent.CREATED)
            .withTimestamp(1651505729571L)
            .withKey(Protocol.encodePartitionId(1, 1))
            .build());
    exporter.export(
        ImmutableRecord.builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(ValueType.JOB)
            .withIntent(JobIntent.CREATED)
            .withTimestamp(1651505729571L)
            .withKey(Protocol.encodePartitionId(1, 2))
            .build());

    // when
    controller.runScheduledTasks(Duration.ofHours(1));

    // then
    assertThat(jobCache.isEmpty()).isTrue();
  }

  @Nested
  @DisplayName("MetricsExporter should configure a Filter")
  class FilterTest {

    static Stream<TypeCombination> acceptedCombinations() {
      return Stream.of(
          new TypeCombination(RecordType.EVENT, ValueType.JOB),
          new TypeCombination(RecordType.EVENT, ValueType.JOB_BATCH),
          new TypeCombination(RecordType.EVENT, ValueType.PROCESS_INSTANCE));
    }

    /** Returns the inverse of {@link #acceptedCombinations()}. */
    static Stream<TypeCombination> rejectedCombinations() {
      return allCombinations().filter(any -> acceptedCombinations().noneMatch(any::equals));
    }

    static Stream<TypeCombination> allCombinations() {
      return Arrays.stream(RecordType.values())
          .flatMap(
              recordType ->
                  Arrays.stream(ValueType.values())
                      .map(valueType -> new TypeCombination(recordType, valueType)));
    }

    @ParameterizedTest
    @DisplayName("accepting records of specific RecordType and ValueType")
    @MethodSource("acceptedCombinations")
    void shouldConfigureFilterAccepting(final TypeCombination combination) throws Exception {
      // given
      final var recordType = combination.recordType();
      final var valueType = combination.valueType();
      final var context = new ExporterTestContext();

      // when
      new MetricsExporter().configure(context);

      // then
      final var recordFilter = context.getRecordFilter();
      assertThat(recordFilter.acceptType(recordType) && recordFilter.acceptValue(valueType))
          .describedAs(
              "Expect RecordFilter to accept record of RecordType %s and ValueType %s",
              recordType, valueType)
          .isTrue();
    }

    @ParameterizedTest
    @DisplayName("rejecting records of specific RecordType and ValueType")
    @MethodSource("rejectedCombinations")
    void shouldConfigureFilterRejecting(final TypeCombination combination) throws Exception {
      // given
      final var recordType = combination.recordType();
      final var valueType = combination.valueType();
      final var context = new ExporterTestContext();

      // when
      new MetricsExporter().configure(context);

      // then
      final var recordFilter = context.getRecordFilter();
      assertThat(recordFilter.acceptType(recordType) && recordFilter.acceptValue(valueType))
          .describedAs(
              "Expect RecordFilter to reject record of RecordType %s and ValueType %s",
              recordType, valueType)
          .isFalse();
    }

    /** Defines a combination of a RecordType and a ValueType. */
    record TypeCombination(RecordType recordType, ValueType valueType) {}
  }
}

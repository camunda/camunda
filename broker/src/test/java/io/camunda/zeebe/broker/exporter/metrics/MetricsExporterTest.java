/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import org.junit.jupiter.api.Test;

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
  }
}

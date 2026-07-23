/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.algebra.Algebras;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The {@code instance_cohorts} declaration {@code LakePocApp} wires: a third {@link MetricsRider}
 * on the instances raw table, keyed by the instance's own {@code started_at} slot rather than its
 * completion slot — see {@code LakePocApp#buildSinkWiring}'s own comment for the survival/cohort
 * analysis this and {@code instance_starts} together give. This exercises exactly the declaration
 * shape LakePocApp builds, not a stand-in.
 */
class InstanceCohortDeclarationTest {

  /** Mirrors the real {@code instances} raw schema's columns this declaration actually touches. */
  private static TableSchema instancesLikeSchema() {
    return new TableSchema(
        "instances",
        List.of(
            new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, 0, false),
            new TableSchema.Column(
                "started_at",
                ColumnType.LONG,
                2,
                false,
                -1,
                true,
                TableSchema.LogicalType.TIMESTAMPTZ),
            new TableSchema.Column(
                "ended_at",
                ColumnType.LONG,
                3,
                false,
                -1,
                false,
                TableSchema.LogicalType.TIMESTAMPTZ),
            new TableSchema.Column("duration_ms", ColumnType.LONG, 4, false, -1, false)));
  }

  private static CompiledEntityMetrics instanceCohortMetrics() {
    return EntityMetrics.declare("instance_cohorts", instancesLikeSchema())
        .dims("process_id")
        .window(Duration.ofHours(1), "started_at")
        .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
        .build();
  }

  @Test
  void shouldNameItsGeneratedTablesAfterTheEntity() {
    final CompiledEntityMetrics compiled = instanceCohortMetrics();
    assertThat(compiled.metricsSchema().table()).isEqualTo("instance_cohorts_metrics");
    assertThat(compiled.histSchema().table()).isEqualTo("instance_cohorts_hist");
  }

  @Test
  void shouldHaveAHistogramSinceItDeclaresAnExpHistogramMeasure() {
    assertThat(instanceCohortMetrics().hasHistogram()).isTrue();
  }

  @Test
  void shouldWindowByTheInstanceOwnStartRatherThanItsCompletion() {
    final CompiledEntityMetrics compiled = instanceCohortMetrics();
    // window source is "started_at" (index 1), not the schema's own familyDaySource column
    assertThat(compiled.riderPlan().windowSourceColumn()).isEqualTo(1);
    assertThat(compiled.riderPlan().windowMicros()).isEqualTo(Duration.ofHours(1).toNanos() / 1000);
  }

  @Test
  void shouldNotCollideWithTheExistingInstanceMetricsDeclarationOnTheSameRawTable() {
    // given the pre-existing instanceMetrics declaration LakePocApp also builds off "instances"
    final CompiledEntityMetrics instanceMetrics =
        EntityMetrics.declare("instances", instancesLikeSchema())
            .dims("process_id")
            .window(Duration.ofMinutes(1), "ended_at")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();
    final CompiledEntityMetrics instanceCohorts = instanceCohortMetrics();

    // then: distinct entity names -> distinct generated table names and fingerprints, so both
    // riders can share the one instances SinkPipeline without FlushLoop#drainRiders colliding
    assertThat(instanceMetrics.metricsSchema().table())
        .isNotEqualTo(instanceCohorts.metricsSchema().table());
    assertThat(instanceMetrics.histSchema().table())
        .isNotEqualTo(instanceCohorts.histSchema().table());
    assertThat(instanceMetrics.fingerprint()).isNotEqualTo(instanceCohorts.fingerprint());
  }
}

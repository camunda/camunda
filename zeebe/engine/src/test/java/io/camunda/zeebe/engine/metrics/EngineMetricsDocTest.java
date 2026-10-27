/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The doc enum is the documentation for these meters, so it is worth asserting on. Renaming a meter
 * or a tag value silently breaks every dashboard and alert built on it, and there is no repo-wide
 * check for that.
 */
final class EngineMetricsDocTest {

  @Test
  void shouldPinTheJobActionLabelsDashboardsKeyOn() {
    // given — the tag values dashboards query zeebe_job_events_total{action=...} by
    final var expectedLabels =
        Map.ofEntries(
            Map.entry(JobAction.CREATED, "created"),
            Map.entry(JobAction.ACTIVATED, "activated"),
            // the constant is named for the lease skip it reports, but the label stays "skipped":
            // panel 13 of monitor/grafana/zeebe.json highlights that row orange to warn operators
            // about unleased workers colliding with leased jobs
            Map.entry(JobAction.SKIPPED_LEASED, "skipped"),
            Map.entry(JobAction.SKIPPED_UNCACHED_SECRET, "skipped uncached secret"),
            Map.entry(JobAction.TIMED_OUT, "timed out"),
            Map.entry(JobAction.COMPLETED, "completed"),
            Map.entry(JobAction.FAILED, "failed"),
            Map.entry(JobAction.CANCELED, "canceled"),
            Map.entry(JobAction.ERROR_THROWN, "error thrown"),
            Map.entry(JobAction.WORKERS_NOTIFIED, "workers notified"),
            Map.entry(JobAction.PUSHED, "pushed"));

    // when
    final var actualLabels =
        Stream.of(JobAction.values())
            .collect(Collectors.toMap(Function.identity(), JobAction::getLabel));

    // then — asserting on the whole domain rather than value by value, so a new action fails here
    // and its public tag becomes a decision instead of a side effect of the constant's name
    assertThat(actualLabels).containsExactlyInAnyOrderEntriesOf(expectedLabels);
  }

  @Test
  void shouldGiveEveryJobActionItsOwnLabel() {
    // given/when
    final var labels = Stream.of(JobAction.values()).map(JobAction::getLabel).toList();

    // then — two actions sharing a label merge their causes into one series, and an operator
    // watching that series can no longer tell which one moved
    assertThat(labels).doesNotHaveDuplicates();
  }
}

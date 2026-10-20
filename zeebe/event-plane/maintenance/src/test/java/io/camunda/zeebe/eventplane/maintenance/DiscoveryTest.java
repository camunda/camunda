/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Finds the logs in a store, and leaves everything else in it alone. */
final class DiscoveryTest extends StoreTestCase {

  private MaintenanceReport run() throws Exception {
    return new Maintenance(store, new MaintenanceConfig(Duration.ofDays(1), 1 << 20, null), clock)
        .runOnce();
  }

  @Test
  void shouldMaintainEveryLogOfEveryTenantOfEveryCluster() throws Exception {
    // given: names that are percent-encoded in keys, two clusters, tenants and partitions
    final var logs =
        List.of(
            new LogId("cluster-a", "default", 1),
            new LogId("cluster-a", "default", 2),
            new LogId("cluster-a", "other tenant", 3),
            new LogId("cluster b", "default", 12));
    for (final var log : logs) {
      segments.put(log, HISTORY, 0, TWO_DAYS, entries(1, 3));
      segments.put(log, HISTORY, 0, TWO_DAYS, entries(4, 6));
    }

    // when
    final var report = run();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(4, 8, 0));
    for (final var log : logs) {
      assertThat(segments.segments(log, HISTORY)).as("%s", log).containsExactly("1-6 L1");
    }
  }

  @Test
  void shouldIgnoreWhatIsNotALogOrAHistoryDirectory() throws Exception {
    // given: a log with a rollable history, among objects that are not segments of a log
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));
    segments.putHistory(HISTORY, Duration.ofDays(30), Base.NOTHING);
    segments.putMarker(TWO_DAYS);
    final var others =
        List.of(
            "_groups/g/group.json",
            "_groups/g/cursors/cluster-1/default/1.json",
            "_other/default/1/" + HISTORY + "/not-looked-at.seg",
            ObjectNames.snapshotDescriptor("cluster-1", "default", 1, "snap-1"),
            "cluster-1/default/not-a-partition/"
                + HISTORY
                + "/00000000000000000001-00000000000000000003-L0-00000000000000000000000001.seg",
            "cluster-1/default/1/not-a-history/00000000000000000001-00000000000000000003-L0-00000000000000000000000001.seg",
            "cluster-1/default/1/" + HISTORY + "/readme.txt",
            "stray-object.json");
    for (final var other : others) {
      store.put(other, "x".getBytes(StandardCharsets.UTF_8));
    }
    final var writesBefore = store.writes().size();

    // when
    final var report = run();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(1, 2, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("1-6 L1");
    for (final var other : others) {
      assertThat(store.get(other)).as(other).isPresent();
    }
    assertThat(store.writes().subList(writesBefore, store.writes().size()))
        .noneMatch(write -> others.stream().anyMatch(write::endsWith));
    assertThat(segments.markers()).hasSize(1);
  }

  @Test
  void shouldRunOnAnEmptyStore() throws Exception {
    // when
    final var report = run();

    // then
    assertThat(report).isEqualTo(MaintenanceReport.NONE);
  }

  @Test
  void shouldKeepTheHistoriesOfALogApart() throws Exception {
    // given: two histories of one log, each with segments to roll up
    final var other = UUID.fromString("c0ffee00-1234-4abc-8def-0123456789ab");
    segments.put(HISTORY, 0, TWO_DAYS, entries(1, 3));
    segments.put(HISTORY, 0, TWO_DAYS, entries(4, 6));
    segments.put(other, 0, TWO_DAYS, entries(1, 4));
    segments.put(other, 0, TWO_DAYS, entries(5, 8));

    // when
    final var report = run();

    // then
    assertThat(report).isEqualTo(new MaintenanceReport(2, 4, 0));
    assertThat(segments.segments(HISTORY)).containsExactly("1-6 L1");
    assertThat(segments.segments(other)).containsExactly("1-8 L1");
  }
}

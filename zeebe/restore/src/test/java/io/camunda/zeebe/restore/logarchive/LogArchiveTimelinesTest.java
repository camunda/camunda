/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LogArchiveTimelinesTest {

  private final InMemoryLogArchiveStore store = new InMemoryLogArchiveStore();

  @Test
  void shouldDiscoverTheSeedsAndTheirDescendants() {
    // given
    TimelineFixture.start(store, "tl", 1, 1, "").archive(10);
    TimelineFixture.start(store, "tl.r5", 1, 3, "tl").archive(10);
    TimelineFixture.start(store, "tl.r5.f4", 1, 3, "tl.r5").archive(10);
    // started without a parent after restoring an old backup from before the activation
    TimelineFixture.start(store, "tl.b3", 1, 1, "").archive(10);
    // an id that does not start with the seed, e.g. after the configured timeline was renamed
    TimelineFixture.start(store, "manual", 1, 1, "tl.r5").archive(10);
    TimelineFixture.start(store, "renamed", 1, 1, "manual").archive(10);
    // unrelated, although their ids start like the seed
    TimelineFixture.start(store, "tl2", 1, 1, "").archive(10);
    TimelineFixture.start(store, "tl.x", 1, 1, "").archive(10);
    // other partitions are discovered separately
    TimelineFixture.start(store, "tl.r7", 2, 1, "tl").archive(10);

    // when
    final var discovered = LogArchiveTimelines.discover(store, Map.of(1, Set.of("tl")));

    // then
    assertThat(discovered.get(1))
        .extracting(LogArchiveView::timelineId)
        .containsExactly("manual", "renamed", "tl", "tl.b3", "tl.r5", "tl.r5.f4");
  }

  @Test
  void shouldDiscoverAParentlessTimelineNamedByAHandover() {
    // given
    TimelineFixture.start(store, "tl", 1, 1, "").archive(10);
    TimelineFixture.start(store, "other", 1, 1, "").archive(10);
    TimelineFixture.start(store, "other.r2", 1, 1, "other").archive(10);

    // when
    final var discovered = LogArchiveTimelines.discover(store, Map.of(1, Set.of("tl", "other")));

    // then
    assertThat(discovered.get(1))
        .extracting(LogArchiveView::timelineId)
        .containsExactly("other", "other.r2", "tl");
  }

  @Test
  void shouldFindNothingWithoutAnArchive() {
    // when
    final var discovered = LogArchiveTimelines.discover(store, Map.of(1, Set.of("tl")));

    // then
    assertThat(discovered.get(1)).isEmpty();
  }

  @Test
  void shouldFindTimelinesOfAnArchiveWithoutRegistryFilesByTheirIds() {
    // given
    TimelineFixture.startWithoutRegistry(store, "tl", 1, 1, "").archive(10);
    TimelineFixture.startWithoutRegistry(store, "tl.r5", 1, 3, "tl").archive(10);
    TimelineFixture.startWithoutRegistry(store, "tl2", 1, 1, "").archive(10);
    // no registry file and no id of a seed: not found
    TimelineFixture.startWithoutRegistry(store, "manual", 1, 1, "tl.r5").archive(10);

    // when
    final var discovered = LogArchiveTimelines.discover(store, Map.of(1, Set.of("tl")));

    // then
    assertThat(discovered.get(1))
        .extracting(LogArchiveView::timelineId)
        .containsExactly("tl", "tl.r5");
  }

  @Test
  void shouldNotListBelowTheTimelineLevel() {
    // given
    TimelineFixture.start(store, "tl", 1, 1, "").archive(10);
    TimelineFixture.start(store, "tl.r5", 1, 3, "tl").archive(10);
    TimelineFixture.start(store, "renamed", 1, 1, "tl.r5").archive(10);
    TimelineFixture.start(store, "unrelated", 1, 1, "").archive(10);
    store.calls.clear();

    // when
    LogArchiveTimelines.discover(store, Map.of(1, Set.of("tl")));

    // then - one delimiter listing and one registry file per timeline; the views of the selected
    // timelines only list root/ (the fixture writes no _latest.json), never a timeline prefix
    assertThat(store.calls)
        .filteredOn(call -> call.startsWith("list") && !call.endsWith("/root/"))
        .containsExactly("listChildPrefixes log-archive/");
    assertThat(store.calls)
        .filteredOn(call -> call.endsWith("timeline.json"))
        .containsExactlyInAnyOrder(
            "get log-archive/renamed/p1/timeline.json",
            "get log-archive/tl/p1/timeline.json",
            "get log-archive/tl.r5/p1/timeline.json",
            "get log-archive/unrelated/p1/timeline.json");
    assertThat(store.calls).noneMatch(call -> call.contains("log-archive/unrelated/p1/commits"));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class SegmentDedupTest {

  @Test
  void shouldAdmitEachNewSegmentOnceAndSkipReemits() {
    // given
    final SegmentDedup dedup = new SegmentDedup();

    // when/then — first delivery of a segment is admitted
    assertThat(dedup.admit(0, 0L, 0)).isTrue();
    // a producer re-emit of the same segment (same position) is skipped
    assertThat(dedup.admit(0, 0L, 0)).isFalse();
    // the next segment is admitted
    assertThat(dedup.admit(0, 1L, 0)).isTrue();
    // a late re-emit of the earlier, already-merged segment is skipped
    assertThat(dedup.admit(0, 0L, 0)).isFalse();
  }

  @Test
  void shouldAdmitLaterChunksOfTheSameSegmentButSkipReemittedChunks() {
    // given a segment split into ordered chunks
    final SegmentDedup dedup = new SegmentDedup();

    // then chunk 0 then chunk 1 are both admitted (a later chunk of the open segment)
    assertThat(dedup.admit(0, 5L, 0)).isTrue();
    assertThat(dedup.admit(0, 5L, 1)).isTrue();
    // but a re-emit of chunk 0 (or chunk 1) is skipped
    assertThat(dedup.admit(0, 5L, 0)).isFalse();
    assertThat(dedup.admit(0, 5L, 1)).isFalse();
    // and the next segment still passes
    assertThat(dedup.admit(0, 6L, 0)).isTrue();
  }

  @Test
  void shouldTrackWatermarksPerSourcePartitionIndependently() {
    // given
    final SegmentDedup dedup = new SegmentDedup();

    // then a segment number consumed on one partition does not gate another
    assertThat(dedup.admit(0, 3L, 0)).isTrue();
    assertThat(dedup.admit(1, 0L, 0)).isTrue();
    assertThat(dedup.admit(1, 1L, 0)).isTrue();
    assertThat(dedup.admit(0, 3L, 0)).isFalse();
  }

  @Test
  void shouldSurviveRestartViaSnapshotRestore() {
    // given a dedup that has admitted some segments
    final SegmentDedup before = new SegmentDedup();
    before.admit(0, 4L, 2);
    before.admit(1, 1L, 0);

    // when a fresh dedup restores its snapshot (a restart)
    final SegmentDedup after = new SegmentDedup();
    after.restore(before.snapshot());

    // then already-merged positions are still skipped, and progress continues
    assertThat(after.admit(0, 4L, 2)).isFalse();
    assertThat(after.admit(0, 4L, 1)).isFalse(); // an earlier chunk, already covered
    assertThat(after.admit(0, 4L, 3)).isTrue();
    assertThat(after.admit(1, 1L, 0)).isFalse();
  }
}

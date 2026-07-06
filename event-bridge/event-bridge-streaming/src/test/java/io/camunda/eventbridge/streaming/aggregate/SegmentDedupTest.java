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

  private static final int STREAM = 7;

  @Test
  void shouldAdmitEachNewSegmentOnceAndSkipReemits() {
    // given
    final SegmentDedup dedup = new SegmentDedup();

    // when/then — first delivery of a segment is admitted
    assertThat(dedup.admit(0, STREAM, 0L, 0)).isTrue();
    // a producer re-emit of the same segment (same position) is skipped
    assertThat(dedup.admit(0, STREAM, 0L, 0)).isFalse();
    // the next segment is admitted
    assertThat(dedup.admit(0, STREAM, 1L, 0)).isTrue();
    // a late re-emit of the earlier, already-merged segment is skipped
    assertThat(dedup.admit(0, STREAM, 0L, 0)).isFalse();
  }

  @Test
  void shouldAdmitLaterChunksOfTheSameSegmentButSkipReemittedChunks() {
    // given a segment split into ordered chunks
    final SegmentDedup dedup = new SegmentDedup();

    // then chunk 0 then chunk 1 are both admitted (a later chunk of the open segment)
    assertThat(dedup.admit(0, STREAM, 5L, 0)).isTrue();
    assertThat(dedup.admit(0, STREAM, 5L, 1)).isTrue();
    // but a re-emit of chunk 0 (or chunk 1) is skipped
    assertThat(dedup.admit(0, STREAM, 5L, 0)).isFalse();
    assertThat(dedup.admit(0, STREAM, 5L, 1)).isFalse();
    // and the next segment still passes
    assertThat(dedup.admit(0, STREAM, 6L, 0)).isTrue();
  }

  @Test
  void shouldTrackWatermarksPerSourcePartitionIndependently() {
    // given
    final SegmentDedup dedup = new SegmentDedup();

    // then a segment number consumed on one partition does not gate another
    assertThat(dedup.admit(0, STREAM, 3L, 0)).isTrue();
    assertThat(dedup.admit(1, STREAM, 0L, 0)).isTrue();
    assertThat(dedup.admit(1, STREAM, 1L, 0)).isTrue();
    assertThat(dedup.admit(0, STREAM, 3L, 0)).isFalse();
  }

  @Test
  void shouldTrackWatermarksPerStreamIndependentlyWithinAPartition() {
    // given one source partition multiplexing a fast stream and a slow stream
    final SegmentDedup dedup = new SegmentDedup();
    final int fast = 5;
    final int slow = 2;

    // when the fast stream races ahead over several segments on the partition
    assertThat(dedup.admit(0, fast, 54L, 0)).isTrue();
    assertThat(dedup.admit(0, fast, 55L, 0)).isTrue();
    assertThat(dedup.admit(0, fast, 56L, 0)).isTrue();

    // then the slow stream's later, lower-segment batch is still admitted — its own watermark is
    // untouched by the fast stream (the completion-drop regression: a shared per-partition
    // watermark would drop this as a stale re-emit).
    assertThat(dedup.admit(0, slow, 54L, 1)).isTrue();
    // and the slow stream still dedups its own re-emits
    assertThat(dedup.admit(0, slow, 54L, 1)).isFalse();
    assertThat(dedup.admit(0, slow, 54L, 0)).isFalse();
    assertThat(dedup.admit(0, slow, 55L, 0)).isTrue();
  }

  @Test
  void shouldSurviveRestartViaSnapshotRestore() {
    // given a dedup that has admitted some segments across streams
    final SegmentDedup before = new SegmentDedup();
    before.admit(0, STREAM, 4L, 2);
    before.admit(1, STREAM, 1L, 0);

    // when a fresh dedup restores its snapshot (a restart)
    final SegmentDedup after = new SegmentDedup();
    after.restore(before.snapshot());

    // then already-merged positions are still skipped, and progress continues
    assertThat(after.admit(0, STREAM, 4L, 2)).isFalse();
    assertThat(after.admit(0, STREAM, 4L, 1)).isFalse(); // an earlier chunk, already covered
    assertThat(after.admit(0, STREAM, 4L, 3)).isTrue();
    assertThat(after.admit(1, STREAM, 1L, 0)).isFalse();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.sink.BackpressureGate;
import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.ColumnVector;
import io.camunda.analytics.lake.sink.ColumnarSegmentRing;
import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.sink.SealReason;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.TableSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class SegmentRowAppenderTest {

  private static final int ROW_CAPACITY = 4;

  private static TableSchema schema() {
    return new TableSchema(
        "appender_test",
        List.of(new TableSchema.Column("ts", ColumnType.LONG, 1, false, -1, true)));
  }

  private static Segment[] segments(final int count) {
    return SegmentFactory.createSegments(
        schema(), count, ROW_CAPACITY, new int[] {0}, new Interner());
  }

  private void appendOneRow(final RowAppender appender, final long ts) {
    appender.putLong(0, ts);
    appender.endRow();
  }

  @Test
  void shouldFillSegmentThenAutoSealAndContinueIntoTheNextSlot() {
    // given a ring with room to seal once and keep going
    final RecordingGate gate = new RecordingGate();
    final ColumnarSegmentRing ring = new ColumnarSegmentRing(segments(3), gate);
    final RowAppender appender = new SegmentRowAppender(ring);

    // when: fill the first segment exactly to capacity
    for (int i = 0; i < ROW_CAPACITY; i++) {
      assertThat(appender.begin()).isTrue();
      appendOneRow(appender, i);
    }
    // then appending one more row must auto-seal the full segment and continue transparently
    assertThat(appender.begin()).isTrue();
    appendOneRow(appender, 999L);

    // then: one sealed segment is now queued for the flush side, full and tagged SEGMENT_FULL
    final Segment sealed = ring.take();
    assertThat(sealed).isNotNull();
    assertThat(sealed.size()).isEqualTo(ROW_CAPACITY);
    assertThat(sealed.sealReason()).isEqualTo(SealReason.SEGMENT_FULL);
    assertThat(((ColumnVector.LongColumn) sealed.vector(0)).get(0)).isEqualTo(0L);
    assertThat(gate.pauseCount).isZero();
  }

  @Test
  void shouldReturnFalseAndPauseGateExactlyOnceWhenRingIsFull() {
    // given the minimum ring size (2 segments): fill segment 0, roll to segment 1, fill it too
    final RecordingGate gate = new RecordingGate();
    final ColumnarSegmentRing ring = new ColumnarSegmentRing(segments(2), gate);
    final RowAppender appender = new SegmentRowAppender(ring);

    for (int i = 0; i < ROW_CAPACITY; i++) {
      assertThat(appender.begin()).isTrue();
      appendOneRow(appender, i);
    }
    // this row auto-seals segment 0 and starts filling segment 1
    assertThat(appender.begin()).isTrue();
    appendOneRow(appender, 100L);
    for (int i = 1; i < ROW_CAPACITY; i++) {
      assertThat(appender.begin()).isTrue();
      appendOneRow(appender, 100L + i);
    }

    // when: segment 1 is now also full, and there is no free slot left (segment 0 still sealed,
    // unreleased) — the next begin() must engage backpressure instead of sealing
    final boolean began = appender.begin();

    // then
    assertThat(began).isFalse();
    assertThat(gate.pauseCount).isEqualTo(1);
    assertThat(gate.resumeCount).isZero();
  }

  @Test
  void shouldResumeTheGateAndAllowFillingAgainOnceASegmentIsReleased() {
    // given a full ring (as in the previous scenario)
    final RecordingGate gate = new RecordingGate();
    final Segment[] segmentArray = segments(2);
    final ColumnarSegmentRing ring = new ColumnarSegmentRing(segmentArray, gate);
    final RowAppender appender = new SegmentRowAppender(ring);
    for (int i = 0; i < 2 * ROW_CAPACITY; i++) {
      assertThat(appender.begin()).isTrue();
      appendOneRow(appender, i);
    }
    assertThat(appender.begin()).isFalse();
    assertThat(gate.pauseCount).isEqualTo(1);

    // when the flush side releases the one sealed segment
    final Segment sealed = ring.take();
    ring.release(sealed);

    // then the gate is resumed exactly once, and the poll thread can make progress again
    assertThat(gate.resumeCount).isEqualTo(1);
    assertThat(appender.begin()).isTrue();
  }

  @Test
  void shouldThrowWhenReleasingOutOfOrder() {
    // given a ring with one sealed segment at the tail and another still filling
    final RecordingGate gate = new RecordingGate();
    final Segment[] segmentArray = segments(2);
    final ColumnarSegmentRing ring = new ColumnarSegmentRing(segmentArray, gate);
    final RowAppender appender = new SegmentRowAppender(ring);
    for (int i = 0; i < ROW_CAPACITY; i++) {
      assertThat(appender.begin()).isTrue();
      appendOneRow(appender, i);
    }
    assertThat(appender.begin()).isTrue(); // rolls segment 0 sealed, segment 1 now filling

    // when / then: releasing anything other than the segment actually at the tail must throw
    final Segment notAtTail = ring.filling();
    assertThatThrownBy(() -> ring.release(notAtTail)).isInstanceOf(IllegalStateException.class);
  }

  private static final class RecordingGate implements BackpressureGate {
    private int pauseCount;
    private int resumeCount;

    @Override
    public void pause() {
      pauseCount++;
    }

    @Override
    public void resume() {
      resumeCount++;
    }
  }
}

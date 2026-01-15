/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.appint.exporter.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.record.Record;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BatchTests {

  private static final int BATCH_SIZE = 3;
  private static final long FLUSH_INTERVAL = 500;

  private Batch<String> batch;

  @BeforeEach
  void setUp() {
    batch = new Batch<>(BATCH_SIZE, FLUSH_INTERVAL);
  }

  @Test
  void shouldBeEmptyInitially() {
    assertThat(batch.isEmpty()).isTrue();
    assertThat(batch.getSize()).isZero();
    assertThat(batch.spaceLeft()).isEqualTo(BATCH_SIZE);
  }

  @Test
  void shouldAddRecord() {
    final Record<?> record = mockRecord(1L);

    final boolean added = batch.addRecord(record, r -> "entry");

    assertThat(added).isTrue();
    assertThat(batch.getSize()).isEqualTo(1);
    assertThat(batch.isEmpty()).isFalse();
    assertThat(batch.getLastLogPosition()).isEqualTo(1L);
  }

  @Test
  void shouldNotAddRecordWithLowerPosition() {
    batch.addRecord(mockRecord(10L), r -> "entry1");

    final boolean added = batch.addRecord(mockRecord(5L), r -> "entry2");

    assertThat(added).isFalse();
    assertThat(batch.getSize()).isEqualTo(1);
  }

  @Test
  void shouldNotAddRecordWithSamePosition() {
    batch.addRecord(mockRecord(10L), r -> "entry1");

    final boolean added = batch.addRecord(mockRecord(10L), r -> "entry2");

    assertThat(added).isFalse();
    assertThat(batch.getSize()).isEqualTo(1);
  }

  @Test
  void shouldBeFullWhenSizeReached() {
    batch.addRecord(mockRecord(1L), r -> "entry1");
    batch.addRecord(mockRecord(2L), r -> "entry2");
    batch.addRecord(mockRecord(3L), r -> "entry3");

    assertThat(batch.isFull()).isTrue();
    assertThat(batch.spaceLeft()).isZero();
  }

  @Test
  void shouldThrowWhenAddingToFullBatch() {
    batch.addRecord(mockRecord(1L), r -> "entry1");
    batch.addRecord(mockRecord(2L), r -> "entry2");
    batch.addRecord(mockRecord(3L), r -> "entry3");

    assertThatThrownBy(() -> batch.addRecord(mockRecord(4L), r -> "entry4"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Batch has too many entries");
  }

  @Test
  void shouldFlushAndReturnLastPosition() {
    batch.addRecord(mockRecord(1L), r -> "entry1");
    batch.addRecord(mockRecord(2L), r -> "entry2");

    final long lastPosition = batch.flush();

    assertThat(lastPosition).isEqualTo(2L);
    assertThat(batch.isEmpty()).isTrue();
    assertThat(batch.getSize()).isZero();
  }

  @Test
  void shouldThrowWhenFlushingEmptyBatch() {
    assertThatThrownBy(() -> batch.flush())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Flushing empty batch not allowed");
  }

  @Test
  void shouldReturnCopyOfEntries() {
    batch.addRecord(mockRecord(1L), r -> "entry1");
    batch.addRecord(mockRecord(2L), r -> "entry2");

    final var entries = batch.getEntries();

    assertThat(entries).containsExactly("entry1", "entry2");
    entries.clear();
    assertThat(batch.getSize()).isEqualTo(2);
  }

  @Test
  void shouldFlushWhenFull() {
    batch.addRecord(mockRecord(1L), r -> "entry1");
    batch.addRecord(mockRecord(2L), r -> "entry2");
    batch.addRecord(mockRecord(3L), r -> "entry3");

    assertThat(batch.shouldFlush()).isTrue();
  }

  @Test
  void shouldFlushWhenIntervalReached() {
    batch = new Batch<>(BATCH_SIZE, FLUSH_INTERVAL);
    assertThat(batch.shouldFlush()).isFalse();
    assertThat(batch.isFull()).isFalse();
    assertThat(batch.isTimeThresholdReached()).isFalse();

    batch.addRecord(mockRecord(1L), r -> "entry1");
    assertThat(batch.isFull()).isFalse();

    Awaitility.await().until(() -> batch.isTimeThresholdReached());
    assertThat(batch.shouldFlush()).isTrue();
  }

  @Test
  void shouldNotFlushWhenEmpty() {
    assertThat(batch.shouldFlush()).isFalse();
  }

  private Record<?> mockRecord(final long position) {
    final Record<?> record = mock(Record.class);
    when(record.getPosition()).thenReturn(position);
    return record;
  }
}

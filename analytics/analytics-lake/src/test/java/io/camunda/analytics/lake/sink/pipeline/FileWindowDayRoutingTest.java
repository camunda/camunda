/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.TableSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for Fix 2: {@link FileWindow} used to route any window spanning more than 3
 * distinct family days, and every negative epoch day, into one shared {@code epochDay == -1} spill
 * file — {@code DirectCommitSink} then registered that file under the literal partition 1969-12-31,
 * making every real day's rows in it unfindable by a day-filtered scan, and silently
 * indistinguishable from any real family day that itself happened to be 1969-12-31 (epoch day -1).
 * Now that {@link FileWindow} is a thin wrapper around {@code
 * io.camunda.analytics.lake.sink.encode.DayRouter}, a window spanning 5 distinct days — including a
 * real epoch day of -1 — must produce 5 correctly-tagged files, never a "-1" sentinel mixing them.
 */
class FileWindowDayRoutingTest {

  @Test
  void shouldTagFiveDistinctDaysIncludingARealNegativeDayAsFiveSeparateFiles() {
    // given: one row per distinct family day, spanning more than DayRouter's concurrently-open cap
    // (3) and including a real day before 1970-01-01 (epochDay == -1) -- the very value the old
    // FileWindow used as its spill sentinel
    final TableSchema schema = TestPipelines.schema("t-file-window-days");
    final FakeBatchEncoderFactory factory = new FakeBatchEncoderFactory();
    final FileWindow window = new FileWindow(factory, schema);

    final long[] days = {-2L, -1L, 0L, 1L, 2L};
    for (int i = 0; i < days.length; i++) {
      final Segment segment = TestPipelines.segmentWithValues(schema, i);
      window.append(new IdentitySortedRun(segment, days[i]));
    }

    // when
    final List<DataFileResult> results = window.finishAll();

    // then: 5 files, one per real family day -- none collapsed into a shared "-1" sentinel file
    assertThat(results).hasSize(5);
    final List<Long> epochDays = results.stream().map(DataFileResult::epochDay).toList();
    assertThat(epochDays).containsExactlyInAnyOrder(-2L, -1L, 0L, 1L, 2L);
    for (final DataFileResult file : results) {
      assertThat(file.rowCount()).as("rows in file for day %d", file.epochDay()).isEqualTo(1);
    }
  }
}

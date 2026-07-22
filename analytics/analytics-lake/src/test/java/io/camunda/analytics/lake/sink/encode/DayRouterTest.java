/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.encode;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.TableSchema;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.apache.iceberg.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DayRouterTest {

  @TempDir Path tempDir;

  @Test
  void shouldCapOpenDayEncodersAndSpillExtraDaysToASharedFile() {
    // given: 4 distinct family days, one row each
    final TableSchema schema = EncodeTestFixtures.instancesSchema();
    final Schema icebergSchema = EncodeTestFixtures.icebergSchema(schema);
    final LocalFileSink fileSink = new LocalFileSink(tempDir);
    final IcebergParquetEncoderFactory factory =
        new IcebergParquetEncoderFactory(icebergSchema, fileSink, 100, Set.of());
    final DayRouter router = new DayRouter(schema, factory);

    final Object[][] columns = new Object[5][4];
    for (int i = 0; i < 4; i++) {
      columns[0][i] = (long) i;
      columns[1][i] = 1_000L + i;
      columns[2][i] = "P";
      columns[3][i] = (long) i;
      columns[4][i] = new byte[] {(byte) i};
    }
    final long[] days = {10, 11, 12, 13};
    final FakeSortedRun run = new FakeSortedRun(schema, columns, days);

    // when
    router.route(run);
    final List<DataFileResult> results = router.closeAll();

    // then: 3 day files (the first 3 distinct days) + 1 spill file at epochDay -1
    assertThat(results).hasSize(4);
    final List<Long> epochDays = results.stream().map(DataFileResult::epochDay).toList();
    assertThat(epochDays).containsExactlyInAnyOrder(10L, 11L, 12L, -1L);

    final DataFileResult spill =
        results.stream().filter(r -> r.epochDay() == -1L).findFirst().orElseThrow();
    assertThat(spill.rowCount()).isEqualTo(1);

    final long totalRows = results.stream().mapToLong(DataFileResult::rowCount).sum();
    assertThat(totalRows).isEqualTo(4);
  }

  @Test
  void shouldRouteAnAlreadyMixedDayRangeDirectlyToTheSameSpillSlot() {
    // given: a run whose SortedRun already reports epochDay < 0 for one of its ranges (e.g. an
    // unpartitioned-table mixed-day dump), alongside 3 normal distinct days -- exactly filling the
    // cap -- so the pre-existing "-1" range must land in the same shared spill file, not open a
    // 4th day slot.
    final TableSchema schema = EncodeTestFixtures.instancesSchema();
    final Schema icebergSchema = EncodeTestFixtures.icebergSchema(schema);
    final LocalFileSink fileSink = new LocalFileSink(tempDir);
    final IcebergParquetEncoderFactory factory =
        new IcebergParquetEncoderFactory(icebergSchema, fileSink, 100, Set.of());
    final DayRouter router = new DayRouter(schema, factory);

    final Object[][] columns = new Object[5][4];
    for (int i = 0; i < 4; i++) {
      columns[0][i] = (long) i;
      columns[1][i] = 1_000L + i;
      columns[2][i] = "P";
      columns[3][i] = (long) i;
      columns[4][i] = new byte[] {(byte) i};
    }
    final long[] days = {10, 11, 12, -1};
    final FakeSortedRun run = new FakeSortedRun(schema, columns, days);

    // when
    router.route(run);
    final List<DataFileResult> results = router.closeAll();

    // then: still exactly 4 files (3 day files + the one shared spill), not 5
    assertThat(results).hasSize(4);
    final List<Long> epochDays = results.stream().map(DataFileResult::epochDay).toList();
    assertThat(epochDays).containsExactlyInAnyOrder(10L, 11L, 12L, -1L);
  }

  @Test
  void shouldReturnEmptyListWhenNothingWasRouted() {
    // given
    final TableSchema schema = EncodeTestFixtures.instancesSchema();
    final Schema icebergSchema = EncodeTestFixtures.icebergSchema(schema);
    final LocalFileSink fileSink = new LocalFileSink(tempDir);
    final IcebergParquetEncoderFactory factory =
        new IcebergParquetEncoderFactory(icebergSchema, fileSink, 100, Set.of());
    final DayRouter router = new DayRouter(schema, factory);

    // when
    final List<DataFileResult> results = router.closeAll();

    // then
    assertThat(results).isEmpty();
  }

  @Test
  void shouldAbortAllOpenEncodersWithoutThrowing() {
    // given
    final TableSchema schema = EncodeTestFixtures.instancesSchema();
    final Schema icebergSchema = EncodeTestFixtures.icebergSchema(schema);
    final LocalFileSink fileSink = new LocalFileSink(tempDir);
    final IcebergParquetEncoderFactory factory =
        new IcebergParquetEncoderFactory(icebergSchema, fileSink, 100, Set.of());
    final DayRouter router = new DayRouter(schema, factory);

    final Object[][] columns = new Object[5][2];
    for (int i = 0; i < 2; i++) {
      columns[0][i] = (long) i;
      columns[1][i] = 1_000L + i;
      columns[2][i] = "P";
      columns[3][i] = (long) i;
      columns[4][i] = new byte[] {(byte) i};
    }
    final FakeSortedRun run = new FakeSortedRun(schema, columns, new long[] {5, 6});
    router.route(run);

    // when / then
    router.abortAll();
    // safe to call again on an already-empty router
    router.abortAll();
    assertThat(router.closeAll()).isEmpty();
  }
}

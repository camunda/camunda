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
  void shouldWriteStragglerDaysAsTheirOwnOneShotFilesBeyondTheOpenCap() {
    // given: 4 distinct family days, one row each -- one more than MAX_OPEN_DAY_ENCODERS (3)
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

    // then: 4 files, one per real family day -- no "mixed"/spill sentinel; a partitioned table's
    // data file must carry exactly one partition tuple, so the 4th day (beyond the open cap) gets
    // its own dedicated one-shot file instead of sharing one
    assertThat(results).hasSize(4);
    final List<Long> epochDays = results.stream().map(DataFileResult::epochDay).toList();
    assertThat(epochDays).containsExactlyInAnyOrder(10L, 11L, 12L, 13L);

    final DataFileResult straggler =
        results.stream().filter(r -> r.epochDay() == 13L).findFirst().orElseThrow();
    assertThat(straggler.rowCount()).isEqualTo(1);

    final long totalRows = results.stream().mapToLong(DataFileResult::rowCount).sum();
    assertThat(totalRows).isEqualTo(4);
  }

  @Test
  void shouldWriteEachStragglerRouteCallAsItsOwnSeparateFile() {
    // given: 3 days fill the open cap, then two SEPARATE route() calls each contribute one more
    // row for the very same 4th (straggler) day -- each call must produce its own one-shot file,
    // never merged into a shared spill file (that concept no longer exists, see class javadoc)
    final TableSchema schema = EncodeTestFixtures.instancesSchema();
    final Schema icebergSchema = EncodeTestFixtures.icebergSchema(schema);
    final LocalFileSink fileSink = new LocalFileSink(tempDir);
    final IcebergParquetEncoderFactory factory =
        new IcebergParquetEncoderFactory(icebergSchema, fileSink, 100, Set.of());
    final DayRouter router = new DayRouter(schema, factory);

    final Object[][] firstColumns = new Object[5][4];
    for (int i = 0; i < 4; i++) {
      firstColumns[0][i] = (long) i;
      firstColumns[1][i] = 1_000L + i;
      firstColumns[2][i] = "P";
      firstColumns[3][i] = (long) i;
      firstColumns[4][i] = new byte[] {(byte) i};
    }
    final long[] firstDays = {10, 11, 12, 13};
    router.route(new FakeSortedRun(schema, firstColumns, firstDays));

    final Object[][] secondColumns = new Object[5][1];
    secondColumns[0][0] = 99L;
    secondColumns[1][0] = 1_099L;
    secondColumns[2][0] = "P";
    secondColumns[3][0] = 99L;
    secondColumns[4][0] = new byte[] {9};
    router.route(new FakeSortedRun(schema, secondColumns, new long[] {13}));

    // when
    final List<DataFileResult> results = router.closeAll();

    // then: 3 open-day files + TWO separate day-13 straggler files (not one merged file)
    assertThat(results).hasSize(5);
    final List<DataFileResult> day13Files =
        results.stream().filter(r -> r.epochDay() == 13L).toList();
    assertThat(day13Files).hasSize(2);
    assertThat(day13Files).allSatisfy(r -> assertThat(r.rowCount()).isEqualTo(1));
    assertThat(day13Files.stream().map(DataFileResult::path).distinct()).hasSize(2);
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

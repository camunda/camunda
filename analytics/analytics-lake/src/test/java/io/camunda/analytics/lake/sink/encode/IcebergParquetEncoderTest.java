/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.encode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.write.LocalFileIO;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Types;
import org.apache.parquet.ParquetReadOptions;
import org.apache.parquet.conf.PlainParquetConfiguration;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IcebergParquetEncoderTest {

  @TempDir Path tempDir;

  @Test
  void shouldEncodeFieldIdsBloomFiltersAndValuesPerDay() throws Exception {
    // given
    final TableSchema schema = EncodeTestFixtures.instancesSchema();
    final Schema icebergSchema = EncodeTestFixtures.icebergSchema(schema);
    final LocalFileSink fileSink = new LocalFileSink(tempDir);
    final IcebergParquetEncoderFactory factory =
        new IcebergParquetEncoderFactory(
            icebergSchema, fileSink, 100, Set.of("instance_key", "element_key"));
    final DayRouter router = new DayRouter(schema, factory);

    final long dayA = 100L;
    final long dayB = 101L;
    final Object[][] columns = new Object[5][5];
    // instance_key
    columns[0] = new Object[] {1L, 2L, 3L, 4L, 5L};
    // start_ms (family day source)
    columns[1] = new Object[] {1_000L, 1_001L, 1_002L, 2_000L, 2_001L};
    // process_id (dict-resolved string, one null)
    columns[2] = new Object[] {"P1", "P1", null, "P2", "P2"};
    // element_key (nullable long)
    columns[3] = new Object[] {10L, null, 30L, 40L, null};
    // vars_json (nullable binary, one null)
    columns[4] =
        new Object[] {
          new byte[] {1, 2, 3}, null, new byte[] {9}, new byte[] {5, 6}, new byte[] {7, 8, 9, 10}
        };
    final FakeSortedRun run =
        new FakeSortedRun(schema, columns, new long[] {dayA, dayA, dayA, dayB, dayB});

    // when
    router.route(run);
    final List<DataFileResult> results = router.closeAll();

    // then
    assertThat(results).hasSize(2);
    final DataFileResult dayAResult = results.get(0);
    final DataFileResult dayBResult = results.get(1);
    assertThat(dayAResult.epochDay()).isEqualTo(dayA);
    assertThat(dayAResult.rowCount()).isEqualTo(3);
    assertThat(dayBResult.epochDay()).isEqualTo(dayB);
    assertThat(dayBResult.rowCount()).isEqualTo(2);

    assertFieldIdsAndBloomFilters(dayAResult, schema);
    assertFieldIdsAndBloomFilters(dayBResult, schema);

    assertDayAValuesViaDuckDb(dayAResult);
    assertDayBValuesViaDuckDb(dayBResult);

    assertMetrics(dayAResult, schema);
  }

  private void assertFieldIdsAndBloomFilters(final DataFileResult result, final TableSchema schema)
      throws Exception {
    final Path physicalPath = LocalFileIO.toFilesystemPath(result.path());
    final ParquetReadOptions options =
        ParquetReadOptions.builder(new PlainParquetConfiguration()).build();
    try (ParquetFileReader reader =
        ParquetFileReader.open(new LocalParquetInputFile(physicalPath), options)) {
      final ParquetMetadata footer = reader.getFooter();
      final MessageType fileSchema = footer.getFileMetaData().getSchema();

      // field ids in the file must be exactly the TableSchema's authoritative ids
      for (final TableSchema.Column column : schema.columns()) {
        final Type field = fileSchema.getType(column.name());
        assertThat(field.getId().intValue())
            .as("field id for column %s", column.name())
            .isEqualTo(column.icebergFieldId());
      }

      // exactly one append per day range -> exactly one row group, given the large
      // targetRowGroupRows configured in the test
      assertThat(footer.getBlocks()).hasSize(1);
      final BlockMetaData block = footer.getBlocks().get(0);
      assertThat(block.getRowCount()).isEqualTo(result.rowCount());

      final Set<String> bloomColumns = Set.of("instance_key", "element_key");
      for (final ColumnChunkMetaData columnMeta : block.getColumns()) {
        final String columnName = columnMeta.getPath().toDotString();
        if (bloomColumns.contains(columnName)) {
          assertThat(columnMeta.getBloomFilterOffset())
              .as("bloom filter offset for %s", columnName)
              .isGreaterThanOrEqualTo(0);
        } else {
          assertThat(columnMeta.getBloomFilterOffset())
              .as("bloom filter offset for %s", columnName)
              .isEqualTo(-1);
        }
      }
    }
  }

  private void assertDayAValuesViaDuckDb(final DataFileResult result) throws SQLException {
    final Path physicalPath = LocalFileIO.toFilesystemPath(result.path());
    try (Connection duckdb = DriverManager.getConnection("jdbc:duckdb:");
        Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT instance_key, process_id, element_key, vars_json FROM read_parquet('"
                    + physicalPath
                    + "') ORDER BY instance_key")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong("instance_key")).isEqualTo(1L);
      assertThat(rs.getString("process_id")).isEqualTo("P1");
      assertThat(rs.getLong("element_key")).isEqualTo(10L);
      assertThat(blobBytes(rs, "vars_json")).isEqualTo(new byte[] {1, 2, 3});

      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong("instance_key")).isEqualTo(2L);
      assertThat(rs.getString("process_id")).isEqualTo("P1");
      rs.getLong("element_key");
      assertThat(rs.wasNull()).isTrue();
      assertThat(blobBytes(rs, "vars_json")).isNull();

      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong("instance_key")).isEqualTo(3L);
      assertThat(rs.getString("process_id")).isNull();
      assertThat(rs.getLong("element_key")).isEqualTo(30L);
      assertThat(blobBytes(rs, "vars_json")).isEqualTo(new byte[] {9});

      assertThat(rs.next()).isFalse();
    }
  }

  private void assertDayBValuesViaDuckDb(final DataFileResult result) throws SQLException {
    final Path physicalPath = LocalFileIO.toFilesystemPath(result.path());
    try (Connection duckdb = DriverManager.getConnection("jdbc:duckdb:");
        Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT instance_key, process_id, element_key, vars_json FROM read_parquet('"
                    + physicalPath
                    + "') ORDER BY instance_key")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong("instance_key")).isEqualTo(4L);
      assertThat(rs.getString("process_id")).isEqualTo("P2");
      assertThat(rs.getLong("element_key")).isEqualTo(40L);
      assertThat(blobBytes(rs, "vars_json")).isEqualTo(new byte[] {5, 6});

      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong("instance_key")).isEqualTo(5L);
      assertThat(rs.getString("process_id")).isEqualTo("P2");
      rs.getLong("element_key");
      assertThat(rs.wasNull()).isTrue();
      assertThat(blobBytes(rs, "vars_json")).isEqualTo(new byte[] {7, 8, 9, 10});

      assertThat(rs.next()).isFalse();
    }
  }

  private void assertMetrics(final DataFileResult result, final TableSchema schema) {
    assertThat(result.metrics().recordCount()).isEqualTo(result.rowCount());
    // LONG columns: instance_key (id 1), start_ms (id 2), element_key (id 4)
    for (final int longFieldId : new int[] {1, 2, 4}) {
      assertThat(result.metrics().lowerBounds())
          .as("lower bound present for field id %s", longFieldId)
          .containsKey(longFieldId);
      assertThat(result.metrics().upperBounds())
          .as("upper bound present for field id %s", longFieldId)
          .containsKey(longFieldId);
    }
  }

  /**
   * DuckDB's JDBC driver returns a {@link Blob} (not a plain {@code byte[]}, and {@code
   * ResultSet#getBytes} isn't implemented) for a Parquet BINARY column; unwraps that to compare raw
   * bytes directly, {@code null} when the column itself was null.
   */
  private static byte[] blobBytes(final ResultSet rs, final String column) throws SQLException {
    final Blob blob = rs.getBlob(column);
    return blob == null ? null : blob.getBytes(1, (int) blob.length());
  }

  @Test
  void shouldAlignRowGroupsToTargetSizeWhenAppendsMatchTarget() throws Exception {
    // given: two appends of exactly targetRowGroupRows rows each on the same open file
    final TableSchema schema = EncodeTestFixtures.instancesSchema();
    final Schema icebergSchema = EncodeTestFixtures.icebergSchema(schema);
    final LocalFileSink fileSink = new LocalFileSink(tempDir);
    final int targetRowGroupRows = 3;
    final IcebergParquetEncoderFactory factory =
        new IcebergParquetEncoderFactory(icebergSchema, fileSink, targetRowGroupRows, Set.of());
    final BatchEncoder encoder = factory.newFile(schema, 42L);

    final Object[][] columns = new Object[5][6];
    for (int i = 0; i < 6; i++) {
      columns[0][i] = (long) i;
      columns[1][i] = 1_000L + i;
      columns[2][i] = "P";
      columns[3][i] = (long) i;
      columns[4][i] = new byte[] {(byte) i};
    }
    final FakeSortedRun run =
        new FakeSortedRun(schema, columns, new long[] {42, 42, 42, 42, 42, 42});

    // when: two appends of 3 rows each
    encoder.append(run, 0, 3);
    encoder.append(run, 3, 6);
    final DataFileResult result = encoder.finish();

    // then
    assertThat(result.rowCount()).isEqualTo(6);
    final Path physicalPath = LocalFileIO.toFilesystemPath(result.path());
    final ParquetReadOptions options =
        ParquetReadOptions.builder(new PlainParquetConfiguration()).build();
    try (ParquetFileReader reader =
        ParquetFileReader.open(new LocalParquetInputFile(physicalPath), options)) {
      final ParquetMetadata footer = reader.getFooter();
      assertThat(footer.getBlocks()).hasSize(2);
      assertThat(footer.getBlocks().get(0).getRowCount()).isEqualTo(3);
      assertThat(footer.getBlocks().get(1).getRowCount()).isEqualTo(3);
    }
  }

  @Test
  void shouldLeaveNoFinishableResultAfterAbort() {
    // given
    final TableSchema schema = EncodeTestFixtures.instancesSchema();
    final Schema icebergSchema = EncodeTestFixtures.icebergSchema(schema);
    final LocalFileSink fileSink = new LocalFileSink(tempDir);
    final IcebergParquetEncoderFactory factory =
        new IcebergParquetEncoderFactory(icebergSchema, fileSink, 10, Set.of());
    final BatchEncoder encoder = factory.newFile(schema, 7L);

    final Object[][] columns = new Object[5][1];
    columns[0][0] = 1L;
    columns[1][0] = 1_000L;
    columns[2][0] = "P";
    columns[3][0] = 1L;
    columns[4][0] = new byte[] {1};
    final FakeSortedRun run = new FakeSortedRun(schema, columns, new long[] {7});

    // when
    encoder.append(run, 0, 1);
    encoder.abort();

    // then: no result is ever obtainable, and abort is idempotent / safe to repeat
    assertThatThrownBy(encoder::finish).isInstanceOf(IllegalStateException.class);
    encoder.abort();
  }

  @Test
  void shouldAllowAbortWithoutAnyAppend() {
    // given
    final TableSchema schema = EncodeTestFixtures.instancesSchema();
    final Schema icebergSchema = EncodeTestFixtures.icebergSchema(schema);
    final LocalFileSink fileSink = new LocalFileSink(tempDir);
    final IcebergParquetEncoderFactory factory =
        new IcebergParquetEncoderFactory(icebergSchema, fileSink, 10, Set.of());
    final BatchEncoder encoder = factory.newFile(schema, 3L);

    // when / then: abort right after opening, with no appends at all, does not throw
    encoder.abort();
    assertThatThrownBy(encoder::finish).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRoundTripTimestamptzValuesThroughDuckDb() throws Exception {
    // given a schema v2 column marked timestamptz (see TableSchema.Column#logicalType()): the
    // batch vector carries epoch MICROSECONDS, exactly what LakeTranslator's ×1000 conversion
    // produces, and the catalog field is Iceberg's timestamptz logical type
    final TableSchema schema =
        new TableSchema(
            "instances",
            List.of(
                new TableSchema.Column("key", ColumnType.LONG, 1, false, 0, false),
                new TableSchema.Column(
                    "started_at",
                    ColumnType.LONG,
                    2,
                    false,
                    -1,
                    true,
                    TableSchema.LogicalType.TIMESTAMPTZ)));
    final Schema icebergSchema =
        new Schema(
            List.of(
                Types.NestedField.required(1, "key", Types.LongType.get()),
                Types.NestedField.required(2, "started_at", Types.TimestampType.withZone())));
    final LocalFileSink fileSink = new LocalFileSink(tempDir);
    final IcebergParquetEncoderFactory factory =
        new IcebergParquetEncoderFactory(icebergSchema, fileSink, 10, Set.of());

    final Instant instantA = Instant.parse("2026-07-20T10:15:30.123456Z");
    final Instant instantB = Instant.parse("2026-07-20T11:00:00.000001Z");
    final long microsA = instantA.getEpochSecond() * 1_000_000L + instantA.getNano() / 1000;
    final long microsB = instantB.getEpochSecond() * 1_000_000L + instantB.getNano() / 1000;

    final Object[][] columns = new Object[2][2];
    columns[0] = new Object[] {1L, 2L};
    columns[1] = new Object[] {microsA, microsB};
    final FakeSortedRun run = new FakeSortedRun(schema, columns, new long[] {19193, 19193});

    // when: the encoder writes the sink's own Parquet file (the same rung the sink pipeline uses)
    final BatchEncoder encoder = factory.newFile(schema, 19193L);
    encoder.append(run, 0, 2);
    final DataFileResult result = encoder.finish();

    // then: DuckDB reads the file back with proper TIMESTAMP (WITH TIME ZONE) values equal to the
    // instants that were appended, not raw integers
    final Path physicalPath = LocalFileIO.toFilesystemPath(result.path());
    try (Connection duckdb = DriverManager.getConnection("jdbc:duckdb:");
        Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT key, started_at FROM read_parquet('" + physicalPath + "') ORDER BY key")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong("key")).isEqualTo(1L);
      assertThat(rs.getObject("started_at", OffsetDateTime.class).toInstant()).isEqualTo(instantA);

      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong("key")).isEqualTo(2L);
      assertThat(rs.getObject("started_at", OffsetDateTime.class).toInstant()).isEqualTo(instantB);

      assertThat(rs.next()).isFalse();
    }
  }

  @Test
  void shouldRoundTripDoubleValuesThroughDuckDb() throws Exception {
    // given a schema with a nullable DOUBLE column alongside its LONG family-day source
    final TableSchema schema =
        new TableSchema(
            "profiles",
            List.of(
                new TableSchema.Column("key", ColumnType.LONG, 1, false, 0, false),
                new TableSchema.Column("ts", ColumnType.LONG, 2, false, -1, true),
                new TableSchema.Column("value_sum", ColumnType.DOUBLE, 3, true, -1, false)));
    final Schema icebergSchema = EncodeTestFixtures.icebergSchema(schema);
    final LocalFileSink fileSink = new LocalFileSink(tempDir);
    final IcebergParquetEncoderFactory factory =
        new IcebergParquetEncoderFactory(icebergSchema, fileSink, 10, Set.of());

    final Object[][] columns = new Object[3][3];
    columns[0] = new Object[] {1L, 2L, 3L};
    columns[1] = new Object[] {1_000L, 1_000L, 1_000L};
    columns[2] = new Object[] {12.5, null, -0.001};
    final FakeSortedRun run = new FakeSortedRun(schema, columns, new long[] {5, 5, 5});

    // when
    final BatchEncoder encoder = factory.newFile(schema, 5L);
    encoder.append(run, 0, 3);
    final DataFileResult result = encoder.finish();

    // then: DuckDB reads the exact double values back, null preserved
    final Path physicalPath = LocalFileIO.toFilesystemPath(result.path());
    try (Connection duckdb = DriverManager.getConnection("jdbc:duckdb:");
        Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT key, value_sum FROM read_parquet('" + physicalPath + "') ORDER BY key")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong("key")).isEqualTo(1L);
      assertThat(rs.getDouble("value_sum")).isEqualTo(12.5);

      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong("key")).isEqualTo(2L);
      rs.getDouble("value_sum");
      assertThat(rs.wasNull()).isTrue();

      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong("key")).isEqualTo(3L);
      assertThat(rs.getDouble("value_sum")).isEqualTo(-0.001);

      assertThat(rs.next()).isFalse();
    }
  }
}

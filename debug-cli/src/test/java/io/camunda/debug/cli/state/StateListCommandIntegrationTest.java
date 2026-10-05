/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.debug.cli.Main;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.rocksdb.transaction.RawTransactionalColumnFamily;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransaction;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

/**
 * Covers what the command adds on top of the key formatter: reading a real snapshot, decoding
 * values, bounding the output, and rejecting bad input. Key layouts are covered by {@link
 * StateKeyFormatterTest}.
 */
class StateListCommandIntegrationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final byte[] MSGPACK_VALUE =
      MsgPackConverter.convertToMsgPack("{\"message\":\"failed\"}");

  @TempDir Path tempDir;

  private final StringWriter out = new StringWriter();
  private final StringWriter err = new StringWriter();
  private final CommandLine commandLine =
      new CommandLine(new Main()).setOut(new PrintWriter(out)).setErr(new PrintWriter(err));

  @Test
  void shouldListDecodedKeyAndMsgPackValue() throws Exception {
    // given
    final var snapshotId =
        takeSnapshot(entry(ZbColumnFamilies.INCIDENTS, longKey(42), MSGPACK_VALUE));

    // when
    final var exitCode = list(snapshotId, "INCIDENTS");

    // then
    assertThat(exitCode).isZero();
    final var json = OBJECT_MAPPER.readTree(out.toString());
    assertThat(json.path("snapshot").textValue()).isEqualTo(snapshotId);
    assertThat(json.path("columnFamily").textValue()).isEqualTo("INCIDENTS");
    assertThat(json.path("truncated").booleanValue()).isFalse();
    assertThat(json.path("entries")).hasSize(1);
    final var entry = json.path("entries").get(0);
    assertThat(entry.path("key").textValue()).isEqualTo("42");
    assertThat(entry.path("keyHex").textValue()).isEqualTo(hex(longKey(42)));
    assertThat(entry.path("value")).isEqualTo(OBJECT_MAPPER.readTree("{\"message\":\"failed\"}"));
    assertThat(entry.has("valueHex")).isFalse();
  }

  @ParameterizedTest
  @MethodSource("rawValues")
  void shouldExposeValuesThatAreNotMsgPackMapsAsHex(final byte[] value) throws Exception {
    // given
    final var snapshotId = takeSnapshot(entry(ZbColumnFamilies.INCIDENTS, longKey(1), value));

    // when
    list(snapshotId, "INCIDENTS");

    // then
    final var entry = firstEntry();
    assertThat(entry.path("value").isNull()).isTrue();
    assertThat(entry.path("valueHex").textValue()).isEqualTo(hex(value));
  }

  static Stream<Arguments> rawValues() {
    final var trailingPayload = Arrays.copyOf(MSGPACK_VALUE, MSGPACK_VALUE.length + 1);
    return Stream.of(
        Arguments.of((Object) valueBytes(longValue(42))),
        Arguments.of((Object) valueBytes(stringValue("migration"))),
        Arguments.of((Object) valueBytes(DbNil.INSTANCE)),
        Arguments.of((Object) trailingPayload));
  }

  @Test
  void shouldFallBackToHexForColumnFamiliesWithoutAKnownKeyLayout() throws Exception {
    // given
    final var key = new byte[] {1, 2, 3, 4};
    final var snapshotId = takeSnapshot(entry(ZbColumnFamilies.TENANTS, key, MSGPACK_VALUE));

    // when
    list(snapshotId, "TENANTS");

    // then
    final var entry = firstEntry();
    assertThat(entry.path("key").textValue()).isEqualTo("01 02 03 04");
    assertThat(entry.path("keyHex").textValue()).isEqualTo("01020304");
  }

  @Test
  void shouldApplyTheRequestedKeyFormat() throws Exception {
    // given
    final var snapshotId =
        takeSnapshot(entry(ZbColumnFamilies.INCIDENTS, longKey(42), MSGPACK_VALUE));

    // when
    list(snapshotId, "INCIDENTS", "--key-format", "hex");

    // then
    assertThat(firstEntry().path("key").textValue()).isEqualTo("00 00 00 00 00 00 00 2a");
  }

  @Test
  void shouldLimitEntriesAndReportTruncation() throws Exception {
    // given
    final var snapshotId =
        takeSnapshot(
            entry(ZbColumnFamilies.INCIDENTS, longKey(1), MSGPACK_VALUE),
            entry(ZbColumnFamilies.INCIDENTS, longKey(2), MSGPACK_VALUE));

    // when
    list(snapshotId, "INCIDENTS", "--limit", "1");

    // then
    final var json = OBJECT_MAPPER.readTree(out.toString());
    assertThat(json.path("entries")).hasSize(1);
    assertThat(json.path("truncated").booleanValue()).isTrue();
  }

  @Test
  void shouldNotReportTruncationWhenEntriesFitTheLimit() throws Exception {
    // given
    final var snapshotId =
        takeSnapshot(
            entry(ZbColumnFamilies.INCIDENTS, longKey(1), MSGPACK_VALUE),
            entry(ZbColumnFamilies.INCIDENTS, longKey(2), MSGPACK_VALUE));

    // when
    list(snapshotId, "INCIDENTS", "--limit", "2");

    // then
    final var json = OBJECT_MAPPER.readTree(out.toString());
    assertThat(json.path("entries")).hasSize(2);
    assertThat(json.path("truncated").booleanValue()).isFalse();
  }

  @Test
  void shouldOnlyListTheRequestedColumnFamily() throws Exception {
    // given
    final var snapshotId =
        takeSnapshot(
            entry(ZbColumnFamilies.INCIDENTS, longKey(1), MSGPACK_VALUE),
            entry(ZbColumnFamilies.JOBS, longKey(2), MSGPACK_VALUE));

    // when
    list(snapshotId, "JOBS");

    // then
    final var json = OBJECT_MAPPER.readTree(out.toString());
    assertThat(json.path("entries")).hasSize(1);
    assertThat(firstEntry().path("key").textValue()).isEqualTo("2");
  }

  @Test
  void shouldRejectUnknownColumnFamily() {
    // given
    final var snapshotId = takeSnapshot();

    // when
    final var exitCode = list(snapshotId, "NOT_A_COLUMN_FAMILY");

    // then
    assertThat(exitCode).isEqualTo(1);
    assertThat(err.toString()).contains("Unknown column family: NOT_A_COLUMN_FAMILY");
    assertThat(out.toString()).isEmpty();
  }

  @Test
  void shouldRejectNonPositiveLimit() {
    // given
    final var snapshotId = takeSnapshot();

    // when
    final var exitCode = list(snapshotId, "INCIDENTS", "--limit", "0");

    // then
    assertThat(exitCode).isEqualTo(1);
    assertThat(err.toString()).contains("--limit must be greater than 0");
    assertThat(out.toString()).isEmpty();
  }

  private int list(final String snapshotId, final String columnFamily, final String... extra) {
    final var args =
        Stream.concat(
                Stream.of(
                    "state",
                    "list",
                    "--root",
                    tempDir.resolve("partition").toString(),
                    "--snapshot",
                    snapshotId,
                    "--column-family",
                    columnFamily),
                Stream.of(extra))
            .toArray(String[]::new);
    return commandLine.execute(args);
  }

  private JsonNode firstEntry() throws Exception {
    return OBJECT_MAPPER.readTree(out.toString()).path("entries").get(0);
  }

  private String takeSnapshot(final Entry... entries) {
    try (final var db =
        SnapshotTestUtil.newDbFactory().createDb(tempDir.resolve("initial").toFile())) {
      final var context = db.createContext();
      context.runInTransaction(
          () -> {
            final var transaction = (ZeebeTransaction) context.getCurrentTransaction();
            for (final var entry : entries) {
              new RawTransactionalColumnFamily(db, entry.columnFamily())
                  .put(
                      transaction,
                      entry.key(),
                      entry.key().length,
                      entry.value(),
                      entry.value().length);
            }
          });
      return new SnapshotUtil()
          .takeSnapshot(db, tempDir.resolve("partition"), "1-1-1-1-1", 1L)
          .getId()
          .toString();
    }
  }

  private static Entry entry(
      final ZbColumnFamilies columnFamily, final byte[] key, final byte[] value) {
    return new Entry(columnFamily, key, value);
  }

  private static byte[] longKey(final long value) {
    return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
  }

  private static DbLong longValue(final long value) {
    final var result = new DbLong();
    result.wrapLong(value);
    return result;
  }

  private static DbString stringValue(final String value) {
    final var result = new DbString();
    result.wrapString(value);
    return result;
  }

  private static byte[] valueBytes(final DbValue value) {
    final var buffer = new UnsafeBuffer(new byte[value.getLength()]);
    value.write(buffer, 0);
    return buffer.byteArray();
  }

  private static String hex(final byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }

  private record Entry(ZbColumnFamilies columnFamily, byte[] key, byte[] value) {}
}

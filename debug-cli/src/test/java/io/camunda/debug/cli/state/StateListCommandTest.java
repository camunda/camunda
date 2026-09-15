/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.debug.cli.Main;
import io.camunda.zeebe.db.impl.rocksdb.transaction.RawTransactionalColumnFamily;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransaction;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StateListCommandTest {

  @TempDir Path tempDir;

  @Test
  void shouldListDecodedKeyAndMsgPackValue() {
    // given
    final var partitionRoot = tempDir.resolve("partition");
    try (final var db =
        SnapshotTestUtil.newDbFactory().createDb(tempDir.resolve("initial").toFile())) {
      final var context = db.createContext();
      final var columnFamily = new RawTransactionalColumnFamily(db, ZbColumnFamilies.INCIDENTS);
      final var key = ByteBuffer.allocate(Long.BYTES).putLong(42).array();
      final var value = MsgPackConverter.convertToMsgPack("{\"message\":\"failed\"}");
      context.runInTransaction(
          () ->
              columnFamily.put(
                  (ZeebeTransaction) context.getCurrentTransaction(),
                  key,
                  key.length,
                  value,
                  value.length));
      final var snapshot = new SnapshotUtil().takeSnapshot(db, partitionRoot, "1-1-1-1-1", 1L);
      final var output = new StringWriter();
      final var commandLine =
          new CommandLine(new Main())
              .setOut(new PrintWriter(output))
              .setErr(new PrintWriter(new StringWriter()));

      // when
      final var exitCode =
          commandLine.execute(
              "state",
              "list",
              "--root",
              partitionRoot.toString(),
              "--snapshot",
              snapshot.getId().toString(),
              "--column-family",
              "INCIDENTS");

      // then
      assertThat(exitCode).isZero();
      assertThat(output.toString())
          .contains("\"key\":\"42\"")
          .contains("\"value\":{\"message\":\"failed\"}")
          .contains("\"truncated\":false");
    }
  }

  @Test
  void shouldListAnEntryFromEveryColumnFamily() {
    // given
    final var partitionRoot = tempDir.resolve("all-column-families");
    final var key = ByteBuffer.allocate(Long.BYTES).putLong(42).array();
    final var value = MsgPackConverter.convertToMsgPack("{\"message\":\"value\"}");
    try (final var db =
        SnapshotTestUtil.newDbFactory()
            .createDb(tempDir.resolve("all-column-families-db").toFile())) {
      final var context = db.createContext();
      final Map<ZbColumnFamilies, RawTransactionalColumnFamily> columnFamilies =
          new EnumMap<>(ZbColumnFamilies.class);
      for (final var columnFamily : ZbColumnFamilies.values()) {
        columnFamilies.put(columnFamily, new RawTransactionalColumnFamily(db, columnFamily));
      }
      context.runInTransaction(
          () -> {
            final var transaction = (ZeebeTransaction) context.getCurrentTransaction();
            for (final var columnFamily : ZbColumnFamilies.values()) {
              final var rawColumnFamily = columnFamilies.get(columnFamily);
              rawColumnFamily.put(transaction, key, key.length, value, value.length);
            }
          });
      final var snapshot = new SnapshotUtil().takeSnapshot(db, partitionRoot, "1-1-1-1-1", 1L);

      // when / then
      for (final var columnFamily : ZbColumnFamilies.values()) {
        final var output = new StringWriter();
        final var commandLine =
            new CommandLine(new Main())
                .setOut(new PrintWriter(output))
                .setErr(new PrintWriter(new StringWriter()));
        final var exitCode =
            commandLine.execute(
                "state",
                "list",
                "--root",
                partitionRoot.toString(),
                "--snapshot",
                snapshot.getId().toString(),
                "--column-family",
                columnFamily.name(),
                "--key-format",
                "hex",
                "--limit",
                "1");

        assertThat(exitCode).isZero();
        assertThat(output.toString())
            .contains("\"columnFamily\":\"" + columnFamily.name() + "\"")
            .contains("\"keyHex\":\"000000000000002a\"")
            .contains("\"value\":{\"message\":\"value\"}")
            .contains("\"truncated\":false");
      }
    }
  }
}

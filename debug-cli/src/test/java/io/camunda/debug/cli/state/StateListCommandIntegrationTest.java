/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.debug.cli.Main;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbByte;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.rocksdb.transaction.RawTransactionalColumnFamily;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransaction;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StateListCommandIntegrationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
      assertThat(output.toString()).endsWith(System.lineSeparator());
    }
  }

  @Test
  void shouldExposeRawDatabaseValuesAsHex() throws Exception {
    // given
    final var partitionRoot = tempDir.resolve("raw-values");
    final var longValue = valueBytes(number(new DbLong(), 42));
    final var nilValue = valueBytes(DbNil.INSTANCE);
    final var stringValue = valueBytes(string("migration"));
    try (final var db =
        SnapshotTestUtil.newDbFactory().createDb(tempDir.resolve("raw-values-db").toFile())) {
      final var context = db.createContext();
      context.runInTransaction(
          () -> {
            final var transaction = (ZeebeTransaction) context.getCurrentTransaction();
            putRaw(db, transaction, ZbColumnFamilies.KEY, keyFor("s"), longValue);
            putRaw(db, transaction, ZbColumnFamilies.MESSAGE_IDS, keyFor("ssss"), nilValue);
            putRaw(db, transaction, ZbColumnFamilies.MIGRATIONS_STATE, keyFor("s"), stringValue);
          });
      final var snapshot = new SnapshotUtil().takeSnapshot(db, partitionRoot, "1-1-1-1-1", 1L);

      // when / then
      assertRawValue(partitionRoot, snapshot.getId().toString(), ZbColumnFamilies.KEY, longValue);
      assertRawValue(
          partitionRoot, snapshot.getId().toString(), ZbColumnFamilies.MESSAGE_IDS, nilValue);
      assertRawValue(
          partitionRoot,
          snapshot.getId().toString(),
          ZbColumnFamilies.MIGRATIONS_STATE,
          stringValue);
    }
  }

  @Test
  void shouldExposeMessagePackWithTrailingPayloadAsHex() throws Exception {
    // given
    final var partitionRoot = tempDir.resolve("trailing-payload");
    final var messagePack = MsgPackConverter.convertToMsgPack("{\"message\":\"value\"}");
    final var value = Arrays.copyOf(messagePack, messagePack.length + 1);
    try (final var db =
        SnapshotTestUtil.newDbFactory().createDb(tempDir.resolve("trailing-payload-db").toFile())) {
      final var context = db.createContext();
      context.runInTransaction(
          () ->
              putRaw(
                  db,
                  (ZeebeTransaction) context.getCurrentTransaction(),
                  ZbColumnFamilies.INCIDENTS,
                  keyFor("l"),
                  value));
      final var snapshot = new SnapshotUtil().takeSnapshot(db, partitionRoot, "1-1-1-1-1", 1L);

      // when
      final var output =
          executeList(partitionRoot, snapshot.getId().toString(), ZbColumnFamilies.INCIDENTS);

      // then
      final var entry = OBJECT_MAPPER.readTree(output).path("entries").get(0);
      assertThat(entry.path("value").isNull()).isTrue();
      assertThat(entry.path("valueHex").textValue()).isEqualTo(HexFormat.of().formatHex(value));
    }
  }

  @Test
  void shouldLimitEntriesAndReportTruncation() throws Exception {
    // given
    final var partitionRoot = tempDir.resolve("limited");
    final var value = MsgPackConverter.convertToMsgPack("{\"message\":\"value\"}");
    try (final var db =
        SnapshotTestUtil.newDbFactory().createDb(tempDir.resolve("limited-db").toFile())) {
      final var context = db.createContext();
      context.runInTransaction(
          () -> {
            final var transaction = (ZeebeTransaction) context.getCurrentTransaction();
            putRaw(db, transaction, ZbColumnFamilies.INCIDENTS, longKey(1), value);
            putRaw(db, transaction, ZbColumnFamilies.INCIDENTS, longKey(2), value);
          });
      final var snapshot = new SnapshotUtil().takeSnapshot(db, partitionRoot, "1-1-1-1-1", 1L);

      // when
      final var output =
          executeList(partitionRoot, snapshot.getId().toString(), ZbColumnFamilies.INCIDENTS);

      // then
      final var json = OBJECT_MAPPER.readTree(output);
      assertThat(json.path("entries")).hasSize(1);
      assertThat(json.path("truncated").booleanValue()).isTrue();
    }
  }

  @Test
  void shouldListAnEntryFromEveryColumnFamilyUsingItsDefaultKeyFormat() {
    // given
    final var partitionRoot = tempDir.resolve("all-column-families");
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
              final var key = keyFor(expectedFormatFor(columnFamily));
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
                "--limit",
                "1");

        final var format = expectedFormatFor(columnFamily);
        assertThat(exitCode).isZero();
        assertThat(output.toString())
            .contains("\"columnFamily\":\"" + columnFamily.name() + "\"")
            .contains("\"value\":{\"message\":\"value\"}")
            .contains("\"truncated\":false");
        if (format == null) {
          assertThat(output.toString()).contains("\"keyHex\":\"01020304\"");
        } else {
          assertThat(output.toString())
              .contains("\"key\":\"" + formattedKey(format) + "\"")
              .contains("\"keyHex\":\"" + HexFormat.of().formatHex(keyFor(format)) + "\"");
        }
      }
    }
  }

  private void assertRawValue(
      final Path partitionRoot,
      final String snapshotId,
      final ZbColumnFamilies columnFamily,
      final byte[] expectedValue)
      throws Exception {
    final var output = executeList(partitionRoot, snapshotId, columnFamily);
    final var entry = OBJECT_MAPPER.readTree(output).path("entries").get(0);
    assertThat(entry.path("value").isNull()).isTrue();
    assertThat(entry.path("valueHex").textValue())
        .isEqualTo(HexFormat.of().formatHex(expectedValue));
  }

  private String executeList(
      final Path partitionRoot, final String snapshotId, final ZbColumnFamilies columnFamily) {
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
            snapshotId,
            "--column-family",
            columnFamily.name(),
            "--limit",
            "1");
    assertThat(exitCode).isZero();
    return output.toString();
  }

  private static void putRaw(
      final ZeebeTransactionDb<ZbColumnFamilies> db,
      final ZeebeTransaction transaction,
      final ZbColumnFamilies columnFamily,
      final byte[] key,
      final byte[] value)
      throws Exception {
    final var rawColumnFamily = new RawTransactionalColumnFamily(db, columnFamily);
    rawColumnFamily.put(transaction, key, key.length, value, value.length);
  }

  private static byte[] valueBytes(final DbValue value) {
    final var buffer = new UnsafeBuffer(new byte[value.getLength()]);
    value.write(buffer, 0);
    return buffer.byteArray();
  }

  private static byte[] longKey(final long value) {
    return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
  }

  private static String expectedFormatFor(final ZbColumnFamilies columnFamily) {
    return switch (columnFamily) {
      case DEFAULT, KEY, EXPORTER, MIGRATIONS_STATE, MESSAGE_STATS, ROLES, CLAIM_BY_ID -> "s";
      case ELEMENT_INSTANCE_PARENT_CHILD,
          TIMERS,
          JOB_DEADLINES,
          EVENT_TRIGGER,
          JOB_BACKOFF,
          PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY,
          MESSAGE_DEADLINES ->
          "ll";
      case ELEMENT_INSTANCE_KEY,
          ELEMENT_INSTANCE_CHILD_PARENT,
          DEPLOYMENT_RAW,
          JOBS,
          JOB_STATES,
          MESSAGE_KEY,
          INCIDENTS,
          INCIDENT_PROCESS_INSTANCES,
          INCIDENT_JOBS,
          EVENT_SCOPE,
          BANNED_INSTANCE,
          MESSAGE_PROCESS_INSTANCE_CORRELATION_KEYS,
          AWAIT_WORKLOW_RESULT,
          COMMAND_DISTRIBUTION_RECORD,
          USER_TASKS,
          USER_TASK_STATES,
          AUTHORIZATIONS ->
          "l";
      case VARIABLES, MESSAGE_CORRELATED, MESSAGE_SUBSCRIPTION_BY_KEY -> "ls";
      case TIMER_DUE_DATES -> "lll";
      case PENDING_DEPLOYMENT, PENDING_DISTRIBUTION -> "li";
      case MESSAGE_IDS -> "ssss";
      case MESSAGE_PROCESSES_ACTIVE_BY_CORRELATION_KEY,
          PROCESS_VERSION,
          PROCESS_CACHE_DIGEST_BY_ID,
          FORM_VERSION,
          MAPPING_RULES,
          DMN_LATEST_DECISION_BY_ID,
          DMN_LATEST_DECISION_REQUIREMENTS_BY_ID,
          AUTHORIZATION_KEYS_BY_OWNER ->
          "ss";
      case PROCESS_CACHE, FORMS, DMN_DECISIONS, DMN_DECISION_REQUIREMENTS -> "sl";
      case PROCESS_CACHE_BY_ID_AND_VERSION, FORM_BY_ID_AND_VERSION -> "ssl";
      case MESSAGES, MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY -> "sssl";
      case MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY, SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY ->
          "ssl";
      case MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME,
          SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME,
          PROCESS_SUBSCRIPTION_BY_KEY,
          NUMBER_OF_TAKEN_SEQUENCE_FLOWS ->
          "lss";
      case JOB_ACTIVATABLE -> "sls";
      case DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY -> "slsl";
      case DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
          DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION ->
          "ssi";
      case COMPENSATION_SUBSCRIPTION -> "sll";
      case ENTITIES_BY_RELATION, RELATIONS_BY_ENTITY -> "bsbs";
      case PERMISSIONS -> "sss";
      case USAGE_METRICS -> "b";
      case DEPRECATED_PROCESS_VERSION,
          DEPRECATED_PROCESS_CACHE,
          DEPRECATED_PROCESS_CACHE_BY_ID_AND_VERSION,
          DEPRECATED_PROCESS_CACHE_DIGEST_BY_ID,
          TEMPORARY_VARIABLE_STORE,
          DEPRECATED_JOB_ACTIVATABLE,
          DEPRECATED_MESSAGES,
          MESSAGE_SUBSCRIPTION_BY_SENT_TIME,
          DEPRECATED_MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY,
          DEPRECATED_MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
          DEPRECATED_MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME,
          DEPRECATED_PROCESS_SUBSCRIPTION_BY_KEY,
          PROCESS_SUBSCRIPTION_BY_SENT_TIME,
          DEPRECATED_DMN_DECISIONS,
          DEPRECATED_DMN_DECISION_REQUIREMENTS,
          DEPRECATED_DMN_LATEST_DECISION_BY_ID,
          DEPRECATED_DMN_LATEST_DECISION_REQUIREMENTS_BY_ID,
          DEPRECATED_DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION,
          DEPRECATED_DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
          DEPRECATED_DMN_DECISION_KEY_BY_DECISION_REQUIREMENTS_KEY,
          DEPRECATED_SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY,
          DEPRECATED_SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME,
          PROCESS_DEFINITION_KEY_BY_PROCESS_ID_AND_DEPLOYMENT_KEY,
          DMN_DECISION_KEY_BY_DECISION_ID_AND_DEPLOYMENT_KEY,
          FORM_KEY_BY_FORM_ID_AND_DEPLOYMENT_KEY,
          MESSAGE_CORRELATION,
          USERS,
          USER_KEY_BY_USERNAME,
          CLOCK,
          PROCESS_DEFINITION_KEY_BY_PROCESS_ID_AND_VERSION_TAG,
          DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION_TAG,
          FORM_KEY_BY_FORM_ID_AND_VERSION_TAG,
          AUTHORIZATION_KEY_BY_RESOURCE_ID,
          OWNER_TYPE_BY_OWNER_KEY,
          ROUTING,
          QUEUED_DISTRIBUTION,
          RETRIABLE_DISTRIBUTION,
          DISTRIBUTION_CONTINUATION,
          RESOURCES,
          RESOURCE_VERSION,
          RESOURCE_BY_ID_AND_VERSION,
          RESOURCE_KEY_BY_RESOURCE_ID_AND_VERSION_TAG,
          RESOURCE_KEY_BY_RESOURCE_ID_AND_DEPLOYMENT_KEY,
          TENANTS,
          USER_TASK_INTERMEDIATE_STATES,
          ASYNC_REQUEST_METADATA,
          GROUPS,
          REDISTRIBUTION,
          USERNAME_BY_USER_KEY,
          BATCH_OPERATION,
          PENDING_BATCH_OPERATION,
          BATCH_OPERATION_CHUNKS,
          VARIABLE_DOCUMENT_STATE_BY_SCOPE_KEY,
          USER_TASK_INITIAL_ASSIGNEE,
          SCALING_STARTED_AT,
          RUNTIME_INSTRUCTIONS,
          MULTI_INSTANCE_INPUT_COLLECTION,
          CLUSTER_VARIABLES,
          CONDITIONAL_SUBSCRIPTION_BY_SUBSCRIPTION_KEY,
          CONDITIONAL_SUBSCRIPTION_BY_SCOPE_KEY,
          CONDITIONAL_SUBSCRIPTION_BY_PROCESS_DEFINITION_KEY,
          GLOBAL_LISTENERS,
          GLOBAL_LISTENER_CURRENT_CONFIG,
          GLOBAL_LISTENER_VERSIONED_CONFIG,
          GLOBAL_LISTENER_PINNED_CONFIG,
          CONDITIONAL_SUBSCRIPTION_PROCESS_INSTANCE_COUNT,
          JOB_METRICS,
          JOB_METRICS_STRING_ENCODING,
          JOB_METRICS_META,
          CONDITIONAL_SUBSCRIPTION_BY_TENANT_ID,
          PROCESS_INSTANCE_KEY_BY_BUSINESS_ID,
          CHECKPOINTS,
          BACKUP_RANGES,
          ACTIVE_PROCESS_INSTANCE_COUNT,
          AGENT_INSTANCES,
          COMMAND_DISTRIBUTION_METADATA,
          CROSS_PARTITION_MESSAGE_START_DEDUP,
          CROSS_PARTITION_MESSAGE_START_ASK,
          CROSS_PARTITION_MESSAGE_START_LOCK,
          JOB_ACTIVATABLE_BY_PRIORITY,
          MESSAGE_BY_BUSINESS_ID,
          AGENT_HISTORY,
          AGENT_HISTORY_BY_JOB_KEY,
          AGENT_INSTANCES_BY_PROCESS_INSTANCE_KEY,
          PENDING_SECRET_REFERENCES,
          SECRET_REFERENCES_BY_JOB,
          JOBS_BY_SECRET_REFERENCE,
          SUSPENDED_PROCESS_INSTANCES,
          BUFFERED_PROCESS_INSTANCE_COMMANDS,
          BUFFERED_PROCESS_INSTANCE_COMMANDS_BY_PROCESS_INSTANCE_KEY,
          PENDING_PROCESS_DELETIONS_PER_PARTITION,
          CROSS_PARTITION_MESSAGE_START_HOLDER_ORIGIN,
          AGENT_DEFINITION_KEY_BY_PROCESS_DEFINITION_KEY_AND_ELEMENT_ID,
          AGENT_DEFINITION_BY_KEY,
          JOBS_BY_PROCESS_INSTANCE,
          AGENT_HISTORY_COMMITTED_IDS,
          AGENT_HISTORY_METRICS_ACCUMULATED_IDS ->
          null;
    };
  }

  private static byte[] keyFor(final String format) {
    if (format == null) {
      return new byte[] {1, 2, 3, 4};
    }

    final var values =
        format.chars().mapToObj(StateListCommandIntegrationTest::valueFor).toArray(DbValue[]::new);
    final var key = new UnsafeBuffer(new byte[valuesLength(values)]);
    var offset = 0;
    for (final var value : values) {
      offset += value.write(key, offset);
    }
    return key.byteArray();
  }

  private static int valuesLength(final DbValue[] values) {
    var length = 0;
    for (final var value : values) {
      length += value.getLength();
    }
    return length;
  }

  private static DbValue valueFor(final int format) {
    return switch (format) {
      case 's' -> string("state");
      case 'l' -> number(new DbLong(), 42L);
      case 'i' -> number(new DbInt(), 7);
      case 'b' -> number(new DbByte(), (byte) 3);
      case 'B' -> bytes(new byte[] {1, 2, 3});
      default -> throw new IllegalArgumentException("Unexpected key format: " + (char) format);
    };
  }

  private static String formattedKey(final String format) {
    return format
        .chars()
        .mapToObj(
            value ->
                switch (value) {
                  case 's' -> "state";
                  case 'l' -> "42";
                  case 'i' -> "7";
                  case 'b' -> "3";
                  case 'B' -> "01 02 03";
                  default ->
                      throw new IllegalArgumentException("Unexpected key format: " + (char) value);
                })
        .reduce((left, right) -> left + ":" + right)
        .orElseThrow();
  }

  private static DbString string(final String value) {
    final var result = new DbString();
    result.wrapString(value);
    return result;
  }

  private static <T extends DbValue> T number(final T value, final long number) {
    switch (value) {
      case DbLong longValue -> longValue.wrapLong(number);
      case DbInt intValue -> intValue.wrapInt((int) number);
      case DbByte byteValue -> byteValue.wrapByte((byte) number);
      default -> throw new IllegalArgumentException("Unexpected numeric value");
    }
    return value;
  }

  private static DbBytes bytes(final byte[] value) {
    final var result = new DbBytes();
    result.wrapBytes(value);
    return result;
  }
}

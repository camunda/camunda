/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import static io.camunda.zeebe.db.rdbms.KeyLayout.bytes;
import static io.camunda.zeebe.db.rdbms.KeyLayout.dbByte;
import static io.camunda.zeebe.db.rdbms.KeyLayout.dbInt;
import static io.camunda.zeebe.db.rdbms.KeyLayout.dbLong;
import static io.camunda.zeebe.db.rdbms.KeyLayout.dbShort;
import static io.camunda.zeebe.db.rdbms.KeyLayout.dbString;
import static io.camunda.zeebe.db.rdbms.KeyLayout.tenantId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.impl.DbByte;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbShort;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.db.rdbms.KeyLayout.Part;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.agrona.ExpandableArrayBuffer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Writes keys with the engine's own key types and reads their parts back through the views, which
 * decode them with each database's built-in functions.
 */
abstract class StateViewsTestBase {

  private final int partitionId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);

  abstract DataSource dataSource();

  abstract StateViews views();

  @BeforeEach
  void createSchema() {
    TestStorage.createTables(dataSource(), "");
  }

  @Test
  void shouldShowEveryKindOfPart() throws SQLException {
    // given
    final var layout =
        new KeyLayout(
            List.of(
                dbLong("A_LONG"),
                dbInt("AN_INT"),
                dbShort("A_SHORT"),
                dbByte("A_BYTE"),
                dbString("A_STRING"),
                bytes("SOME_BYTES", 3),
                tenantId()));
    final var view = createView(layout);
    final var samples = samples(layout);

    // when
    insert("ZS_DEFAULT", key(samples.stream().map(Sample::key)));

    // then
    assertThat(read(view)).singleElement().usingRecursiveComparison().isEqualTo(expected(samples));
  }

  @TestFactory
  Stream<DynamicTest> shouldShowThePartsOfEveryDescribedKey() {
    return StateTables.all().stream()
        .filter(table -> table.keyLayout() != null)
        .map(
            table ->
                DynamicTest.dynamicTest(
                    table.viewName(),
                    () -> {
                      // given
                      final var samples = samples(Objects.requireNonNull(table.keyLayout()));

                      // when
                      insert(table.name(), key(samples.stream().map(Sample::key)));

                      // then
                      assertThat(read(table.viewName()))
                          .singleElement()
                          .usingRecursiveComparison()
                          .isEqualTo(expected(samples));
                    }));
  }

  @Test
  void shouldShowTheKeyOfTheEngine() throws SQLException {
    // given
    final var jobType = new DbString();
    jobType.wrapString("payment");
    final var invertedPriority = new DbInt();
    invertedPriority.wrapInt(Integer.MAX_VALUE - 5);
    final var jobKey = new DbLong();
    jobKey.wrapLong(2251799813685249L);
    final var tenant = new DbString();
    tenant.wrapString("<default>");
    final var key =
        new DbTenantAwareKey<>(
            tenant,
            new DbCompositeKey<>(
                jobType,
                new DbCompositeKey<>(
                    invertedPriority, new DbForeignKey<>(jobKey, ZbColumnFamilies.JOBS))),
            PlacementType.SUFFIX);
    final var table = StateTables.of(ZbColumnFamilies.JOB_ACTIVATABLE_BY_PRIORITY.getValue());

    // when
    insert(table.name(), key(Stream.of(key)));

    // then
    assertThat(read(table.viewName()))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("JOB_TYPE")).isEqualTo("payment");
              assertThat((byte[]) row.get("INVERTED_PRIORITY"))
                  .containsExactly(0x7f, 0xff, 0xff, 0xfa);
              assertThat(row.get("JOB_KEY")).isEqualTo(2251799813685249L);
              assertThat(row.get(KeyLayout.TENANT_ID)).isEqualTo("<default>");
            });
  }

  @Test
  void shouldShowTheTenantBeforeTheKeyOfTheEngine() throws SQLException {
    // given
    final var tenant = new DbString();
    tenant.wrapString("tenant-a");
    final var messageName = new DbString();
    messageName.wrapString("order-paid");
    final var correlationKey = new DbString();
    correlationKey.wrapString("order-42");
    final var elementInstanceKey = new DbLong();
    elementInstanceKey.wrapLong(2251799813685250L);
    final var key =
        new DbCompositeKey<>(
            new DbTenantAwareKey<>(
                tenant, new DbCompositeKey<>(messageName, correlationKey), PlacementType.PREFIX),
            elementInstanceKey);
    final var table =
        StateTables.of(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY.getValue());

    // when
    insert(table.name(), key(Stream.of(key)));

    // then
    assertThat(read(table.viewName()))
        .singleElement()
        .isEqualTo(
            Map.of(
                KeyLayout.TENANT_ID,
                "tenant-a",
                "MESSAGE_NAME",
                "order-paid",
                "CORRELATION_KEY",
                "order-42",
                "ELEMENT_INSTANCE_KEY",
                2251799813685250L));
  }

  @Test
  void shouldShowNullForPartsOutsideTheKey() throws SQLException {
    // given
    final var view = createView(new KeyLayout(List.of(dbString("NAME"), dbString("OTHER"))));
    final var name = new DbString();
    name.wrapString("abc");
    final var longerThanTheKey = new DbInt();
    longerThanTheKey.wrapInt(1_000);
    final var tooShortForALength = new DbShort();
    tooShortForALength.wrapShort((short) 3);

    // when
    insert("ZS_DEFAULT", key(Stream.of(name)));
    insert("ZS_DEFAULT", key(Stream.of(longerThanTheKey, name)));
    insert("ZS_DEFAULT", key(Stream.of(tooShortForALength)));

    // then
    final var onlyName = new HashMap<String, @Nullable Object>();
    onlyName.put("NAME", "abc");
    onlyName.put("OTHER", null);
    final var nothing = new HashMap<String, @Nullable Object>();
    nothing.put("NAME", null);
    nothing.put("OTHER", null);
    assertThat(read(view)).containsExactlyInAnyOrder(onlyName, nothing, nothing);
  }

  private String createView(final KeyLayout layout) throws SQLException {
    final var view = "ZSV_TEST_" + partitionId;
    execute(views().createView(view, "ZS_DEFAULT", layout));
    return view;
  }

  private void insert(final String table, final byte[] key) throws SQLException {
    try (final var connection = dataSource().getConnection();
        final var statement =
            connection.prepareStatement(
                "INSERT INTO "
                    + table
                    + " (PARTITION_ID, STATE_KEY, STATE_VALUE, UPDATED_POSITION)"
                    + " VALUES (?, ?, ?, ?)")) {
      statement.setInt(1, partitionId);
      statement.setBytes(2, key);
      statement.setBytes(3, new byte[0]);
      statement.setLong(4, 1L);
      statement.executeUpdate();
    }
  }

  /** Reads this partition's rows from the view, with only the columns of the key's parts. */
  private List<Map<String, @Nullable Object>> read(final String view) throws SQLException {
    try (final var connection = dataSource().getConnection();
        final var statement =
            connection.prepareStatement("SELECT * FROM " + view + " WHERE PARTITION_ID = ?")) {
      statement.setInt(1, partitionId);
      try (final var rows = statement.executeQuery()) {
        final var read = new ArrayList<Map<String, @Nullable Object>>();
        while (rows.next()) {
          read.add(partsOf(rows));
        }
        return read;
      }
    }
  }

  private void execute(final String sql) throws SQLException {
    try (final var connection = dataSource().getConnection();
        final var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static Map<String, @Nullable Object> partsOf(final ResultSet row) throws SQLException {
    final var parts = new HashMap<String, @Nullable Object>();
    final var columns = row.getMetaData();
    for (int column = 1; column <= columns.getColumnCount(); column++) {
      final var name = columns.getColumnLabel(column).toUpperCase(Locale.ROOT);
      if (List.of("PARTITION_ID", "UPDATED_POSITION", "STATE_KEY", "STATE_VALUE").contains(name)) {
        continue;
      }
      final var value = row.getObject(column);
      // the databases differ in the Java types of small numbers
      parts.put(name, value instanceof final Number number ? number.longValue() : value);
    }
    return parts;
  }

  /** Writes the keys one after the other, as {@link DbCompositeKey} does. */
  private static byte[] key(final Stream<DbKey> parts) {
    final var buffer = new ExpandableArrayBuffer();
    var length = 0;
    for (final var part : parts.toList()) {
      part.write(buffer, length);
      length += part.getLength();
    }
    return Arrays.copyOf(buffer.byteArray(), length);
  }

  private static Map<String, @Nullable Object> expected(final List<Sample> samples) {
    final var expected = new HashMap<String, @Nullable Object>();
    for (final var sample : samples) {
      expected.put(sample.part().column(), sample.value());
    }
    return expected;
  }

  /** Gives each part a value, negative where it's a number, and the key type that writes it. */
  private static List<Sample> samples(final KeyLayout layout) {
    final var samples = new ArrayList<Sample>();
    for (int index = 0; index < layout.parts().size(); index++) {
      final var part = layout.parts().get(index);
      final long number = -(index + 1);
      samples.add(
          switch (part.type()) {
            case LONG -> {
              final long value = number * 1_000_000_007_000L;
              final var key = new DbLong();
              key.wrapLong(value);
              yield new Sample(part, key, value);
            }
            case INT -> {
              final int value = (int) number * 1_000_003;
              final var key = new DbInt();
              key.wrapInt(value);
              yield new Sample(part, key, (long) value);
            }
            case SHORT -> {
              final short value = (short) (number * 1_001);
              final var key = new DbShort();
              key.wrapShort(value);
              yield new Sample(part, key, (long) value);
            }
            case BYTE -> {
              final byte value = (byte) number;
              final var key = new DbByte();
              key.wrapByte(value);
              yield new Sample(part, key, (long) value);
            }
            case STRING -> {
              final var value = "größe-" + index + "-✓";
              final var key = new DbString();
              key.wrapString(value);
              yield new Sample(part, key, value);
            }
            case BYTES -> {
              final var value = new byte[part.length()];
              Arrays.fill(value, (byte) (0x80 + index));
              final var key = new DbBytes();
              key.wrapBytes(value);
              yield new Sample(part, key, value);
            }
          });
    }
    return samples;
  }

  private record Sample(Part part, DbKey key, Object value) {}
}

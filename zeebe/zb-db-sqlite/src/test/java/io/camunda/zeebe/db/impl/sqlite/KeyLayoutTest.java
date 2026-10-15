/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.impl.DbByte;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbShort;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import org.junit.jupiter.api.Test;

/** Pins the DDL generated for representative key shapes. */
final class KeyLayoutTest {

  @Test
  void shouldMapLongToOrderExactBlob() {
    // when
    final var ddl = KeyLayout.of(new DbLong()).createTable("cf_long");

    // then: a long may be negative, which sorts differently as INTEGER than as raw bytes
    assertThat(ddl)
        .isEqualTo(
            """
            CREATE TABLE IF NOT EXISTS cf_long (
              k0 BLOB NOT NULL,
              v BLOB NOT NULL,
              PRIMARY KEY (k0)
            ) WITHOUT ROWID""");
  }

  @Test
  void shouldMapOtherNumericsToBlobByDefault() {
    // given
    final var key =
        new DbCompositeKey<>(
            new DbCompositeKey<>(new DbInt(), new DbShort()),
            new DbCompositeKey<>(new DbByte(), new DbLong()));

    // when
    final var layout = KeyLayout.of(key);

    // then
    assertThat(layout.columnNames()).containsExactly("k0", "k1", "k2", "k3");
    assertThat(layout.createTable("t"))
        .contains("k0 BLOB NOT NULL", "k1 BLOB NOT NULL", "k2 BLOB NOT NULL", "k3 BLOB NOT NULL");
  }

  @Test
  void shouldMapNonNegativeNumericToInteger() {
    // given a foreign key, which declares its numeric components non-negative
    final var key = new DbForeignKey<>(new DbLong(), DefaultColumnFamily.DEFAULT);

    // when
    final var ddl = KeyLayout.of(key).createTable("cf_fk");

    // then
    assertThat(ddl)
        .isEqualTo(
            """
            CREATE TABLE IF NOT EXISTS cf_fk (
              k0 INTEGER NOT NULL,
              v BLOB NOT NULL,
              PRIMARY KEY (k0)
            ) WITHOUT ROWID""");
  }

  @Test
  void shouldMapStringToBlobIncludingLengthPrefix() {
    // given
    final var layout = KeyLayout.of(new DbString());

    // then: not TEXT, which would order by content instead of by length first
    assertThat(layout.createTable("cf_string")).contains("k0 BLOB NOT NULL");
  }

  @Test
  void shouldMapCompositeKeyToOneColumnPerComponent() {
    // when
    final var ddl =
        KeyLayout.of(new DbCompositeKey<>(new DbString(), new DbLong())).createTable("cf_pair");

    // then
    assertThat(ddl)
        .isEqualTo(
            """
            CREATE TABLE IF NOT EXISTS cf_pair (
              k0 BLOB NOT NULL,
              k1 BLOB NOT NULL,
              v BLOB NOT NULL,
              PRIMARY KEY (k0, k1)
            ) WITHOUT ROWID""");
  }

  @Test
  void shouldKeepSerializationOrderForTenantAwarePrefix() {
    // given the tenant is serialized first
    final var key =
        new DbTenantAwareKey<>(
            new DbString(),
            new DbForeignKey<>(new DbLong(), DefaultColumnFamily.DEFAULT),
            PlacementType.PREFIX);

    // when
    final var layout = KeyLayout.of(key);

    // then
    assertThat(layout.createTable("cf_prefix"))
        .isEqualTo(
            """
            CREATE TABLE IF NOT EXISTS cf_prefix (
              k0 BLOB NOT NULL,
              k1 INTEGER NOT NULL,
              v BLOB NOT NULL,
              PRIMARY KEY (k0, k1)
            ) WITHOUT ROWID""");
  }

  @Test
  void shouldKeepSerializationOrderForTenantAwareSuffix() {
    // given the tenant is serialized last
    final var key =
        new DbTenantAwareKey<>(
            new DbString(),
            new DbForeignKey<>(new DbLong(), DefaultColumnFamily.DEFAULT),
            PlacementType.SUFFIX);

    // when
    final var layout = KeyLayout.of(key);

    // then
    assertThat(layout.createTable("cf_suffix"))
        .isEqualTo(
            """
            CREATE TABLE IF NOT EXISTS cf_suffix (
              k0 INTEGER NOT NULL,
              k1 BLOB NOT NULL,
              v BLOB NOT NULL,
              PRIMARY KEY (k0, k1)
            ) WITHOUT ROWID""");
  }

  @Test
  void shouldMapEnumOrdinalToInteger() {
    // given
    final var key = new DbCompositeKey<>(new DbEnumValue<>(Scope.class), new DbBytes());

    // when
    final var ddl = KeyLayout.of(key).createTable("cf_enum");

    // then
    assertThat(ddl)
        .isEqualTo(
            """
            CREATE TABLE IF NOT EXISTS cf_enum (
              k0 INTEGER NOT NULL,
              k1 BLOB NOT NULL,
              v BLOB NOT NULL,
              PRIMARY KEY (k0, k1)
            ) WITHOUT ROWID""");
  }

  @Test
  void shouldUseSentinelColumnForOmittableComponentAfterDiscriminator() {
    // given a leading enum ordinal that discriminates whether the string is present
    final var key = new ScopeKey();

    // when
    final var layout = KeyLayout.of(key);

    // then
    assertThat(layout.isOpaque()).isFalse();
    assertThat(layout.createTable("cf_scoped"))
        .isEqualTo(
            """
            CREATE TABLE IF NOT EXISTS cf_scoped (
              k0 INTEGER NOT NULL,
              k1_scopekey BLOB NOT NULL,
              v BLOB NOT NULL,
              PRIMARY KEY (k0, k1_scopekey)
            ) WITHOUT ROWID""");
  }

  @Test
  void shouldFallBackToOpaqueKeyForOmittableComponentWithoutDiscriminator() {
    // given an omittable string that nothing precedes
    final var layout = KeyLayout.of(new LeadingOmittableKey());

    // then
    assertThat(layout.isOpaque()).isTrue();
    assertThat(layout.createTable("cf_opaque"))
        .isEqualTo(
            """
            CREATE TABLE IF NOT EXISTS cf_opaque (
              k BLOB NOT NULL,
              v BLOB NOT NULL,
              PRIMARY KEY (k)
            ) WITHOUT ROWID""");
  }

  @Test
  void shouldFallBackToOpaqueKeyForOmittableComponentThatIsNotLast() {
    // given the end of the key can not tell whether the string is present
    final var layout = KeyLayout.of(new OmittableBeforeLongKey());

    // then
    assertThat(layout.isOpaque()).isTrue();
  }

  @Test
  void shouldSanitizeComponentNames() {
    // given
    final DbKey key = new NamedKey("Some Name/With-Odd.Chars");

    // when
    final var layout = KeyLayout.of(key);

    // then
    assertThat(layout.columnNames()).containsExactly("k0_some_name_with_odd_chars");
  }

  @Test
  void shouldRejectKeyWithoutComponents() {
    // given
    final DbKey key = new EmptyKey();

    // then
    assertThatThrownBy(() -> KeyLayout.of(key)).isInstanceOf(IllegalArgumentException.class);
  }

  enum Scope {
    GLOBAL,
    TENANT
  }

  private abstract static class TestKey implements DbKey {
    @Override
    public void wrap(final org.agrona.DirectBuffer buffer, final int offset, final int length) {}

    @Override
    public int getLength() {
      return 0;
    }

    @Override
    public int write(final org.agrona.MutableDirectBuffer buffer, final int offset) {
      return 0;
    }
  }

  private static final class ScopeKey extends TestKey {
    @Override
    public void describe(final io.camunda.zeebe.db.KeyStructure structure) {
      structure.enumOrdinal(null);
      structure.omittableString("scopeKey");
    }
  }

  private static final class LeadingOmittableKey extends TestKey {
    @Override
    public void describe(final io.camunda.zeebe.db.KeyStructure structure) {
      structure.omittableString(null);
      structure.enumOrdinal(null);
    }
  }

  private static final class OmittableBeforeLongKey extends TestKey {
    @Override
    public void describe(final io.camunda.zeebe.db.KeyStructure structure) {
      structure.enumOrdinal(null);
      structure.omittableString(null);
      structure.int64(null, false);
    }
  }

  private static final class NamedKey extends TestKey {
    private final String name;

    private NamedKey(final String name) {
      this.name = name;
    }

    @Override
    public void describe(final io.camunda.zeebe.db.KeyStructure structure) {
      structure.int64(name, false);
    }
  }

  private static final class EmptyKey extends TestKey {
    @Override
    public void describe(final io.camunda.zeebe.db.KeyStructure structure) {}
  }
}

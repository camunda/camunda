/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks for every unversioned key type whether {@link DbVersionedKey} stays byte-compatible with
 * it: data written with the plain key must read as version 0, and a version-0 write must produce
 * the plain key's bytes. The test documents the expected result per type, so the cases that do not
 * work (wrapped keys that are not self-delimiting) are asserted to fail.
 */
final class DbVersionedKeyCompatibilityTest {

  private static final int SAMPLES = 3;

  @TempDir private File tempDir;
  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private TransactionContext context;

  @BeforeEach
  void setUp() {
    zeebeDb = DefaultZeebeDbFactory.<DefaultColumnFamily>getDefaultFactory().createDb(tempDir);
    context = zeebeDb.createContext();
  }

  @AfterEach
  void tearDown() throws Exception {
    zeebeDb.close();
  }

  static Stream<KeyCase<?>> cases() {
    return Stream.of(
        new KeyCase<>("DbString", true, DbString::new, (k, i) -> k.wrapString("key-" + i)),
        new KeyCase<>("DbLong", true, DbLong::new, (k, i) -> k.wrapLong(i)),
        new KeyCase<>("DbInt", true, DbInt::new, (k, i) -> k.wrapInt(i)),
        new KeyCase<>("DbShort", true, DbShort::new, (k, i) -> k.wrapShort(i.shortValue())),
        new KeyCase<>("DbByte", true, DbByte::new, (k, i) -> k.wrapByte(i.byteValue())),
        new KeyCase<>(
            "DbEnumValue",
            true,
            () -> new DbEnumValue<>(Sample.class),
            (k, i) -> k.setValue(Sample.values()[i])),
        new KeyCase<>(
            "DbCompositeKey<DbString, DbString>",
            true,
            () -> new DbCompositeKey<>(new DbString(), new DbString()),
            (k, i) -> {
              k.first().wrapString("first-" + i);
              k.second().wrapString("second");
            }),
        new KeyCase<>(
            "DbCompositeKey<DbLong, DbString>",
            true,
            () -> new DbCompositeKey<>(new DbLong(), new DbString()),
            (k, i) -> {
              k.first().wrapLong(i);
              k.second().wrapString("second");
            }),
        new KeyCase<>(
            "DbCompositeKey<DbString, DbLong>",
            true,
            () -> new DbCompositeKey<>(new DbString(), new DbLong()),
            (k, i) -> {
              k.first().wrapString("first");
              k.second().wrapLong(i);
            }),
        new KeyCase<>(
            "membership shape: ((DbEnumValue, DbString), (DbEnumValue, DbString))",
            true,
            () ->
                new DbCompositeKey<>(
                    new DbCompositeKey<>(new DbEnumValue<>(Sample.class), new DbString()),
                    new DbCompositeKey<>(new DbEnumValue<>(Sample.class), new DbString())),
            (k, i) -> {
              k.first().first().setValue(Sample.A);
              k.first().second().wrapString("entity-" + i);
              k.second().first().setValue(Sample.B);
              k.second().second().wrapString("relation");
            }),
        new KeyCase<>(
            "DbTenantAwareKey<DbString> PREFIX",
            true,
            () -> new DbTenantAwareKey<>(new DbString(), new DbString(), PlacementType.PREFIX),
            (k, i) -> {
              k.tenantKey().wrapString("tenant");
              k.wrappedKey().wrapString("key-" + i);
            }),
        new KeyCase<>(
            "DbTenantAwareKey<DbString> SUFFIX",
            true,
            () -> new DbTenantAwareKey<>(new DbString(), new DbString(), PlacementType.SUFFIX),
            (k, i) -> {
              k.tenantKey().wrapString("tenant");
              k.wrappedKey().wrapString("key-" + i);
            }),
        new KeyCase<>(
            "DbForeignKey<DbString>",
            true,
            // the referenced rows don't exist here; shouldCheckForeignKeyInsideVersionedKey covers
            // the check itself
            () ->
                new DbForeignKey<>(
                    new DbString(), DefaultColumnFamily.DEFAULT, MatchType.Full, key -> true),
            (k, i) -> k.inner().wrapString("key-" + i)),
        new KeyCase<>(
            "DbBytes", false, DbBytes::new, (k, i) -> k.wrapBytes(("key-" + i).getBytes())),
        new KeyCase<>(
            "DbCompositeKey<DbString, DbBytes>",
            false,
            () -> new DbCompositeKey<>(new DbString(), new DbBytes()),
            (k, i) -> {
              k.first().wrapString("first");
              k.second().wrapBytes(("key-" + i).getBytes());
            }));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  <K extends DbKey> void shouldWriteVersionZeroIdenticalToUnversionedKey(final KeyCase<K> keyCase) {
    for (int i = 0; i < SAMPLES; i++) {
      // given
      final var plain = keyCase.sample(i);
      final var versioned = keyCase.versioned(i, 0);

      // when
      final var versionedBytes = bytes(versioned);

      // then
      assertThat(versionedBytes).isEqualTo(bytes(plain));
      assertThat(versioned.getLength()).isEqualTo(plain.getLength());
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  <K extends DbKey> void shouldReadUnversionedKeyAsVersionZero(final KeyCase<K> keyCase) {
    // given
    final var plainView = plainView(keyCase);
    final var versionedView = versionedView(keyCase);
    for (int i = 0; i < SAMPLES; i++) {
      plainView.upsert(keyCase.sample(i), value("s" + i + "@0"));
    }

    // when
    final var found = versionedView.get(keyCase.versioned(1, 0)).toString();
    final var decoded = decodeAll(keyCase, versionedView);

    // then
    assertThat(found).isEqualTo("s1@0");
    assertThat(decoded).containsExactly("s0@0", "s1@0", "s2@0");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  <K extends DbKey> void shouldReadVersionZeroWithUnversionedView(final KeyCase<K> keyCase) {
    // given
    final var plainView = plainView(keyCase);
    final var versionedView = versionedView(keyCase);
    plainView.upsert(keyCase.sample(1), value("old"));

    // when
    versionedView.upsert(keyCase.versioned(1, 0), value("new"));

    // then
    assertThat(plainView.get(keyCase.sample(1)).toString()).isEqualTo("new");
    assertThat(plainView.count()).describedAs("no second row for the same key").isEqualTo(1);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  <K extends DbKey> void shouldRoundTripVersionedKey(final KeyCase<K> keyCase) {
    // given
    final var versionedView = versionedView(keyCase);
    final long[] versions = {1, 7, Long.MAX_VALUE};
    for (final long version : versions) {
      versionedView.upsert(keyCase.versioned(1, version), value("s1@" + version));
    }

    // when
    final var decoded = decodeAll(keyCase, versionedView);

    // then
    final var expected = List.of("s1@1", "s1@7", "s1@" + Long.MAX_VALUE);
    if (keyCase.selfDelimiting()) {
      assertThat(decoded).isEqualTo(expected);
    } else {
      assertThat(decoded)
          .describedAs("the version is swallowed by the wrapped key")
          .isNotEqualTo(expected);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  <K extends DbKey> void shouldKeepVersionsOfOneKeyTogetherAndOrdered(final KeyCase<K> keyCase) {
    // given
    final var versionedView = versionedView(keyCase);
    final var rows = List.of("s2@0", "s1@10", "s0@5", "s1@0", "s1@2", "s0@0", "s1@1");
    for (final var row : rows) {
      final var parts = row.substring(1).split("@");
      versionedView.upsert(
          keyCase.versioned(Integer.parseInt(parts[0]), Long.parseLong(parts[1])), value(row));
    }

    // when
    final var valuesInOrder = new ArrayList<String>();
    versionedView.forEach(value -> valuesInOrder.add(value.toString()));
    final var decoded = decodeAll(keyCase, versionedView);
    final var valuesWithPrefix = new ArrayList<String>();
    versionedView.whileEqualPrefix(
        keyCase.sample(1),
        (key, value) -> {
          valuesWithPrefix.add(value.toString());
        });

    // then
    final var expectedOrder = List.of("s0@0", "s0@5", "s1@0", "s1@1", "s1@2", "s1@10", "s2@0");
    assertThat(valuesInOrder).isEqualTo(expectedOrder);
    assertThat(valuesWithPrefix).containsExactly("s1@0", "s1@1", "s1@2", "s1@10");
    if (keyCase.selfDelimiting()) {
      assertThat(decoded).isEqualTo(expectedOrder);
    } else {
      assertThat(decoded).isNotEqualTo(expectedOrder);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  <K extends DbKey> void shouldFindLatestVersionNotNewerThanVisible(final KeyCase<K> keyCase) {
    // given
    final var versionedView = versionedView(keyCase);
    for (final var row : List.of("s0@9", "s1@0", "s1@2", "s1@6", "s2@0")) {
      final var parts = row.substring(1).split("@");
      versionedView.upsert(
          keyCase.versioned(Integer.parseInt(parts[0]), Long.parseLong(parts[1])), value(row));
    }

    // when
    final var atFive = firstReverse(keyCase, versionedView, keyCase.versioned(1, 5));
    final var atZero = firstReverse(keyCase, versionedView, keyCase.versioned(1, 0));

    // then
    assertThat(atFive.value()).isEqualTo("s1@2");
    assertThat(atZero.value()).isEqualTo("s1@0");
    if (keyCase.selfDelimiting()) {
      assertThat(atFive.decoded()).isEqualTo("s1@2");
      assertThat(atZero.decoded()).isEqualTo("s1@0");
    } else {
      assertThat(atFive.decoded()).isNotEqualTo("s1@2");
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  <K extends DbKey> void shouldRejectUnexpectedSuffixLength(final KeyCase<K> keyCase) {
    // given
    final var plainBytes = bytes(keyCase.sample(1));
    final var buffer = new UnsafeBuffer(Arrays.copyOf(plainBytes, plainBytes.length + 3));
    final var versioned = keyCase.versioned(0, 0);

    // when / then
    if (keyCase.selfDelimiting()) {
      assertThatThrownBy(() -> versioned.wrap(buffer, 0, buffer.capacity()))
          .isInstanceOf(IllegalStateException.class);
    } else {
      assertThatCode(() -> versioned.wrap(buffer, 0, buffer.capacity()))
          .describedAs("a wrapped key that consumes the rest hides the extra bytes")
          .doesNotThrowAnyException();
    }
  }

  @Test
  void shouldCheckForeignKeyInsideVersionedKey() {
    // given
    final var targetKey = new DbString();
    final ColumnFamily<DbString, DbString> targetView =
        zeebeDb.createColumnFamily(DefaultColumnFamily.DEFAULT, context, targetKey, new DbString());
    final var foreignKey = new DbForeignKey<>(new DbString(), DefaultColumnFamily.DEFAULT);
    final var versionedKey = new DbVersionedKey<>(foreignKey, new DbLong());
    final ColumnFamily<DbVersionedKey<DbForeignKey<DbString>>, DbString> versionedView =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, context, versionedKey, new DbString());
    foreignKey.inner().wrapString("target");
    versionedKey.version().wrapLong(3);

    // when / then
    assertThatThrownBy(() -> versionedView.upsert(versionedKey, value("referencing")))
        .isInstanceOf(ZeebeDbInconsistentException.class);

    targetKey.wrapString("target");
    targetView.upsert(targetKey, value("target"));
    assertThatCode(() -> versionedView.upsert(versionedKey, value("referencing")))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldNotReadUnversionedKeyWithPlainCompositeSuffix() {
    // given
    final var plainKey = new DbString();
    final ColumnFamily<DbString, DbString> plainView =
        zeebeDb.createColumnFamily(DefaultColumnFamily.DEFAULT, context, plainKey, new DbString());
    final var compositeKey = new DbCompositeKey<>(new DbString(), new DbLong());
    final ColumnFamily<DbCompositeKey<DbString, DbLong>, DbString> compositeView =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, context, compositeKey, new DbString());
    plainKey.wrapString("key-1");
    plainView.upsert(plainKey, value("old"));

    // when / then
    assertThatThrownBy(() -> compositeView.forEach((key, value) -> key.second().getValue()))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void shouldCreateSecondRowWithPlainCompositeSuffixAtVersionZero() {
    // given
    final var plainKey = new DbString();
    final ColumnFamily<DbString, DbString> plainView =
        zeebeDb.createColumnFamily(DefaultColumnFamily.DEFAULT, context, plainKey, new DbString());
    final var compositeKey = new DbCompositeKey<>(new DbString(), new DbLong());
    final ColumnFamily<DbCompositeKey<DbString, DbLong>, DbString> compositeView =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, context, compositeKey, new DbString());
    plainKey.wrapString("key-1");
    plainView.upsert(plainKey, value("old"));

    // when
    compositeKey.first().wrapString("key-1");
    compositeKey.second().wrapLong(0);
    compositeView.upsert(compositeKey, value("new"));

    // then
    assertThat(plainView.get(plainKey).toString()).isEqualTo("old");
    assertThat(plainView.count()).isEqualTo(2);
  }

  private <K extends DbKey> ColumnFamily<K, DbString> plainView(final KeyCase<K> keyCase) {
    return zeebeDb.createColumnFamily(
        DefaultColumnFamily.DEFAULT, context, keyCase.newKey().get(), new DbString());
  }

  private <K extends DbKey> ColumnFamily<DbVersionedKey<K>, DbString> versionedView(
      final KeyCase<K> keyCase) {
    return zeebeDb.createColumnFamily(
        DefaultColumnFamily.DEFAULT,
        context,
        new DbVersionedKey<>(keyCase.newKey().get(), new DbLong()),
        new DbString());
  }

  /** Decodes every row's key into "s{sample}@{version}", with sample -1 if no sample matches. */
  private <K extends DbKey> List<String> decodeAll(
      final KeyCase<K> keyCase, final ColumnFamily<DbVersionedKey<K>, DbString> versionedView) {
    final var decoded = new ArrayList<String>();
    versionedView.forEach((key, value) -> decoded.add(decode(keyCase, key)));
    return decoded;
  }

  private <K extends DbKey> ReverseHit firstReverse(
      final KeyCase<K> keyCase,
      final ColumnFamily<DbVersionedKey<K>, DbString> versionedView,
      final DbVersionedKey<K> startAt) {
    final var hit = new ReverseHit[1];
    versionedView.whileTrueReverse(
        startAt,
        (key, value) -> {
          hit[0] = new ReverseHit(value.toString(), decode(keyCase, key));
          return false;
        });
    return hit[0];
  }

  private static <K extends DbKey> String decode(
      final KeyCase<K> keyCase, final DbVersionedKey<K> key) {
    final var innerBytes = bytes(key.wrappedKey());
    int sample = -1;
    for (int i = 0; i < SAMPLES; i++) {
      if (Arrays.equals(innerBytes, bytes(keyCase.sample(i)))) {
        sample = i;
      }
    }
    return "s" + sample + "@" + key.version().getValue();
  }

  private static byte[] bytes(final DbKey key) {
    final var buffer = new ExpandableArrayBuffer();
    final int length = key.write(buffer, 0);
    final var result = new byte[length];
    buffer.getBytes(0, result);
    return result;
  }

  private static DbString value(final String value) {
    final var dbString = new DbString();
    dbString.wrapString(value);
    return dbString;
  }

  enum Sample {
    A,
    B,
    C
  }

  record ReverseHit(String value, String decoded) {}

  record KeyCase<K extends DbKey>(
      String name, boolean selfDelimiting, Supplier<K> newKey, BiConsumer<K, Integer> fill) {

    K sample(final int index) {
      final var key = newKey.get();
      fill.accept(key, index);
      return key;
    }

    DbVersionedKey<K> versioned(final int index, final long version) {
      final var versionValue = new DbLong();
      versionValue.wrapLong(version);
      return new DbVersionedKey<>(sample(index), versionValue);
    }

    @Override
    public String toString() {
      return name + (selfDelimiting ? " (expected to work)" : " (expected to fail)");
    }
  }
}

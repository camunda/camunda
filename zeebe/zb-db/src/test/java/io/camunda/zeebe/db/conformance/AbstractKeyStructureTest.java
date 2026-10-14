/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import static io.camunda.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.KeyStructure;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link DbKey#describe(KeyStructure)} declares exactly the components, in exactly
 * the order, that {@link DbKey#write} serializes. A mismatch would silently produce a wrong schema
 * in a backend that derives it from the description.
 */
public abstract class AbstractKeyStructureTest {

  /** A key that has no components, such as the internal null key of the RocksDB backend. */
  protected abstract DbKey emptyKey();

  @Test
  void shouldDescribeLong() {
    // given
    final var key = new DbLong();
    key.wrapLong(-42L);

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components()).containsExactly(new Component(Kind.INT64, 0, 8, null, false));
    assertThat(layout.buffer().getLong(0, ZB_DB_BYTE_ORDER)).isEqualTo(-42L);
  }

  @Test
  void shouldDescribeInt() {
    // given
    final var key = new DbInt();
    key.wrapInt(-7);

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components()).containsExactly(new Component(Kind.INT32, 0, 4, null, false));
    assertThat(layout.buffer().getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(-7);
  }

  @Test
  void shouldDescribeShort() {
    // given
    final var key = new DbShort();
    key.wrapShort((short) -3);

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components()).containsExactly(new Component(Kind.INT16, 0, 2, null, false));
    assertThat(layout.buffer().getShort(0, ZB_DB_BYTE_ORDER)).isEqualTo((short) -3);
  }

  @Test
  void shouldDescribeByte() {
    // given
    final var key = new DbByte();
    key.wrapByte((byte) 5);

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components()).containsExactly(new Component(Kind.INT8, 0, 1, null, false));
    assertThat(layout.buffer().getByte(0)).isEqualTo((byte) 5);
  }

  @Test
  void shouldDescribeStringAsLengthPrefixedBytes() {
    // given
    final var key = new DbString();
    key.wrapString("describe-string");

    // when
    final var layout = Layout.of(key);

    // then: 4 byte length prefix + 15 bytes content
    assertThat(layout.components())
        .containsExactly(new Component(Kind.STRING, 0, 4 + 15, null, false));
    assertThat(layout.buffer().getInt(0, ZB_DB_BYTE_ORDER)).isEqualTo(15);
    assertThat(layout.buffer().getStringWithoutLengthUtf8(4, 15)).isEqualTo("describe-string");
  }

  @Test
  void shouldDescribeEmptyStringAsLengthPrefixOnly() {
    // given
    final var key = new DbString();
    key.wrapString("");

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components()).containsExactly(new Component(Kind.STRING, 0, 4, null, false));
  }

  @Test
  void shouldDescribeBytesAsRawRemainder() {
    // given
    final var key = new DbBytes();
    key.wrapBytes("raw-bytes".getBytes(StandardCharsets.UTF_8));

    // when
    final var layout = Layout.of(key);

    // then: no length prefix
    assertThat(layout.components()).containsExactly(new Component(Kind.BYTES, 0, 9, null, false));
    assertThat(layout.buffer().getStringWithoutLengthUtf8(0, 9)).isEqualTo("raw-bytes");
  }

  @Test
  void shouldDescribeEnumAsSingleByteOrdinal() {
    // given
    final var key = new DbEnumValue<>(Color.class);
    key.setValue(Color.BLUE);

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components())
        .containsExactly(new Component(Kind.ENUM_ORDINAL, 0, 1, null, false));
    assertThat(layout.buffer().getByte(0))
        .isEqualTo((byte) (Byte.MIN_VALUE + Color.BLUE.ordinal()));
  }

  @Test
  void shouldDescribeEmptyKeyWithoutComponents() {
    // given
    final var key = emptyKey();

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components()).isEmpty();
    assertThat(layout.written()).isZero();
  }

  @Test
  void shouldDescribeCompositeKeyInSerializationOrder() {
    // given
    final var first = new DbLong();
    first.wrapLong(11L);
    final var second = new DbString();
    second.wrapString("second");
    final var key = new DbCompositeKey<>(first, second);

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components())
        .containsExactly(
            new Component(Kind.INT64, 0, 8, null, false),
            new Component(Kind.STRING, 8, 4 + 6, null, false));
    assertThat(layout.buffer().getLong(0, ZB_DB_BYTE_ORDER)).isEqualTo(11L);
    assertThat(layout.buffer().getStringWithoutLengthUtf8(12, 6)).isEqualTo("second");
  }

  @Test
  void shouldFlattenNestedCompositeKeysOnTheLeft() {
    // given: Composite<Composite<A, B>, C>
    final var a = new DbShort();
    a.wrapShort((short) 1);
    final var b = new DbInt();
    b.wrapInt(2);
    final var c = new DbLong();
    c.wrapLong(3L);
    final var key = new DbCompositeKey<>(new DbCompositeKey<>(a, b), c);

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components())
        .containsExactly(
            new Component(Kind.INT16, 0, 2, null, false),
            new Component(Kind.INT32, 2, 4, null, false),
            new Component(Kind.INT64, 6, 8, null, false));
    assertThat(layout.buffer().getShort(0, ZB_DB_BYTE_ORDER)).isEqualTo((short) 1);
    assertThat(layout.buffer().getInt(2, ZB_DB_BYTE_ORDER)).isEqualTo(2);
    assertThat(layout.buffer().getLong(6, ZB_DB_BYTE_ORDER)).isEqualTo(3L);
  }

  @Test
  void shouldFlattenNestedCompositeKeysOnTheRight() {
    // given: Composite<A, Composite<B, C>>
    final var a = new DbByte();
    a.wrapByte((byte) 1);
    final var b = new DbString();
    b.wrapString("bb");
    final var c = new DbLong();
    c.wrapLong(3L);
    final var key = new DbCompositeKey<>(a, new DbCompositeKey<>(b, c));

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components())
        .containsExactly(
            new Component(Kind.INT8, 0, 1, null, false),
            new Component(Kind.STRING, 1, 4 + 2, null, false),
            new Component(Kind.INT64, 7, 8, null, false));
  }

  @Test
  void shouldDescribeTenantAwareKeyWithPrefixPlacementTenantFirst() {
    // given
    final var tenant = new DbString();
    tenant.wrapString("tenant-prefix");
    final var wrapped = new DbLong();
    wrapped.wrapLong(99L);
    final var key = new DbTenantAwareKey<>(tenant, wrapped, PlacementType.PREFIX);

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components())
        .containsExactly(
            new Component(Kind.STRING, 0, 4 + 13, null, false),
            new Component(Kind.INT64, 17, 8, null, false));
    assertThat(layout.buffer().getStringWithoutLengthUtf8(4, 13)).isEqualTo("tenant-prefix");
    assertThat(layout.buffer().getLong(17, ZB_DB_BYTE_ORDER)).isEqualTo(99L);
  }

  @Test
  void shouldDescribeTenantAwareKeyWithSuffixPlacementTenantLast() {
    // given
    final var tenant = new DbString();
    tenant.wrapString("tenant-suffix");
    final var wrapped = new DbLong();
    wrapped.wrapLong(98L);
    final var key = new DbTenantAwareKey<>(tenant, wrapped, PlacementType.SUFFIX);

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components())
        .containsExactly(
            new Component(Kind.INT64, 0, 8, null, false),
            new Component(Kind.STRING, 8, 4 + 13, null, false));
    assertThat(layout.buffer().getLong(0, ZB_DB_BYTE_ORDER)).isEqualTo(98L);
    assertThat(layout.buffer().getStringWithoutLengthUtf8(12, 13)).isEqualTo("tenant-suffix");
  }

  @Test
  void shouldFlattenCompositeKeyWrappedByTenantAwareKey() {
    // given
    final var tenant = new DbString();
    tenant.wrapString("t");
    final var first = new DbLong();
    first.wrapLong(1L);
    final var second = new DbInt();
    second.wrapInt(2);
    final var key =
        new DbTenantAwareKey<>(tenant, new DbCompositeKey<>(first, second), PlacementType.PREFIX);

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components())
        .containsExactly(
            new Component(Kind.STRING, 0, 4 + 1, null, false),
            new Component(Kind.INT64, 5, 8, null, false),
            new Component(Kind.INT32, 13, 4, null, false));
  }

  @Test
  void shouldDelegateForeignKeyToWrappedKey() {
    // given: a foreign key around a composite, not just a leaf
    final var tenant = new DbString();
    tenant.wrapString("fk");
    final var id = new DbLong();
    id.wrapLong(5L);
    final var key =
        new DbForeignKey<>(new DbCompositeKey<>(id, tenant), DefaultColumnFamily.DEFAULT);

    // when
    final var layout = Layout.of(key);

    // then
    assertThat(layout.components())
        .containsExactly(
            new Component(Kind.INT64, 0, 8, null, true),
            new Component(Kind.STRING, 8, 4 + 2, null, false));
  }

  @Test
  void shouldDeclareForeignKeyNonNegativeByDefault() {
    // given
    final var long64 = new DbForeignKey<>(new DbLong(), DefaultColumnFamily.DEFAULT);
    final var int32 = new DbForeignKey<>(new DbInt(), DefaultColumnFamily.DEFAULT);

    // when
    final var longComponents = Layout.of(long64).components();
    final var intComponents = Layout.of(int32).components();

    // then
    assertThat(longComponents).extracting(Component::nonNegative).containsExactly(true);
    assertThat(intComponents).extracting(Component::nonNegative).containsExactly(true);
  }

  @Test
  void shouldNotDeclareForeignKeyWithSkipPredicateNonNegative() {
    // given: a skip predicate exists to ignore a sentinel such as -1
    final var key =
        new DbForeignKey<>(
            new DbLong(),
            DefaultColumnFamily.DEFAULT,
            DbForeignKey.MatchType.Full,
            k -> k.getValue() == -1);

    // when
    final var components = Layout.of(key).components();

    // then
    assertThat(components).extracting(Component::nonNegative).containsExactly(false);
  }

  @Test
  void shouldDefaultNumericComponentsToMayBeNegative() {
    // given
    final var key =
        new DbCompositeKey<>(
            new DbCompositeKey<>(new DbLong(), new DbInt()),
            new DbCompositeKey<>(new DbShort(), new DbByte()));

    // when
    final var components = Layout.of(key).components();

    // then
    assertThat(components).hasSize(4).extracting(Component::nonNegative).containsOnly(false);
  }

  @Test
  void shouldDeclareNonNegativeOnlyWhereOptedIn() {
    // given
    final var key =
        new DbCompositeKey<>(
            new DbCompositeKey<>(new DbLong().nonNegative(), new DbInt()),
            new DbCompositeKey<>(new DbShort().nonNegative(), new DbByte().nonNegative()));

    // when
    final var components = Layout.of(key).components();

    // then
    assertThat(components)
        .extracting(Component::kind, Component::nonNegative)
        .containsExactly(
            tuple(Kind.INT64, true),
            tuple(Kind.INT32, false),
            tuple(Kind.INT16, true),
            tuple(Kind.INT8, true));
  }

  @Test
  void shouldNotChangeSerializationWhenOptingIntoNonNegative() {
    // given
    final var plain = new DbLong();
    plain.wrapLong(1234L);
    final var optedIn = new DbLong().nonNegative();
    optedIn.wrapLong(1234L);

    // when
    final var plainLayout = Layout.of(plain);
    final var optedInLayout = Layout.of(optedIn);

    // then
    assertThat(optedInLayout.written()).isEqualTo(plainLayout.written());
    assertThat(optedInLayout.buffer().getLong(0, ZB_DB_BYTE_ORDER))
        .isEqualTo(plainLayout.buffer().getLong(0, ZB_DB_BYTE_ORDER));
  }

  enum Color {
    RED,
    GREEN,
    BLUE
  }

  enum Kind {
    INT64,
    INT32,
    INT16,
    INT8,
    STRING,
    OMITTABLE_STRING,
    BYTES,
    ENUM_ORDINAL
  }

  /** One declared component, with the offset and width it actually occupies in the bytes. */
  record Component(Kind kind, int offset, int width, String name, boolean nonNegative) {}

  /**
   * Walks the bytes {@code write()} produced using only the declared components, and checks that
   * doing so consumes exactly the serialized bytes. A declaration that is missing, superfluous,
   * reordered or of the wrong width makes the walk disagree with {@code write()}.
   */
  record Layout(List<Component> components, MutableDirectBuffer buffer, int written) {

    static Layout of(final DbKey key) {
      final var declared = new RecordingStructure();
      key.describe(declared);

      final var buffer = new ExpandableArrayBuffer();
      final int written = key.write(buffer, 0);
      assertThat(key.getLength()).as("getLength() agrees with write()").isEqualTo(written);

      final var components = new ArrayList<Component>();
      int cursor = 0;
      for (final var component : declared.components) {
        final int width =
            switch (component.kind) {
              case INT64 -> Long.BYTES;
              case INT32 -> Integer.BYTES;
              case INT16 -> Short.BYTES;
              case INT8, ENUM_ORDINAL -> Byte.BYTES;
              case STRING -> Integer.BYTES + buffer.getInt(cursor, ZB_DB_BYTE_ORDER);
              case BYTES -> written - cursor;
              case OMITTABLE_STRING ->
                  throw new IllegalStateException("not walkable without knowing the key's state");
            };
        components.add(
            new Component(component.kind, cursor, width, component.name, component.nonNegative));
        cursor += width;
      }
      assertThat(cursor)
          .as("declared components cover exactly the %d serialized bytes", written)
          .isEqualTo(written);

      return new Layout(components, buffer, written);
    }
  }

  private static final class RecordingStructure implements KeyStructure {
    private final List<Component> components = new ArrayList<>();

    private void add(final Kind kind, final String name, final boolean nonNegative) {
      components.add(new Component(kind, -1, -1, name, nonNegative));
    }

    @Override
    public void int64(final String name, final boolean nonNegative) {
      add(Kind.INT64, name, nonNegative);
    }

    @Override
    public void int32(final String name, final boolean nonNegative) {
      add(Kind.INT32, name, nonNegative);
    }

    @Override
    public void int16(final String name, final boolean nonNegative) {
      add(Kind.INT16, name, nonNegative);
    }

    @Override
    public void int8(final String name, final boolean nonNegative) {
      add(Kind.INT8, name, nonNegative);
    }

    @Override
    public void string(final String name) {
      add(Kind.STRING, name, false);
    }

    @Override
    public void omittableString(final String name) {
      add(Kind.OMITTABLE_STRING, name, false);
    }

    @Override
    public void bytes(final String name) {
      add(Kind.BYTES, name, false);
    }

    @Override
    public void enumOrdinal(final String name) {
      add(Kind.ENUM_ORDINAL, name, false);
    }
  }
}

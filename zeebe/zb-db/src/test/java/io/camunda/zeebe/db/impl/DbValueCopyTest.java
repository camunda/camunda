/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.DbValue;
import java.util.stream.Stream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link DbValue#copyTo} and {@link DbValue#newInstance} for every concrete DbValue
 * implementation in the {@code db.impl} package.
 */
final class DbValueCopyTest {

  // ---- copyTo + newInstance contract tests per type ----

  @Nested
  class DbLongCopy {
    @Test
    void shouldCopyTo() {
      final var source = new DbLong();
      source.wrapLong(42L);
      final var target = new DbLong();

      source.copyTo(target);

      assertThat(target.getValue()).isEqualTo(42L);
    }

    @Test
    void shouldNewInstance() {
      final var source = new DbLong();
      source.wrapLong(42L);

      final var instance = source.newInstance();

      assertThat(instance).isInstanceOf(DbLong.class);
      assertThat(instance).isNotSameAs(source);
    }

    @Test
    void shouldBeIndependentAfterCopy() {
      final var source = new DbLong();
      source.wrapLong(42L);
      final var target = new DbLong();
      source.copyTo(target);

      source.wrapLong(999L);

      assertThat(target.getValue()).isEqualTo(42L);
    }
  }

  @Nested
  class DbIntCopy {
    @Test
    void shouldCopyTo() {
      final var source = new DbInt();
      source.wrapInt(123);
      final var target = new DbInt();

      source.copyTo(target);

      assertThat(target.getValue()).isEqualTo(123);
    }

    @Test
    void shouldNewInstance() {
      assertThat(new DbInt().newInstance()).isInstanceOf(DbInt.class);
    }
  }

  @Nested
  class DbByteCopy {
    @Test
    void shouldCopyTo() {
      final var source = new DbByte();
      source.wrapByte((byte) 0x42);
      final var target = new DbByte();

      source.copyTo(target);

      assertThat(target.getValue()).isEqualTo((byte) 0x42);
    }

    @Test
    void shouldNewInstance() {
      assertThat(new DbByte().newInstance()).isInstanceOf(DbByte.class);
    }
  }

  @Nested
  class DbShortCopy {
    @Test
    void shouldCopyTo() {
      final var source = new DbShort();
      source.wrapShort((short) 1234);
      final var target = new DbShort();

      source.copyTo(target);

      assertThat(target.getValue()).isEqualTo((short) 1234);
    }

    @Test
    void shouldNewInstance() {
      assertThat(new DbShort().newInstance()).isInstanceOf(DbShort.class);
    }
  }

  @Nested
  class DbBytesCopy {
    @Test
    void shouldCopyTo() {
      final var source = new DbBytes();
      source.wrapBytes(new byte[] {1, 2, 3, 4});
      final var target = new DbBytes();

      source.copyTo(target);

      assertThat(target.getBytes()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void shouldBeIndependentAfterCopy() {
      final var source = new DbBytes();
      source.wrapBytes(new byte[] {1, 2, 3});
      final var target = new DbBytes();
      source.copyTo(target);

      source.wrapBytes(new byte[] {9, 9, 9});

      assertThat(target.getBytes()).containsExactly(1, 2, 3);
    }

    @Test
    void shouldNewInstance() {
      assertThat(new DbBytes().newInstance()).isInstanceOf(DbBytes.class);
    }
  }

  @Nested
  class DbStringCopy {
    @Test
    void shouldCopyTo() {
      final var source = new DbString();
      source.wrapString("hello");
      final var target = new DbString();

      source.copyTo(target);

      assertThat(target.toString()).isEqualTo("hello");
    }

    @Test
    void shouldBeIndependentAfterCopy() {
      final var source = new DbString();
      source.wrapString("hello");
      final var target = new DbString();
      source.copyTo(target);

      source.wrapString("world");

      assertThat(target.toString()).isEqualTo("hello");
    }

    @Test
    void shouldNewInstance() {
      assertThat(new DbString().newInstance()).isInstanceOf(DbString.class);
    }
  }

  @Nested
  class DbNilCopy {
    @Test
    void shouldCopyTo() {
      // DbNil is a singleton with no state — copyTo is a no-op
      DbNil.INSTANCE.copyTo(DbNil.INSTANCE);
    }

    @Test
    void shouldNewInstance() {
      assertThat(DbNil.INSTANCE.newInstance()).isSameAs(DbNil.INSTANCE);
    }
  }

  @Nested
  class DbEnumValueCopy {
    private enum TestEnum {
      A,
      B,
      C
    }

    @Test
    void shouldCopyTo() {
      final var source = new DbEnumValue<>(TestEnum.class);
      source.setValue(TestEnum.B);
      final var target = new DbEnumValue<>(TestEnum.class);

      source.copyTo(target);

      assertThat(target.getValue()).isEqualTo(TestEnum.B);
    }

    @Test
    void shouldNewInstance() {
      final var source = new DbEnumValue<>(TestEnum.class);
      assertThat(source.newInstance()).isInstanceOf(DbEnumValue.class);
    }
  }

  // ---- Cross-cutting: copyTo + newInstance round-trip produces same serialization ----

  @ParameterizedTest
  @MethodSource("populatedValues")
  void shouldProduceIdenticalSerializationAfterCopyToNewInstance(
      final DbValue source, final String label) {
    // given
    final var target = source.newInstance();
    source.copyTo(target);

    // when — serialize both
    final var srcBuf = new UnsafeBuffer(new byte[source.getLength()]);
    source.write(srcBuf, 0);
    final var tgtBuf = new UnsafeBuffer(new byte[target.getLength()]);
    target.write(tgtBuf, 0);

    // then
    assertThat(tgtBuf).as("Serialization of %s after copyTo+newInstance", label).isEqualTo(srcBuf);
  }

  static Stream<Arguments> populatedValues() {
    final var dbLong = new DbLong();
    dbLong.wrapLong(42L);

    final var dbInt = new DbInt();
    dbInt.wrapInt(123);

    final var dbByte = new DbByte();
    dbByte.wrapByte((byte) 0x42);

    final var dbShort = new DbShort();
    dbShort.wrapShort((short) 1234);

    final var dbBytes = new DbBytes();
    dbBytes.wrapBytes(new byte[] {1, 2, 3, 4});

    final var dbString = new DbString();
    dbString.wrapString("hello world");

    return Stream.of(
        Arguments.of(dbLong, "DbLong"),
        Arguments.of(dbInt, "DbInt"),
        Arguments.of(dbByte, "DbByte"),
        Arguments.of(dbShort, "DbShort"),
        Arguments.of(dbBytes, "DbBytes"),
        Arguments.of(dbString, "DbString"),
        Arguments.of(DbNil.INSTANCE, "DbNil"));
  }
}

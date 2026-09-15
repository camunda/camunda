/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StateKeyFormatterTest {

  @Test
  void shouldFormatIncidentKeyUsingTheColumnFamilyFormat() {
    // given
    final var key =
        ByteBuffer.allocate(Long.BYTES * 2)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(34)
            .putLong(42)
            .array();

    // when
    final var formatted =
        StateKeyFormatters.forColumnFamily(ZbColumnFamilies.INCIDENTS, "default").format(key);

    // then
    assertThat(formatted).isEqualTo("42");
  }

  @Test
  void shouldFormatSequenceFlowKeyUsingTheColumnFamilyFormat() {
    // given
    final var key =
        ByteBuffer.allocate(Long.BYTES + Long.BYTES + 4 + 3 + 4 + 3)
            .putLong(1)
            .putLong(3)
            .putInt(3)
            .put("foo".getBytes())
            .putInt(3)
            .put("bar".getBytes())
            .array();

    // when
    final var formatted =
        StateKeyFormatters.forColumnFamily(
                ZbColumnFamilies.NUMBER_OF_TAKEN_SEQUENCE_FLOWS, "default")
            .format(key);

    // then
    assertThat(formatted).isEqualTo("3:foo:bar");
  }

  @Test
  void shouldFormatDecisionRequirementsKeyUsingTheColumnFamilyFormat() {
    // given
    final var key =
        ByteBuffer.allocate(Long.BYTES + 4 + 3 + Long.BYTES)
            .putLong(1)
            .putInt(3)
            .put("foo".getBytes())
            .putLong(42)
            .array();

    // when
    final var formatted =
        StateKeyFormatters.forColumnFamily(ZbColumnFamilies.DMN_DECISION_REQUIREMENTS, "default")
            .format(key);

    // then
    assertThat(formatted).isEqualTo("foo:42");
  }

  @ParameterizedTest
  @MethodSource("databaseValueFormats")
  void shouldFormatDatabaseValueTypes(
      final String format, final byte[] value, final String expected) {
    // given
    final var key = ByteBuffer.allocate(Long.BYTES + value.length).putLong(1).put(value).array();

    // when
    final var formatted = StateKeyFormatter.databaseValues(format).format(key);

    // then
    assertThat(formatted).isEqualTo(expected);
  }

  static Stream<Arguments> databaseValueFormats() {
    return Stream.of(
        Arguments.of(
            "s", ByteBuffer.allocate(4 + 3).putInt(3).put("abc".getBytes()).array(), "abc"),
        Arguments.of("l", ByteBuffer.allocate(Long.BYTES).putLong(42).array(), "42"),
        Arguments.of("i", ByteBuffer.allocate(Integer.BYTES).putInt(42).array(), "42"),
        Arguments.of("b", new byte[] {7}, "7"),
        Arguments.of("B", new byte[] {0x01, 0x02}, "01 02"),
        Arguments.of(
            "ls",
            ByteBuffer.allocate(Long.BYTES + 4 + 3)
                .putLong(42)
                .putInt(3)
                .put("abc".getBytes())
                .array(),
            "42:abc"));
  }

  @Test
  void shouldFormatMalformedKeysAsHexadecimal() {
    // given
    final byte[] key = {0, 1, 2};

    // when
    final var formatted = StateKeyFormatter.databaseValues("l").format(key);

    // then
    assertThat(formatted).isEqualTo("00 01 02");
  }

  @Test
  void shouldFormatKeysAsHexadecimalWhenRequested() {
    // given
    final byte[] key = {0, 1, 2};

    // when
    final var formatted =
        StateKeyFormatters.forColumnFamily(ZbColumnFamilies.INCIDENTS, "hex").format(key);

    // then
    assertThat(formatted).isEqualTo("00 01 02");
  }

  @Test
  void shouldFormatTheRawKeyWithoutTheColumnFamilyPrefix() {
    // given
    final var key =
        ByteBuffer.allocate(Long.BYTES + 2).putLong(1).put((byte) 0x01).put((byte) 0x02).array();

    // when
    final var formatted = StateKeyFormatter.hexadecimal().format(key);

    // then
    assertThat(formatted).isEqualTo("01 02");
  }

  @Test
  void shouldFallBackToHexadecimalWhenAKeyContainsTrailingBytes() {
    // given
    final var key =
        ByteBuffer.allocate(Long.BYTES * 2 + 1).putLong(1).putLong(42).put((byte) 1).array();

    // when
    final var formatted = StateKeyFormatter.databaseValues("l").format(key);

    // then
    assertThat(formatted).isEqualTo("00 00 00 00 00 00 00 2a 01");
  }

  @Test
  void shouldFallBackToHexadecimalWhenAStringLengthIsMalformed() {
    // given
    final var key = ByteBuffer.allocate(Long.BYTES + Integer.BYTES).putLong(1).putInt(-1).array();

    // when
    final var formatted = StateKeyFormatter.databaseValues("s").format(key);

    // then
    assertThat(formatted).isEqualTo("ff ff ff ff");
  }

  @Test
  void shouldFallBackToHexadecimalWhenAStringLengthExceedsTheKey() {
    // given
    final var key =
        ByteBuffer.allocate(Long.BYTES + Integer.BYTES)
            .putLong(1)
            .putInt(Integer.MAX_VALUE)
            .array();

    // when
    final var formatted = StateKeyFormatter.databaseValues("s").format(key);

    // then
    assertThat(formatted).isEqualTo("7f ff ff ff");
  }

  @Test
  void shouldFallBackToHexadecimalWhenAStringPayloadIsTruncated() {
    // given
    final var key =
        ByteBuffer.allocate(Long.BYTES + Integer.BYTES + 2)
            .putLong(1)
            .putInt(3)
            .put((byte) 'a')
            .put((byte) 'b')
            .array();

    // when
    final var formatted = StateKeyFormatter.databaseValues("s").format(key);

    // then
    assertThat(formatted).isEqualTo("00 00 00 03 61 62");
  }

  @Test
  void shouldRejectUnknownFormatComponents() {
    // when / then
    assertThatThrownBy(() -> StateKeyFormatter.databaseValues("x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown key format component");
  }
}

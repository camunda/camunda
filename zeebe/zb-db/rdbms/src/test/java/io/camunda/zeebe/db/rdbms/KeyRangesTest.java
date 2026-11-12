/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class KeyRangesTest {

  private static final int PARTITION_ID = 3;
  private static final int COLUMN_FAMILY = 7;

  @Test
  void shouldFindNoKeyAfterAnEmptyPrefix() {
    assertThat(KeyRanges.next(new byte[0])).isNull();
  }

  @Test
  void shouldFindNoKeyAfterAPrefixOfOnlyHighestBytes() {
    assertThat(KeyRanges.next(bytes(0xFF, 0xFF))).isNull();
  }

  @Test
  void shouldFindTheKeyAfterAPrefixByIncrementingItsLastByte() {
    assertThat(KeyRanges.next(bytes(0x01, 0x7F))).containsExactly(bytes(0x01, 0x80));
  }

  @Test
  void shouldFindTheKeyAfterAPrefixBySkippingItsTrailingHighestBytes() {
    assertThat(KeyRanges.next(bytes(0x01, 0x02, 0xFF, 0xFF))).containsExactly(bytes(0x01, 0x03));
  }

  @Test
  void shouldScanForwardFromTheStartToTheEndOfThePrefix() {
    // when
    final var range =
        KeyRanges.forward(PARTITION_ID, COLUMN_FAMILY, bytes(0x01), bytes(0x01, 0x05));

    // then
    assertThat(range)
        .usingRecursiveComparison()
        .isEqualTo(
            new StateRange(PARTITION_ID, COLUMN_FAMILY, bytes(0x01, 0x05), bytes(0x02), false));
  }

  @Test
  void shouldScanForwardFromAStartBeforeThePrefixToTheEndOfThePrefix() {
    // when
    final var range = KeyRanges.forward(PARTITION_ID, COLUMN_FAMILY, bytes(0x02), bytes(0x01));

    // then
    assertThat(range.from()).containsExactly(bytes(0x01));
    assertThat(range.to()).containsExactly(bytes(0x03));
  }

  @Test
  void shouldScanForwardOverEverythingWithoutPrefixAndStart() {
    // when
    final var range = KeyRanges.forward(PARTITION_ID, COLUMN_FAMILY, new byte[0], new byte[0]);

    // then
    assertThat(range.from()).isNull();
    assertThat(range.to()).isNull();
  }

  @Test
  void shouldScanInReverseFromTheStartDownToThePrefix() {
    // when
    final var range =
        KeyRanges.reverse(PARTITION_ID, COLUMN_FAMILY, bytes(0x01), bytes(0x01, 0x05));

    // then
    assertThat(range.from()).containsExactly(bytes(0x01));
    assertThat(range.to()).containsExactly(bytes(0x01, 0x05, 0x00));
    assertThat(range.descending()).isTrue();
  }

  @Test
  void shouldScanInReverseOverEverythingUpToTheStartWithoutPrefix() {
    // when
    final var range = KeyRanges.reverse(PARTITION_ID, COLUMN_FAMILY, new byte[0], bytes(0x01));

    // then
    assertThat(range.from()).isNull();
    assertThat(range.to()).containsExactly(bytes(0x01, 0x00));
  }

  @Test
  void shouldFindKeysWithPrefix() {
    // when
    final var range = KeyRanges.withPrefix(PARTITION_ID, COLUMN_FAMILY, bytes(0x01, 0xFF));

    // then
    assertThat(range)
        .usingRecursiveComparison()
        .isEqualTo(
            new StateRange(PARTITION_ID, COLUMN_FAMILY, bytes(0x01, 0xFF), bytes(0x02), false));
  }

  @Test
  void shouldTellWhetherAKeyStartsWithAPrefix() {
    assertThat(KeyRanges.startsWith(bytes(0x01, 0x02), bytes(0x01))).isTrue();
    assertThat(KeyRanges.startsWith(bytes(0x01, 0x02), new byte[0])).isTrue();
    assertThat(KeyRanges.startsWith(bytes(0x01), bytes(0x01, 0x02))).isFalse();
    assertThat(KeyRanges.startsWith(bytes(0x02, 0x01), bytes(0x01))).isFalse();
  }

  private static byte[] bytes(final int... values) {
    final var bytes = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      bytes[i] = (byte) values[i];
    }
    return bytes;
  }
}

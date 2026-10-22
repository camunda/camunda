/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class UlidTest {

  @Test
  void shouldEncodeTheSmallestAndLargestAsTheSpecificationSays() {
    // given
    final var ones = new byte[10];
    Arrays.fill(ones, (byte) 0xFF);

    // when
    final var smallest = Ulid.of(0, new byte[10]);
    final var largest = Ulid.of((1L << 48) - 1, ones);

    // then
    assertThat(smallest).isEqualTo("00000000000000000000000000");
    assertThat(largest).isEqualTo("7ZZZZZZZZZZZZZZZZZZZZZZZZZ");
  }

  @Test
  void shouldEncodeTheTimeAsInTheSpecificationsExample() {
    // when
    final var ulid = Ulid.of(1_469_922_850_259L, new byte[10]);

    // then
    assertThat(ulid).startsWith("01ARZ3NDEK");
  }

  @Test
  void shouldSortALaterMillisecondAfterAnyRandomness() {
    // given
    final var ones = new byte[10];
    Arrays.fill(ones, (byte) 0xFF);

    // when
    final var earlier = Ulid.of(1_790_000_000_000L, ones);
    final var later = Ulid.of(1_790_000_000_001L, new byte[10]);

    // then
    assertThat(earlier).isLessThan(later);
  }

  @Test
  void shouldMakeANewUlidOfTwentySixCharactersOfCrockfordsAlphabet() {
    // when
    final var ulid = Ulid.next();

    // then
    assertThat(ulid).matches("[0-9A-HJKMNP-TV-Z]{26}").isNotEqualTo(Ulid.next());
    assertThat(Ulid.isValid(ulid)).isTrue();
  }

  @Test
  void shouldRejectATimeOrRandomnessOfTheWrongSize() {
    // when, then
    assertThatThrownBy(() -> Ulid.of(-1, new byte[10]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("but got -1 milliseconds and 10 bytes");
    assertThatThrownBy(() -> Ulid.of(1L << 48, new byte[10]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Ulid.of(0, new byte[9]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("but got 0 milliseconds and 9 bytes");
  }

  @Test
  void shouldKnowWhichTextsAreUlids() {
    // when, then
    assertThat(Ulid.isValid("01ARZ3NDEKTSV4RRFFQ69G5FAV")).isTrue();
    assertThat(Ulid.isValid("7ZZZZZZZZZZZZZZZZZZZZZZZZZ")).isTrue();
    assertThat(Ulid.isValid("8ZZZZZZZZZZZZZZZZZZZZZZZZZ"))
        .as("more than 48 bits of time")
        .isFalse();
    assertThat(Ulid.isValid("01ARZ3NDEKTSV4RRFFQ69G5FA")).as("too short").isFalse();
    assertThat(Ulid.isValid("01ARZ3NDEKTSV4RRFFQ69G5FAVV")).as("too long").isFalse();
    assertThat(Ulid.isValid("01ARZ3NDEKTSV4RRFFQ69G5FAI")).as("I isn't in the alphabet").isFalse();
    assertThat(Ulid.isValid("01arz3ndektsv4rrffq69g5fav")).as("lower case").isFalse();
    assertThat(Ulid.isValid("")).isFalse();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.store.InvalidObjectNameException;
import io.camunda.zeebe.eventplane.store.Ulid;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class UlidTimeTest {

  @Test
  void shouldReadTheTimeOfAUlidThatJustWasMade() {
    // given
    final var before = Instant.now().minusMillis(1);

    // when
    final var time = UlidTime.of(Ulid.next());

    // then
    assertThat(time).isBetween(before, Instant.now().plusMillis(1));
  }

  @Test
  void shouldReadFiveBitsToACharacter() {
    assertThat(UlidTime.of("00000000000000000000000000")).isEqualTo(Instant.EPOCH);
    assertThat(UlidTime.of("00000000010000000000000000")).isEqualTo(Instant.ofEpochMilli(1));
    assertThat(UlidTime.of("00000000100000000000000000")).isEqualTo(Instant.ofEpochMilli(32));
    assertThat(UlidTime.of("0000000000ZZZZZZZZZZZZZZZZ")).isEqualTo(Instant.ofEpochMilli(0));
    assertThat(UlidTime.of("01K00000000000000000000000"))
        .isEqualTo(Instant.ofEpochMilli(1L << 40 | 19L << 35));
  }

  @Test
  void shouldReadTheLatestTimeAUlidHolds() {
    assertThat(UlidTime.of("7ZZZZZZZZZZZZZZZZZZZZZZZZZ"))
        .isEqualTo(Instant.ofEpochMilli((1L << 48) - 1));
  }

  @Test
  void shouldOrderUlidsAsItOrdersTheirTimes() {
    assertThat("01JD0000000000000000000000".compareTo("01JE0000000000000000000000")).isNegative();
    assertThat(UlidTime.of("01JD0000000000000000000000"))
        .isBefore(UlidTime.of("01JE0000000000000000000000"));
  }

  @Test
  void shouldRejectWhatIsNotAUlid() {
    assertThatThrownBy(() -> UlidTime.of("not-a-ulid"))
        .isInstanceOf(InvalidObjectNameException.class);
    assertThatThrownBy(() -> UlidTime.of("01jd0000000000000000000000"))
        .isInstanceOf(InvalidObjectNameException.class);
  }
}

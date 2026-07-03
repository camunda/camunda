/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.shuffle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class PartialCodecTest {

  @Test
  void shouldRoundTrip() {
    // given
    final Partial partial =
        new Partial(7, "EU".getBytes(StandardCharsets.UTF_8), 60_000L, 3, new byte[] {1, 2, 3, 4});

    // when
    final Partial decoded = PartialCodec.decode(PartialCodec.encode(partial));

    // then
    assertThat(decoded.aggId()).isEqualTo(7);
    assertThat(decoded.key()).isEqualTo("EU".getBytes(StandardCharsets.UTF_8));
    assertThat(decoded.windowStart()).isEqualTo(60_000L);
    assertThat(decoded.writer()).isEqualTo(3);
    assertThat(decoded.acc()).isEqualTo(new byte[] {1, 2, 3, 4});
  }

  @Test
  void shouldRoundTripEmptyKeyAndAcc() {
    // given
    final Partial partial = new Partial(0, new byte[0], 0L, 0, new byte[0]);

    // when
    final Partial decoded = PartialCodec.decode(PartialCodec.encode(partial));

    // then
    assertThat(decoded.aggId()).isZero();
    assertThat(decoded.key()).isEmpty();
    assertThat(decoded.windowStart()).isZero();
    assertThat(decoded.writer()).isZero();
    assertThat(decoded.acc()).isEmpty();
  }
}

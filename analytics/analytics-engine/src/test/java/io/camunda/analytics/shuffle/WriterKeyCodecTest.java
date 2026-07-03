/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.shuffle;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.aggregate.StringCodec;
import org.junit.jupiter.api.Test;

final class WriterKeyCodecTest {

  @Test
  void shouldRoundTripKeyAndWriter() {
    // given
    final WriterKeyCodec<String> codec = new WriterKeyCodec<>(new StringCodec());
    final WriterKey<String> key = new WriterKey<>("order-process", 42);

    // when
    final WriterKey<String> decoded = codec.decode(codec.encode(key));

    // then
    assertThat(decoded.key()).isEqualTo("order-process");
    assertThat(decoded.writer()).isEqualTo(42);
  }

  @Test
  void shouldRoundTripEmptyInnerKey() {
    // given
    final WriterKeyCodec<String> codec = new WriterKeyCodec<>(new StringCodec());

    // when
    final WriterKey<String> decoded = codec.decode(codec.encode(new WriterKey<>("", 0)));

    // then
    assertThat(decoded.key()).isEmpty();
    assertThat(decoded.writer()).isZero();
  }
}

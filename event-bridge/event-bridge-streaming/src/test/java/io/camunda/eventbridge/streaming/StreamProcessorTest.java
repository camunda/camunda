/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The stream processor drives any {@link Stage}'s lifecycle agnostically, per source partition. */
final class StreamProcessorTest {

  private record Order(String region, String product, long amount, boolean completed) {}

  @Test
  void shouldDriveAnyCustomStageAgnostically() {
    // given — a stage that is not a projection/rollup at all, just counting records and lifecycle
    final long[] processed = {0};
    final boolean[] initialized = {false};
    final boolean[] closed = {false};
    final Stage<Order> counting =
        new Stage<>() {
          @Override
          public void init() {
            initialized[0] = true;
          }

          @Override
          public void process(final Order record) {
            processed[0]++;
          }

          @Override
          public void close() {
            closed[0] = true;
          }
        };

    final StreamProcessor<Order> processor = new StreamProcessor<Order>().add(counting);

    // when
    processor.init();
    processor.process(new Order("EU", "widget", 1, true));
    processor.process(new Order("US", "gadget", 2, true));
    processor.close();

    // then — the runtime drove the stage's lifecycle without knowing what it does
    assertThat(initialized[0]).isTrue();
    assertThat(processed[0]).isEqualTo(2L);
    assertThat(closed[0]).isTrue();
  }
}

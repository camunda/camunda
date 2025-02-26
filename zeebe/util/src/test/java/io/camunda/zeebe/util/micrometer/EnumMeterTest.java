/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullableProblems")
final class EnumMeterTest {
  private static final ExtendedMeterDocumentation DOC =
      new ExtendedMeterDocumentation() {
        @Override
        public String getDescription() {
          return "Test metric";
        }

        @Override
        public String getName() {
          return "metric";
        }

        @Override
        public Type getType() {
          return Type.GAUGE;
        }
      };
  private static final KeyName TAG =
      new KeyName() {
        @Override
        public String asString() {
          return "foo";
        }
      };

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void shouldRegisterGaugeForAllValues() {
    // given/when
    EnumMeter.register(States.class, DOC, TAG, registry);

    // then
    assertThat(registry.get(DOC.getName()).tagKeys(TAG.asString()).meters())
        .hasSameSizeAs(States.values());
    assertThat(registry.get(DOC.getName()).tag(TAG.asString(), "A").gauge())
        .returns(0.0, Gauge::value);
    assertThat(registry.get(DOC.getName()).tag(TAG.asString(), "B").gauge())
        .returns(0.0, Gauge::value);
    assertThat(registry.get(DOC.getName()).tag(TAG.asString(), "C").gauge())
        .returns(0.0, Gauge::value);
  }

  @Test
  void shouldSetState() {
    // given
    final var meter = EnumMeter.register(States.class, DOC, TAG, registry);

    // when
    meter.state(States.B);

    // then
    final var gauge = registry.get(DOC.getName()).tag(TAG.asString(), "B").gauge();
    assertThat(gauge).returns(1.0, Gauge::value);
  }

  @Test
  void shouldResetOtherStatesToZero() {
    // given
    final var meter = EnumMeter.register(States.class, DOC, TAG, registry);
    meter.state(States.A);

    // when
    meter.state(States.B);

    // then
    assertThat(registry.get(DOC.getName()).tag(TAG.asString(), "A").gauge())
        .returns(0.0, Gauge::value);
    assertThat(registry.get(DOC.getName()).tag(TAG.asString(), "B").gauge())
        .returns(1.0, Gauge::value);
    assertThat(registry.get(DOC.getName()).tag(TAG.asString(), "C").gauge())
        .returns(0.0, Gauge::value);
  }

  private enum States {
    A,
    B,
    C
  }
}

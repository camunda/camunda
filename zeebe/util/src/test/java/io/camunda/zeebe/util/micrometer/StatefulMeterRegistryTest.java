/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class StatefulMeterRegistryTest {

  private final MeterRegistry wrapped = new SimpleMeterRegistry();
  private final StatefulMeterRegistry registry =
      new StatefulMeterRegistry(wrapped, PartitionKeyNames.tags(1));

  @AfterEach
  void afterEach() {
    MicrometerUtil.discard(registry);

    wrapped.clear();
    wrapped.close();
  }

  @Test
  void shouldReturnSameState() {
    // given
    final var first = registry.newLongGauge(TestDoc.FOO, "a", "1");
    final var second = registry.newLongGauge(TestDoc.FOO, "a", "1");

    // when
    first.state().set(1);
    second.state().set(2);

    // then
    assertThat(second).isSameAs(first);
    assertThat(second.state()).isSameAs(first.state()).hasValue(2);
    assertThat(wrapped.get(TestDoc.FOO.getName()).tag("a", "1").gauge()).returns(2.0, Gauge::value);
  }

  @Test
  void shouldRegisterDifferentGaugeBasedOnTag() {
    // given
    final var first = registry.newLongGauge(TestDoc.FOO, "a", "1");
    final var second = registry.newLongGauge(TestDoc.FOO, "a", "2");

    // when
    first.state().set(1);
    second.state().set(2);

    // then
    assertThat(first).isNotSameAs(second);
    assertThat(first.state()).hasValue(1);
    assertThat(second.state()).isNotSameAs(first.state()).hasValue(2);
    assertThat(wrapped.get(TestDoc.FOO.getName()).tag("a", "1").gauge()).returns(1.0, Gauge::value);
    assertThat(wrapped.get(TestDoc.FOO.getName()).tag("a", "2").gauge()).returns(2.0, Gauge::value);
  }

  @Test
  void shouldRegisterDifferentGaugeBasedOnName() {
    // given
    final var first = registry.newLongGauge(TestDoc.FOO, "a", "1");
    final var second = registry.newLongGauge(TestDoc.BAR, "a", "1");

    // when
    first.state().set(1);
    second.state().set(2);

    // then
    assertThat(first).isNotSameAs(second);
    assertThat(first.state()).hasValue(1);
    assertThat(second.state()).isNotSameAs(first.state()).hasValue(2);
    assertThat(wrapped.get(TestDoc.FOO.getName()).tag("a", "1").gauge()).returns(1.0, Gauge::value);
    assertThat(wrapped.get(TestDoc.BAR.getName()).tag("a", "1").gauge()).returns(2.0, Gauge::value);
  }

  @Test
  void shouldRemoveAllGaugesOnClear() {
    // given
    final var first = registry.newLongGauge(TestDoc.FOO, "a", "1");
    final var second = registry.newLongGauge(TestDoc.BAR, "a", "1");
    first.state().set(3);
    second.state().set(2);

    // when
    registry.clear();

    // then
    assertThat(registry.getMeters()).isEmpty();
    assertThat(registry.newLongGauge(TestDoc.FOO, "a", "1")).isNotSameAs(first);
    assertThat(registry.newLongGauge(TestDoc.BAR, "a", "1")).isNotSameAs(second);
  }

  @Test
  void shouldRemoveGaugeOnMeterRemove() {
    // given
    final var gauge = registry.newLongGauge(TestDoc.FOO, "a", "1");
    gauge.state().set(3);

    // when
    registry.remove(gauge.gauge());

    // then
    assertThat(registry.getMeters()).isEmpty();
    assertThat(registry.newLongGauge(TestDoc.FOO, "a", "1")).isNotSameAs(gauge);
  }

  @SuppressWarnings("NullableProblems")
  private enum TestDoc implements ExtendedMeterDocumentation {
    FOO {
      @Override
      public String getDescription() {
        return "foo description";
      }

      @Override
      public String getName() {
        return "foo";
      }

      @Override
      public Type getType() {
        return Type.GAUGE;
      }
    },

    BAR {
      @Override
      public String getDescription() {
        return "bar description";
      }

      @Override
      public String getName() {
        return "bar";
      }

      @Override
      public Type getType() {
        return Type.GAUGE;
      }
    },

    COUNTER {
      @Override
      public String getDescription() {
        return "counter";
      }

      @Override
      public String getName() {
        return "counter";
      }

      @Override
      public Type getType() {
        return Type.COUNTER;
      }
    }
  }
}

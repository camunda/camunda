/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CompositeMeterTest {
  @Test
  public void testCompositeMeter() {
    final var registry = new SimpleMeterRegistry();
    final var firstLevel = new WrappedCompositeMeterRegistry(registry, Tags.of("foo", "bar"));
    final var secondLevel = new WrappedCompositeMeterRegistry(firstLevel, Tags.of("qux", "baz"));
    final var counter1 = firstLevel.counter("test1");
    counter1.increment();
    counter1.increment();
    final var counter2 = secondLevel.counter("test2");
    counter2.increment();

    assertThat(registry.getMetersAsString().lines().toList())
        .isEqualTo(
            List.of(
                "test1(COUNTER)[foo='bar']; count=2.0",
                "test2(COUNTER)[foo='bar', qux='baz']; count=1.0"));
    secondLevel.remove(counter2);

    assertThat(registry.getMetersAsString().lines().toList())
        .isEqualTo(List.of("test1(COUNTER)[foo='bar']; count=2.0"));
  }
}

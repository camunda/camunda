/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.fact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeySelector;
import io.camunda.analytics.dimension.DimensionSchema;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.meter.MeasureRef;
import org.junit.jupiter.api.Test;

final class FactTest {

  @Test
  void shouldExposeFieldsThroughFactRow() {
    // given
    final Fact fact =
        Fact.builder(FactType.PROCESS_INSTANCE)
            .eventTime(1_000L)
            .source(2, 42L)
            .transition(Transition.COMPLETED)
            .field("region", "EU")
            .field("durationMs", 250L)
            .build();

    // then structural accessors and by-name field access
    assertThat(fact.factType()).isEqualTo(FactType.PROCESS_INSTANCE);
    assertThat(fact.eventTime()).isEqualTo(1_000L);
    assertThat(fact.sourcePartition()).isEqualTo(2);
    assertThat(fact.sourcePosition()).isEqualTo(42L);
    assertThat(fact.get("region")).isEqualTo("EU");
    assertThat(fact.get("durationMs")).isEqualTo(250L);
    assertThat(fact.get(Fact.TRANSITION)).isEqualTo("COMPLETED");
    assertThat(fact.get("missing")).isNull();
  }

  @Test
  void shouldTreatNullFieldAsAbsent() {
    // given a field set to null
    final Fact fact = Fact.builder(FactType.ELEMENT).field("region", null).build();

    // then it is simply absent (the unknown bucket)
    assertThat(fact.get("region")).isNull();
    assertThat(fact.fields()).doesNotContainKey("region");
  }

  @Test
  void shouldFeedTheGenericCoreDirectlyWithoutAnAdapter() {
    // given a fact and a declared grain + measure over it
    final Fact fact =
        Fact.builder(FactType.PROCESS_INSTANCE)
            .field("region", "EU")
            .field("processDefinitionKey", 100L)
            .field("durationMs", 250L)
            .build();
    final DimensionSchema grain =
        DimensionSchema.of(
            new DimensionColumn("region", DimensionType.STRING),
            new DimensionColumn("processDefinitionKey", DimensionType.LONG));

    // when the key selector and a measure ref read the fact directly (it is FactRow)
    final DimensionKey key = new DimensionKeySelector(grain).getKey(fact);
    final long duration = new MeasureRef("durationMs").asLong(fact);

    // then
    assertThat(key).isEqualTo(DimensionKey.of(grain, "EU", 100L));
    assertThat(duration).isEqualTo(250L);
  }

  @Test
  void shouldRejectTheReservedTransitionFieldName() {
    // given a builder
    final Fact.Builder builder = Fact.builder(FactType.PROCESS_INSTANCE);

    // when the reserved name is set as a free-form field, then it is rejected — the lifecycle
    // transition is typed and must go through transition(Transition)
    assertThatThrownBy(() -> builder.field(Fact.TRANSITION, "COMPLETED"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved")
        .hasMessageContaining("transition(Transition)");

    // and setting it through the typed setter still works
    assertThat(builder.transition(Transition.COMPLETED).build().get(Fact.TRANSITION))
        .isEqualTo("COMPLETED");
  }

  @Test
  void shouldBeValueEqual() {
    // given two facts built the same way
    final Fact a =
        Fact.builder(FactType.INCIDENT)
            .eventTime(5L)
            .source(1, 7L)
            .field("errorType", "IO")
            .build();
    final Fact b =
        Fact.builder(FactType.INCIDENT)
            .eventTime(5L)
            .source(1, 7L)
            .field("errorType", "IO")
            .build();

    // then
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }
}

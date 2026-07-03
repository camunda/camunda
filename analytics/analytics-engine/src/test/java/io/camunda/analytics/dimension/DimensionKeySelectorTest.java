/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class DimensionKeySelectorTest {

  private static final DimensionSchema GRAIN =
      DimensionSchema.of(
          new DimensionColumn("region", DimensionType.STRING),
          new DimensionColumn("processDefinitionKey", DimensionType.LONG),
          new DimensionColumn("version", DimensionType.INT));

  /** A trivial map-backed fact for the test — stands in for a real fact's FactRow adapter. */
  private static FactRow row(final Map<String, Object> fields) {
    return fields::get;
  }

  @Test
  void shouldBuildKeyByReadingSchemaColumnsFromFact() {
    // given
    final DimensionKeySelector selector = new DimensionKeySelector(GRAIN);
    final FactRow fact = row(Map.of("region", "EU", "processDefinitionKey", 1234L, "version", 3));

    // when
    final DimensionKey key = selector.getKey(fact);

    // then
    assertThat(key).isEqualTo(DimensionKey.of(GRAIN, "EU", 1234L, 3));
  }

  @Test
  void shouldBeDeterministic() {
    // given
    final DimensionKeySelector selector = new DimensionKeySelector(GRAIN);
    final FactRow fact = row(Map.of("region", "US", "processDefinitionKey", 9L, "version", 1));

    // then the same fact always yields the same key (KeySelector contract)
    assertThat(selector.getKey(fact)).isEqualTo(selector.getKey(fact));
  }

  @Test
  void shouldMapMissingDimensionToUnknownBucket() {
    // given a fact lacking the region field
    final DimensionKeySelector selector = new DimensionKeySelector(GRAIN);
    final FactRow fact = row(Map.of("processDefinitionKey", 1234L, "version", 3));

    // when
    final DimensionKey key = selector.getKey(fact);

    // then region is the null/unknown bucket
    assertThat(key.get("region")).isNull();
    assertThat(key).isEqualTo(DimensionKey.of(GRAIN, null, 1234L, 3));
  }

  @Test
  void shouldCoerceNumericValueToColumnType() {
    // given a fact exposing an int for a LONG column and a long for an INT column
    final DimensionKeySelector selector = new DimensionKeySelector(GRAIN);
    final FactRow fact = row(Map.of("region", "EU", "processDefinitionKey", 1234, "version", 3L));

    // when
    final DimensionKey key = selector.getKey(fact);

    // then values are coerced to the declared types (Long / Integer)
    assertThat(key.get("processDefinitionKey")).isEqualTo(1234L);
    assertThat(key.get("version")).isEqualTo(3);
    assertThat(key).isEqualTo(DimensionKey.of(GRAIN, "EU", 1234L, 3));
  }
}

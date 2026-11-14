/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class VariableValueTypeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // ---------------------------------------------------------------------------
  // infer(...)
  // ---------------------------------------------------------------------------

  @Test
  void shouldInferNullTypeFromNullRawValue() {
    assertThat(VariableValueType.infer(MAPPER, null)).isEqualTo(VariableValueType.NULL);
  }

  @Test
  void shouldInferNullTypeFromJsonNullLiteral() {
    assertThat(VariableValueType.infer(MAPPER, "null")).isEqualTo(VariableValueType.NULL);
  }

  @Test
  void shouldInferBooleanFromJsonBoolean() {
    assertThat(VariableValueType.infer(MAPPER, "true")).isEqualTo(VariableValueType.BOOLEAN);
    assertThat(VariableValueType.infer(MAPPER, "false")).isEqualTo(VariableValueType.BOOLEAN);
    assertThat(VariableValueType.infer(MAPPER, "\"true\""))
        .as("JSON string '\"true\"' is STRING, not BOOLEAN")
        .isEqualTo(VariableValueType.STRING);
  }

  @Test
  void shouldInferNumberFromJsonNumber() {
    assertThat(VariableValueType.infer(MAPPER, "0")).isEqualTo(VariableValueType.NUMBER);
    assertThat(VariableValueType.infer(MAPPER, "1.23")).isEqualTo(VariableValueType.NUMBER);
    assertThat(VariableValueType.infer(MAPPER, "\"1.23\""))
        .as("JSON string '\"1.23\"' is STRING, not NUMBER")
        .isEqualTo(VariableValueType.STRING);
  }

  @Test
  void shouldInferStringFromJsonString() {
    assertThat(VariableValueType.infer(MAPPER, "\"foo\"")).isEqualTo(VariableValueType.STRING);
  }

  @Test
  void shouldInferObjectFromJsonObject() {
    assertThat(VariableValueType.infer(MAPPER, "{\"a\":1}")).isEqualTo(VariableValueType.OBJECT);
  }

  @Test
  void shouldInferObjectFromJsonArray() {
    assertThat(VariableValueType.infer(MAPPER, "[1,2,3]")).isEqualTo(VariableValueType.OBJECT);
  }

  @Test
  void shouldInferUnknownFromInvalidJson() {
    assertThat(VariableValueType.infer(MAPPER, "foo")).isEqualTo(VariableValueType.UNKNOWN);
  }

  // ---------------------------------------------------------------------------
  // buildAllowedSet(...)
  // ---------------------------------------------------------------------------

  @Test
  void shouldAllowAllTypesWhenInclusionAndExclusionAreEmpty() {
    // given / when
    final var allowed = VariableValueType.buildAllowedSet(Set.of(), Set.of());

    // then
    assertThat(allowed).containsExactlyInAnyOrder(VariableValueType.values());
  }

  @Test
  void shouldRestrictToInclusionWhenProvided() {
    // given / when
    final var allowed =
        VariableValueType.buildAllowedSet(
            Set.of(VariableValueType.STRING, VariableValueType.NUMBER), Set.of());

    // then
    assertThat(allowed)
        .containsExactlyInAnyOrder(VariableValueType.STRING, VariableValueType.NUMBER);
  }

  @Test
  void shouldExcludeFromAllTypesWhenNoInclusionProvided() {
    // given / when
    final var allowed =
        VariableValueType.buildAllowedSet(Set.of(), Set.of(VariableValueType.OBJECT));

    // then
    assertThat(allowed).doesNotContain(VariableValueType.OBJECT);
    assertThat(allowed)
        .containsExactlyInAnyOrder(
            VariableValueType.BOOLEAN,
            VariableValueType.NUMBER,
            VariableValueType.STRING,
            VariableValueType.NULL,
            VariableValueType.UNKNOWN);
  }

  @Test
  void shouldLetExclusionOverrideInclusion() {
    // given — STRING is in both sets; exclusion wins
    final var allowed =
        VariableValueType.buildAllowedSet(
            Set.of(VariableValueType.STRING, VariableValueType.NUMBER),
            Set.of(VariableValueType.STRING));

    // then
    assertThat(allowed)
        .as("STRING excluded even though it is also included")
        .containsExactlyInAnyOrder(VariableValueType.NUMBER);
  }
}

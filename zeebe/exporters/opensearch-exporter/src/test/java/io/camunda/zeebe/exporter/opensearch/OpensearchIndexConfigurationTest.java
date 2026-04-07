/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.IndexConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the new scope-based variable filter fields on {@link IndexConfiguration} are
 * correctly wired: setters update the backing fields and getters return the expected values. Tests
 * also verify round-trip deserialization via {@link ExporterConfiguration#MAPPER}, which is the
 * exact mapper the Zeebe broker uses to instantiate exporter configuration at runtime.
 */
final class OpensearchIndexConfigurationTest {

  // ---------------------------------------------------------------------------
  // exportLocalVariablesEnabled
  // ---------------------------------------------------------------------------

  @Test
  void shouldDefaultExportLocalVariablesEnabledToTrue() {
    assertThat(new IndexConfiguration().isExportLocalVariablesEnabled()).isTrue();
  }

  @Test
  void shouldRoundTripExportLocalVariablesEnabled() {
    // given
    final var args = Map.<String, Object>of("exportLocalVariablesEnabled", false);

    // when
    final var config = ExporterConfiguration.MAPPER.convertValue(args, IndexConfiguration.class);

    // then
    assertThat(config.isExportLocalVariablesEnabled()).isFalse();
  }

  // ---------------------------------------------------------------------------
  // Local variable name filters
  // ---------------------------------------------------------------------------

  @Test
  void shouldRoundTripLocalVariableNameInclusionExact() {
    // given
    final var args =
        Map.<String, Object>of("localVariableNameInclusionExact", List.of("localVar", "other"));

    // when
    final var config = ExporterConfiguration.MAPPER.convertValue(args, IndexConfiguration.class);

    // then
    assertThat(config.getLocalVariableNameInclusionExact()).containsExactly("localVar", "other");
  }

  @Test
  void shouldRoundTripLocalVariableNameExclusionStartWith() {
    // given
    final var args =
        Map.<String, Object>of("localVariableNameExclusionStartWith", List.of("debug_", "tmp_"));

    // when
    final var config = ExporterConfiguration.MAPPER.convertValue(args, IndexConfiguration.class);

    // then
    assertThat(config.getLocalVariableNameExclusionStartWith()).containsExactly("debug_", "tmp_");
  }

  // ---------------------------------------------------------------------------
  // Root variable name filters
  // ---------------------------------------------------------------------------

  @Test
  void shouldRoundTripRootVariableNameInclusionExact() {
    // given
    final var args = Map.<String, Object>of("rootVariableNameInclusionExact", List.of("rootVar"));

    // when
    final var config = ExporterConfiguration.MAPPER.convertValue(args, IndexConfiguration.class);

    // then
    assertThat(config.getRootVariableNameInclusionExact()).containsExactly("rootVar");
  }

  @Test
  void shouldRoundTripRootVariableNameExclusionEndWith() {
    // given
    final var args = Map.<String, Object>of("rootVariableNameExclusionEndWith", List.of("_secret"));

    // when
    final var config = ExporterConfiguration.MAPPER.convertValue(args, IndexConfiguration.class);

    // then
    assertThat(config.getRootVariableNameExclusionEndWith()).containsExactly("_secret");
  }

  // ---------------------------------------------------------------------------
  // Scope variable value type filters
  // ---------------------------------------------------------------------------

  @Test
  void shouldRoundTripLocalAndRootVariableValueTypeFilters() {
    // given
    final var args =
        Map.<String, Object>of(
            "localVariableValueTypeInclusion", List.of("NUMBER", "STRING"),
            "rootVariableValueTypeExclusion", List.of("BOOLEAN"));

    // when
    final var config = ExporterConfiguration.MAPPER.convertValue(args, IndexConfiguration.class);

    // then
    assertThat(config.getLocalVariableValueTypeInclusion()).containsExactly("NUMBER", "STRING");
    assertThat(config.getRootVariableValueTypeExclusion()).containsExactly("BOOLEAN");
  }

  // ---------------------------------------------------------------------------
  // Defaults (all new list fields default to empty)
  // ---------------------------------------------------------------------------

  @Test
  void shouldDefaultAllNewListFieldsToEmpty() {
    final var config = new IndexConfiguration();

    assertThat(config.getLocalVariableNameInclusionExact()).isEmpty();
    assertThat(config.getLocalVariableNameInclusionStartWith()).isEmpty();
    assertThat(config.getLocalVariableNameInclusionEndWith()).isEmpty();
    assertThat(config.getLocalVariableNameExclusionExact()).isEmpty();
    assertThat(config.getLocalVariableNameExclusionStartWith()).isEmpty();
    assertThat(config.getLocalVariableNameExclusionEndWith()).isEmpty();
    assertThat(config.getLocalVariableValueTypeInclusion()).isEmpty();
    assertThat(config.getLocalVariableValueTypeExclusion()).isEmpty();
    assertThat(config.getRootVariableNameInclusionExact()).isEmpty();
    assertThat(config.getRootVariableNameInclusionStartWith()).isEmpty();
    assertThat(config.getRootVariableNameInclusionEndWith()).isEmpty();
    assertThat(config.getRootVariableNameExclusionExact()).isEmpty();
    assertThat(config.getRootVariableNameExclusionStartWith()).isEmpty();
    assertThat(config.getRootVariableNameExclusionEndWith()).isEmpty();
    assertThat(config.getRootVariableValueTypeInclusion()).isEmpty();
    assertThat(config.getRootVariableValueTypeExclusion()).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Spring indexed-list format (map with numeric string keys)
  // ExporterConfigurationListDeserializer handles this at runtime when lists are
  // configured via Spring Boot properties: e.g. index.localVariableNameInclusionExact[0]=foo
  // ---------------------------------------------------------------------------

  @Test
  void shouldDeserializeSpringIndexedListForLocalVariableNameInclusionExact() {
    // given — Spring Boot encodes list[0]=a, list[1]=b as a Map {"0": "a", "1": "b"}
    final var args =
        Map.<String, Object>of(
            "localVariableNameInclusionExact", Map.of("0", "localVar", "1", "other"));

    // when
    final var config = ExporterConfiguration.MAPPER.convertValue(args, IndexConfiguration.class);

    // then
    assertThat(config.getLocalVariableNameInclusionExact()).containsExactly("localVar", "other");
  }

  @Test
  void shouldDeserializeSpringIndexedListForRootVariableNameExclusionExact() {
    // given
    final var args =
        Map.<String, Object>of(
            "rootVariableNameExclusionExact", Map.of("0", "_secret", "1", "_internal"));

    // when
    final var config = ExporterConfiguration.MAPPER.convertValue(args, IndexConfiguration.class);

    // then
    assertThat(config.getRootVariableNameExclusionExact()).containsExactly("_secret", "_internal");
  }

  @Test
  void shouldDeserializeSpringIndexedListForLocalVariableValueTypeInclusion() {
    // given
    final var args =
        Map.<String, Object>of(
            "localVariableValueTypeInclusion", Map.of("0", "NUMBER", "1", "STRING"));

    // when
    final var config = ExporterConfiguration.MAPPER.convertValue(args, IndexConfiguration.class);

    // then
    assertThat(config.getLocalVariableValueTypeInclusion()).containsExactly("NUMBER", "STRING");
  }
}

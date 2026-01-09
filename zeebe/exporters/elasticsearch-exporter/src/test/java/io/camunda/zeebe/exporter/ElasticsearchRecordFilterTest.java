/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for the variable name inclusion filtering in ElasticsearchRecordFilter. */
final class ElasticsearchRecordFilterTest {

  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));

  @Test
  void shouldAcceptVariableWhenNameMatchesInclusionPrefix() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionRegex = "included.*;allowed";
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("includedVariable")
            .build();

    // when
    final var accepted = filter.acceptValue(variableValue);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldNotAcceptVariableWhenNameDoesNotMatchInclusionPrefix() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionRegex = "included.*;allowed.*";
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("excludedVariable")
            .build();

    // when
    final var accepted = filter.acceptValue(variableValue);

    // then
    assertThat(accepted).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      value = {
        "included.* | includedVariable | true",
        "included.* | includedAnotherName | true",
        "included | included | true",
        "allowed.* | allowedVariable | true",
        "allowed.* | allowedAnotherName | true",
        "var.* | variable | true",
        "var.* | anotherVar | false",
        ".*Var.* | anotherVar | true",
      },
      delimiter = '|')
  void shouldFilterVariablesByNamePrefix(
      final String inclusionPrefix, final String variableName, final boolean expectedAccepted) {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionRegex = inclusionPrefix;
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName(variableName)
            .build();

    // when
    final var accepted = filter.acceptValue(variableValue);

    // then
    assertThat(accepted).isEqualTo(expectedAccepted);
  }

  @Test
  void shouldAcceptAllVariablesWhenInclusionIsEmpty() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionRegex = "";
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("anyVariable")
            .build();

    // when
    final var accepted = filter.acceptValue(variableValue);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldTrimWhitespaceFromInclusionPrefixes() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionRegex = "  included.*  ; allowed  ";
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("includedVariable")
            .build();

    // when
    final var accepted = filter.acceptValue(variableValue);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldAcceptNonVariableRecords() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionRegex = "included";
    final var filter = createFilter(config);

    // when - acceptValue with a non-variable record value
    final var accepted =
        filter.acceptValue(factory.generateObject(ValueType.PROCESS_INSTANCE.getClass()));

    // then
    assertThat(accepted).isTrue();
  }

  @ParameterizedTest(name = "blank inclusion -> accept variable: [{0}]")
  @CsvSource({
    ",", // null
    "'',", // empty string
    "'   '," // whitespace-only
  })
  void shouldAcceptAllVariablesWhenInclusionIsBlank(final String inclusion) {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variable = true;
    config.index.variableNameInclusionRegex = inclusion; // may be null/empty/whitespace
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("anyVariable")
            .build();

    // when
    final var accepted = filter.acceptValue(variableValue);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldAcceptVariablesWithCommaSeparatedPrefixes() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variable = true;
    config.index.variableNameInclusionRegex = "included.*;allowed.*";
    final var filter = createFilter(config);

    final var includedVar =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("includedVariable")
            .build();
    final var allowedVar =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("allowedVariable")
            .build();

    // when
    final var includedAccepted = filter.acceptValue(includedVar);
    final var allowedAccepted = filter.acceptValue(allowedVar);

    // then
    assertThat(includedAccepted).isTrue();
    assertThat(allowedAccepted).isTrue();
  }

  private ElasticsearchRecordFilter createFilter(final ElasticsearchExporterConfiguration config) {
    return new ElasticsearchRecordFilter(config);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class DmnParsingTest {

  private static final String VALID_DECISION_TABLE = "/decision-table.dmn";
  private static final String INVALID_DECISION_TABLE =
      "/decision-table-with-invalid-expression.dmn";
  private static final String VALID_DRG = "/drg-force-user.dmn";

  private final DecisionEngine decisionEngine = DecisionEngineFactory.createDecisionEngine();

  @Test
  void shouldRejectEmptyInputStream() {
    // given
    final InputStream inputStream = null;

    // when/then
    assertThatThrownBy(() -> decisionEngine.parse(inputStream))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The input stream must not be null");
  }

  @Test
  void shouldRejectInvalidInputStream() {
    // given
    final InputStream inputStream = new ByteArrayInputStream("invalid DMN".getBytes());

    // when
    final var parsedDrg = decisionEngine.parse(inputStream);

    // then
    assertThat(parsedDrg.isValid())
        .describedAs("Expect that the DMN is not parsed successfully")
        .isFalse();

    assertThat(parsedDrg.getFailureMessage()).startsWith("Failed to parse DMN");
  }

  @Test
  void shouldParseDecisionTable() {
    // given
    final var inputStream = getClass().getResourceAsStream(VALID_DECISION_TABLE);

    // when
    final var parsedDrg = decisionEngine.parse(inputStream);

    // then
    assertThat(parsedDrg.isValid())
        .describedAs("Expect that the DMN is parsed successfully")
        .isTrue();

    assertThat(parsedDrg.getId()).isEqualTo("force-users");
    assertThat(parsedDrg.getName()).isEqualTo("Force Users");
    assertThat(parsedDrg.getNamespace()).isEqualTo("http://camunda.org/schema/1.0/dmn");

    assertThat(parsedDrg.getDecisions())
        .hasSize(1)
        .extracting(ParsedDecision::getId, ParsedDecision::getName)
        .contains(tuple("jedi-or-sith", "Jedi or Sith"));

    assertThat(parsedDrg.getFailureMessage()).isNull();
  }

  @Test
  void shouldReturnAllParsedDecisions() {
    // given
    final var inputStream = getClass().getResourceAsStream(VALID_DRG);

    // when
    final var parsedDrg = decisionEngine.parse(inputStream);

    // then
    assertThat(parsedDrg.isValid())
        .describedAs("Expect that the DMN is parsed successfully")
        .isTrue();

    assertThat(parsedDrg.getDecisions())
        .hasSize(2)
        .extracting(ParsedDecision::getId, ParsedDecision::getName)
        .contains(tuple("jedi-or-sith", "Jedi or Sith"), tuple("force-user", "Which force user?"));
  }

  @Test
  void shouldReportParseFailure() {
    // given
    final var inputStream = getClass().getResourceAsStream(INVALID_DECISION_TABLE);

    // when
    final var parsedDrg = decisionEngine.parse(inputStream);

    // then
    assertThat(parsedDrg.isValid())
        .describedAs("Expect that the DMN is not parsed successfully")
        .isFalse();

    assertThat(parsedDrg.getFailureMessage())
        .startsWith("FEEL unary-tests: failed to parse expression");

    assertThat(parsedDrg.getId()).isNull();
    assertThat(parsedDrg.getName()).isNull();
    assertThat(parsedDrg.getNamespace()).isNull();
    assertThat(parsedDrg.getDecisions()).isEmpty();
  }
}

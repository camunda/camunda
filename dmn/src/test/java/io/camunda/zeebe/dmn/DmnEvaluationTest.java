/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.dmn.impl.ParseFailureMessage;
import io.camunda.zeebe.dmn.impl.VariablesContext;
import io.camunda.zeebe.test.util.JsonUtil;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DmnEvaluationTest {

  private static final String VALID_DRG = "/drg-force-user.dmn";
  private static final String IDENTITY_DRG = "/identity-decision.dmn";

  private final DecisionEngine decisionEngine = DecisionEngineFactory.createDecisionEngine();

  @Test
  @DisplayName("Should result in failure when decision with decisionId does not exist in DRG")
  void shouldResultInFailureWhenDecisionNotInDrg() {
    // given
    final var inputStream = getClass().getResourceAsStream(VALID_DRG);
    final var parsedDrg = decisionEngine.parse(inputStream);

    // when
    final var result = decisionEngine.evaluateDecisionById(parsedDrg, "not_in_drg", null);

    // then
    assertThat(result.isFailure())
        .describedAs("Expect that the result is not evaluated successfully")
        .isTrue();

    assertThat(result.getFailureMessage())
        .isNotNull()
        .describedAs(
            "Expect that the evaluation failed because the DRG does not contain the referred decision")
        .contains("no decision found with id 'not_in_drg'");

    assertThat(result.getFailedDecisionId())
        .describedAs(
            "Expect that the failed decision id is the target decision id if the decision was not evaluated")
        .isEqualTo("not_in_drg");

    assertThat(result.getOutput())
        .describedAs("Expect that a failed evaluation has no output")
        .isNull();
  }

  @Test
  @DisplayName("Should result in failure when context does not contain required value")
  void shouldResultInFailureWhenContextMissesRequiredValue() {
    // given
    final var inputStream = getClass().getResourceAsStream(VALID_DRG);
    final var parsedDrg = decisionEngine.parse(inputStream);

    // when
    final var result = decisionEngine.evaluateDecisionById(parsedDrg, "jedi_or_sith", null);

    // then
    assertThat(result.isFailure())
        .describedAs("Expect that the result is not evaluated successfully")
        .isTrue();

    assertThat(result.getFailureMessage())
        .isNotNull()
        .describedAs("Expect that the evaluation failed because of a missing variable")
        .contains("no variable found for");

    assertThat(result.getFailedDecisionId()).isEqualTo("jedi_or_sith");

    assertThat(result.getOutput())
        .describedAs("Expect that a failed evaluation has no output")
        .isNull();
  }

  @Test
  @DisplayName("Should result in failure when invalid drg used")
  void shouldResultInFailureWhenDrgInvalid() {
    // given
    final var parsedDrg = new ParseFailureMessage("Example Parse Failure");

    // when
    final var result =
        decisionEngine.evaluateDecisionById(
            parsedDrg,
            "jedi_or_sith",
            new VariablesContext(Map.of("lightsaberColor", asMsgPack("\"blue\""))));

    // then
    assertThat(result.isFailure())
        .describedAs("Expect that the result is not evaluated successfully")
        .isTrue();

    assertThat(result.getFailureMessage())
        .isNotNull()
        .describedAs("Expect that the evaluation failed because the DRG is invalid")
        .contains("the decision requirements graph is invalid");

    assertThat(result.getFailedDecisionId())
        .describedAs(
            "Expect that the failed decision id is the target decision id if the DRG is invalid")
        .isEqualTo("jedi_or_sith");

    assertThat(result.getOutput())
        .describedAs("Expect that a successful result has no output")
        .isNull();
  }

  @Test
  @DisplayName("Should result in decision with output when successful")
  void shouldResultInDecisionOutputWhenSuccessful() {
    // given
    final var inputStream = getClass().getResourceAsStream(VALID_DRG);
    final var parsedDrg = decisionEngine.parse(inputStream);

    // when
    final var result =
        decisionEngine.evaluateDecisionById(
            parsedDrg,
            "jedi_or_sith",
            new VariablesContext(Map.of("lightsaberColor", asMsgPack("\"blue\""))));

    // then
    assertThat(result.isFailure())
        .describedAs("Expect that the result is evaluated successfully")
        .isFalse();

    assertThat(result.getFailureMessage())
        .describedAs("Expect that a successful result has no failure message")
        .isNull();

    assertThat(result.getFailedDecisionId())
        .describedAs("Expect that a successful result has no failed decision")
        .isNull();

    assertThat(result.getOutput())
        .describedAs("Expect that a successful result has some output")
        .isNotNull();
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @DisplayName("If successfully evaluated, the output")
  class OutputTests {

    Stream<Arguments> outputs() {
      return Stream.of(
          Arguments.of("value"),
          Arguments.of(1),
          Arguments.of(true),
          Arguments.of((Object) null),
          Arguments.of(List.of(1, 2, 3)),
          Arguments.of(Map.of("x", 1, "y", true, "z", List.of(1, 2, 3))));
    }

    @ParameterizedTest
    @MethodSource("outputs")
    @DisplayName("Should return a message pack output")
    void shouldReturnAMsgPackOutput(final Object value) {
      // given
      final var inputStream = getClass().getResourceAsStream(IDENTITY_DRG);
      final var parsedDrg = decisionEngine.parse(inputStream);

      // when
      final var encodedValue = asMsgPack(JsonUtil.toJson(value));
      final var result =
          decisionEngine.evaluateDecisionById(
              parsedDrg, "identity", new VariablesContext(Map.of("input", encodedValue)));

      // then
      assertThat(result.getOutput())
          .describedAs("Expect that a successful result has a message pack output")
          .isEqualTo(encodedValue);
    }
  }
}

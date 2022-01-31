/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.camunda.zeebe.test.util.MsgPackUtil.assertEquality;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.dmn.impl.ParseFailureMessage;
import io.camunda.zeebe.dmn.impl.VariablesContext;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DmnEvaluatedDecisionsTest {

  private static final String VALID_DRG = "/drg-force-user.dmn";

  private final DecisionEngine decisionEngine = DecisionEngineFactory.createDecisionEngine();

  @Test
  @DisplayName("Should return no evaluated decisions if the decision is invalid")
  void shouldReturnNullIfDecisionIsInvalid() {
    // given
    final var invalidDecision = new ParseFailureMessage("invalid decision");

    // when
    final var result = decisionEngine.evaluateDecisionById(invalidDecision, "decision", null);

    // then
    assertThat(result.isFailure())
        .describedAs("Expect that the result is not evaluated successfully")
        .isTrue();

    assertThat(result.getEvaluatedDecisions())
        .describedAs("Expect that an invalid decision has no evaluated results")
        .isEmpty();
  }

  @Test
  @DisplayName("Should return evaluated decision table")
  void shouldReturnResultOfEvaluatedDecisionTable() {
    // given
    final var inputStream = getClass().getResourceAsStream(VALID_DRG);
    final var parsedDrg = decisionEngine.parse(inputStream);

    // when
    final var context = new VariablesContext(Map.of("lightsaberColor", asMsgPack("'blue'")));
    final var result = decisionEngine.evaluateDecisionById(parsedDrg, "jedi_or_sith", context);

    // then
    assertThat(result.getEvaluatedDecisions()).hasSize(1);

    final var evaluatedDecision = result.getEvaluatedDecisions().get(0);
    assertThat(evaluatedDecision.decisionId()).isEqualTo("jedi_or_sith");
    assertThat(evaluatedDecision.decisionName()).isEqualTo("Jedi or Sith");
    assertThat(evaluatedDecision.decisionType()).isEqualTo(DecisionType.DECISION_TABLE);
    assertEquality(evaluatedDecision.decisionOutput(), "'Jedi'");

    assertThat(evaluatedDecision.evaluatedInputs()).hasSize(1);

    final var evaluatedInput = evaluatedDecision.evaluatedInputs().get(0);
    assertThat(evaluatedInput.inputId()).isEqualTo("Input_1");
    assertThat(evaluatedInput.inputName()).isEqualTo("Lightsaber color");
    assertEquality(evaluatedInput.inputValue(), "'blue'");

    assertThat(evaluatedDecision.matchedRules()).hasSize(1);

    final var matchedRules = evaluatedDecision.matchedRules().get(0);
    assertThat(matchedRules.ruleId()).isEqualTo("DecisionRule_0zumznl");
    assertThat(matchedRules.ruleIndex()).isEqualTo(1);

    assertThat(matchedRules.evaluatedOutputs()).hasSize(1);

    final var evaluatedOutput = matchedRules.evaluatedOutputs().get(0);
    assertThat(evaluatedOutput.outputId()).isEqualTo("Output_1");
    assertThat(evaluatedOutput.outputName()).isEqualTo("jedi_or_sith");
    assertEquality(evaluatedOutput.outputValue(), "'Jedi'");
  }

  @Test
  @DisplayName("Should return all evaluated decisions")
  void shouldReturnResultOfAllEvaluatedDecisions() {
    // given
    final var inputStream = getClass().getResourceAsStream(VALID_DRG);
    final var parsedDrg = decisionEngine.parse(inputStream);

    // when
    final var context =
        new VariablesContext(
            Map.ofEntries(
                entry("lightsaberColor", asMsgPack("'blue'")), entry("height", asMsgPack("182"))));
    final var result = decisionEngine.evaluateDecisionById(parsedDrg, "force_user", context);

    // then
    assertThat(result.getEvaluatedDecisions())
        .hasSize(2)
        .extracting(EvaluatedDecision::decisionId, EvaluatedDecision::decisionName)
        .containsExactly(
            tuple("jedi_or_sith", "Jedi or Sith"), tuple("force_user", "Which force user?"));

    final var evaluatedDecisions = result.getEvaluatedDecisions();
    assertEquality(evaluatedDecisions.get(0).decisionOutput(), "'Jedi'");
    assertEquality(evaluatedDecisions.get(1).decisionOutput(), "'Obi-Wan Kenobi'");
  }

  @Test
  @DisplayName("Should return partial output if evaluation fails")
  void shouldReturnPartialResultIfEvaluationFails() {
    // given
    final var inputStream = getClass().getResourceAsStream(VALID_DRG);
    final var parsedDrg = decisionEngine.parse(inputStream);

    // when
    final var context =
        new VariablesContext(Map.ofEntries(entry("lightsaberColor", asMsgPack("'blue'"))));
    final var result = decisionEngine.evaluateDecisionById(parsedDrg, "force_user", context);

    // then
    assertThat(result.isFailure())
        .describedAs("Expect that the result is not evaluated successfully")
        .isTrue();

    assertThat(result.getFailureMessage())
        .isEqualTo(
            "Expected to evaluate decision 'force_user', but failed to evaluate expression 'height': "
                + "no variable found for name 'height'");

    assertThat(result.getEvaluatedDecisions())
        .hasSize(2)
        .extracting(EvaluatedDecision::decisionId, EvaluatedDecision::decisionName)
        .containsExactly(
            tuple("jedi_or_sith", "Jedi or Sith"), tuple("force_user", "Which force user?"));

    final var evaluatedDecisions = result.getEvaluatedDecisions();
    assertEquality(evaluatedDecisions.get(0).decisionOutput(), "'Jedi'");
    assertEquality(evaluatedDecisions.get(1).decisionOutput(), "null");
  }
}

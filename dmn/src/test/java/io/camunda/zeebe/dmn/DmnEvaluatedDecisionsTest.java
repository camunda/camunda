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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
    assertThat(evaluatedOutput.outputName()).isEqualTo("Jedi or Sith");
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
        .describedAs(
            "Expect to contain all evaluated decisions in the order of their evaluation, starting with the required decision")
        .containsExactly(
            tuple("jedi_or_sith", "Jedi or Sith"), tuple("force_user", "Which force user?"));

    final var evaluatedDecisions = result.getEvaluatedDecisions();
    assertEquality(evaluatedDecisions.get(0).decisionOutput(), "'Jedi'");
    assertEquality(evaluatedDecisions.get(1).decisionOutput(), "'Obi-Wan Kenobi'");
  }

  @Test
  @DisplayName("Should return output of required decision if evaluation fails on root decision")
  void shouldReturnOutputOfRequiredDecisionIfEvaluationFailsOnRootDecision() {
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

    assertThat(result.getFailedDecisionId()).isEqualTo("force_user");

    assertThat(result.getEvaluatedDecisions())
        .hasSize(2)
        .extracting(EvaluatedDecision::decisionId, EvaluatedDecision::decisionName)
        .containsExactly(
            tuple("jedi_or_sith", "Jedi or Sith"), tuple("force_user", "Which force user?"));

    final var evaluatedDecisions = result.getEvaluatedDecisions();
    assertEquality(evaluatedDecisions.get(0).decisionOutput(), "'Jedi'");
    assertEquality(evaluatedDecisions.get(1).decisionOutput(), "null");
  }

  @Test
  @DisplayName("Should return partial output if evaluation fails on required decision")
  void shouldReturnPartialOutputIfEvaluationFailsOnRequiredDecision() {
    // given
    final var inputStream = getClass().getResourceAsStream(VALID_DRG);
    final var parsedDrg = decisionEngine.parse(inputStream);

    // when
    final var result = decisionEngine.evaluateDecisionById(parsedDrg, "force_user", null);

    // then
    assertThat(result.isFailure())
        .describedAs("Expect that the result is not evaluated successfully")
        .isTrue();

    assertThat(result.getFailureMessage())
        .isEqualTo(
            """
            Expected to evaluate decision 'force_user', \
            but failed to evaluate expression 'lightsaberColor': \
            no variable found for name 'lightsaberColor'\
            """);

    assertThat(result.getFailedDecisionId()).isEqualTo("jedi_or_sith");

    assertThat(result.getEvaluatedDecisions()).hasSize(1);

    final var evaluatedDecision = result.getEvaluatedDecisions().get(0);
    assertThat(evaluatedDecision.decisionId()).isEqualTo("jedi_or_sith");
    assertEquality(evaluatedDecision.decisionOutput(), "null");
    assertThat(evaluatedDecision.evaluatedInputs()).hasSize(0);
    assertThat(evaluatedDecision.matchedRules()).hasSize(0);
  }

  private DecisionEvaluationResult evaluateDecision(
      final String resource, final String decisionId) {
    final var inputStream = getClass().getResourceAsStream(resource);
    final var parsedDrg = decisionEngine.parse(inputStream);

    // when
    final var result = decisionEngine.evaluateDecisionById(parsedDrg, decisionId, null);

    // then
    assertThat(result.isFailure())
        .describedAs(
            "Expect that the decision is evaluated successfully: %s", result.getFailureMessage())
        .isFalse();

    return result;
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @DisplayName("If successfully evaluated, the decision")
  class DecisionTypeTests {

    private static final String DECISION_TYPES_DRG = "/drg-decision-types.dmn";

    Stream<DecisionTest> decisions() {
      return Stream.of(
          new DecisionTest("decision_table", DecisionType.DECISION_TABLE, "'okay'"),
          new DecisionTest("literal_expression", DecisionType.LITERAL_EXPRESSION, "'okay'"),
          new DecisionTest("context", DecisionType.CONTEXT, "{'is':'okay'}"),
          new DecisionTest("invocation", DecisionType.INVOCATION, "'okay'"),
          new DecisionTest("list", DecisionType.LIST, "['okay']"),
          new DecisionTest("relation", DecisionType.RELATION, "[{'is':'okay'}]"));
    }

    @ParameterizedTest
    @MethodSource("decisions")
    @DisplayName("Should return the type of the decision")
    void shouldReturnTheDecisionType(final DecisionTest test) {
      // when
      final var result = evaluateDecision(DECISION_TYPES_DRG, test.decisionId);

      // then
      assertThat(result.getEvaluatedDecisions())
          .extracting(EvaluatedDecision::decisionType)
          .contains(test.expectedType);
    }

    @ParameterizedTest
    @MethodSource("decisions")
    @DisplayName("Should return the output of the decision")
    void shouldReturnDecisionResult(final DecisionTest test) {
      // when
      final var result = evaluateDecision(DECISION_TYPES_DRG, test.decisionId);

      // then
      assertThat(result.getEvaluatedDecisions())
          .extracting(EvaluatedDecision::decisionOutput)
          .allSatisfy(decisionResult -> assertEquality(decisionResult, test.expectedResult));
    }

    record DecisionTest(String decisionId, DecisionType expectedType, String expectedResult) {}
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @DisplayName("If successfully evaluated, the decision table")
  class DecisionTableTest {

    // This drg contains different decision tables, each with a special case
    private static final String DRG_DECISION_TABLE = "/drg-decision-table-io-names.dmn";

    @Test
    @DisplayName("Should use input label as input name, if label defined")
    void shouldUseInputLabelAsInputName() {
      // when
      final var result = evaluateDecision(DRG_DECISION_TABLE, "labeled_input");

      // then
      assertThat(result.getEvaluatedDecisions())
          .flatMap(EvaluatedDecision::evaluatedInputs)
          .extracting(EvaluatedInput::inputName)
          .containsExactly("input_label_is_used_as_input_name");
    }

    @Test
    @DisplayName("Should use input expression as input name, if no label defined")
    void shouldUseInputExpressionAsInputName() {
      // when
      final var result = evaluateDecision(DRG_DECISION_TABLE, "unlabeled_input");

      // then
      assertThat(result.getEvaluatedDecisions())
          .flatMap(EvaluatedDecision::evaluatedInputs)
          .extracting(EvaluatedInput::inputName)
          // the expression is truncated at 30 chars when used as name
          .containsExactly("\"expression is used as input n");
    }

    @Test
    @DisplayName("Should use output label as output name, if label defined")
    void shouldUseOutputLabelAsOutputName() {
      // when
      final var result = evaluateDecision(DRG_DECISION_TABLE, "labeled_output");

      // then
      assertThat(result.getEvaluatedDecisions())
          .flatMap(EvaluatedDecision::matchedRules)
          .flatMap(MatchedRule::evaluatedOutputs)
          .extracting(EvaluatedOutput::outputName)
          .containsExactly("output_label_is_used_as_output_name");
    }

    @Test
    @DisplayName("Should use output name as output name, if no label defined")
    void shouldUseOutputNameAsOutputName() {
      // when
      final var result = evaluateDecision(DRG_DECISION_TABLE, "unlabeled_output");

      // then
      assertThat(result.getEvaluatedDecisions())
          .flatMap(EvaluatedDecision::matchedRules)
          .flatMap(MatchedRule::evaluatedOutputs)
          .extracting(EvaluatedOutput::outputName)
          .containsExactly("output_name_is_used_as_output_name");
    }
  }
}

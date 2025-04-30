/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

public class DecisionInstanceAssertj
    extends AbstractAssert<DecisionInstanceAssertj, DecisionSelector>
    implements DecisionInstanceAssert {

  private final CamundaDataSource dataSource;

  public DecisionInstanceAssertj(
      final CamundaDataSource dataSource, final DecisionSelector decisionSelector) {

    super(decisionSelector, DecisionInstanceAssert.class);
    this.dataSource = dataSource;
  }

  @Override
  public DecisionInstanceAssert isEvaluated() {
    awaitDecisionInstance(instance ->
        assertThat(instance.getState())
            .withFailMessage(
                "Expected [%s] to have been evaluated, but was %s",
                actual.describe(), formatState(instance.getState()))
            .isEqualTo(DecisionInstanceState.EVALUATED)
    );
    return this;
  }

  @Override
  public DecisionInstanceAssert hasName(final String expectedDefinitionName) {
    awaitDecisionInstance(instance ->
        assertThat(instance.getDecisionDefinitionName())
            .withFailMessage(
                "Expected [%s] to have name '%s', but was '%s'",
                actual.describe(), expectedDefinitionName, instance.getDecisionDefinitionName())
            .isEqualTo(expectedDefinitionName)
    );

    return this;
  }

  @Override
  public DecisionInstanceAssert hasId(final String expectedDefinitionId) {
    awaitDecisionInstance(instance ->
        assertThat(instance.getDecisionDefinitionId())
            .withFailMessage(
                "Expected [%s] to have id '%s', but was '%s'",
                actual.describe(), expectedDefinitionId, instance.getDecisionDefinitionId())
            .isEqualTo(expectedDefinitionId)
    );

    return this;
  }

  @Override
  public DecisionInstanceAssert hasVersion(final int expectedVersion) {
    awaitDecisionInstance(instance ->
        assertThat(instance.getDecisionDefinitionVersion())
            .withFailMessage(
                "Expected [%s] to have version %d, but was %d",
                actual.describe(), expectedVersion, instance.getDecisionDefinitionVersion())
            .isEqualTo(expectedVersion)
    );

    return this;
  }

  @Override
  public DecisionInstanceAssert hasOutput(final String expectedValue) {
    awaitDecisionInstance(instance ->
        assertThat(instance.getResult())
            .withFailMessage(
                "Expected [%s] to have output '%s', but was '%s'",
                actual.describe(),
                expectedValue,
                formatResult(instance.getResult()))
            .contains(expectedValue)
    );

    return this;
  }

  @Override
  public DecisionInstanceAssert hasMatchedRules(final int... expectedMatchedRuleIndexes) {
    awaitDecisionInstance(instance -> {
      final List<Integer> expectedMatches =
          Arrays.stream(expectedMatchedRuleIndexes).boxed().collect(Collectors.toList());
      final List<Integer> actualMatchedRuleIndices =
          instance.getMatchedRules().stream()
              .map(MatchedDecisionRule::getRuleIndex)
              .collect(Collectors.toList());

      assertThat(actualMatchedRuleIndices)
          .withFailMessage(
              "Expected [%s] to have matched rule indexes %s, but did not. Matches:\n"
                  + "\t- matched: %s\n"
                  + "\t- missing: %s\n"
                  + "\t- unexpected: %s",
              actual.describe(),
              Arrays.toString(expectedMatchedRuleIndexes),
              matchingRules(actualMatchedRuleIndices, expectedMatches),
              missingRules(actualMatchedRuleIndices, expectedMatches),
              unexpectedRules(actualMatchedRuleIndices, expectedMatches))
          .containsAll(expectedMatches);
    });

    return this;
  }

  private <T> List<T> matchingRules(final List<T> actualMatches, final List<T> expectedMatches) {
    return expectedMatches.stream().filter(actualMatches::contains).collect(Collectors.toList());
  }

  private <T> List<T> missingRules(final List<T> actualMatches, final List<T> expectedMatches) {
    return expectedMatches.stream()
        .filter(e -> actualMatches.stream().noneMatch(a -> Objects.equals(a, e)))
        .collect(Collectors.toList());
  }

  private <T> List<T> unexpectedRules(final List<T> actualMatches, final List<T> expectedMatches) {
    return actualMatches.stream()
        .filter(e -> expectedMatches.stream().noneMatch(a -> Objects.equals(a, e)))
        .collect(Collectors.toList());
  }

  private String formatResult(final String result) {
    if (result == null) {
      return "<None>";
    }
    if (result.startsWith("\"") && result.endsWith("\"")) {
      return result.substring(1, result.length() - 1);
    }
    return result;
  }

  private String formatState(final DecisionInstanceState state) {
    if (state == null) {
      return "not activated";
    }

    return state.name().toLowerCase();
  }

  private String formatEvaluatedOutputs(final List<MatchedDecisionRule> matchedRules) {
    if (matchedRules == null || matchedRules.isEmpty()) {
      return "\t- <None>";
    }

    return matchedRules.stream()
        .map(MatchedDecisionRule::getEvaluatedOutputs)
        .flatMap(Collection::stream)
        .map(
            o ->
                String.format(
                    "\t- %s (id: %s): %s", o.getOutputName(), o.getOutputId(), o.getOutputValue()))
        .collect(Collectors.joining("\n"));
  }

  private void awaitDecisionInstance(
      final Consumer<DecisionInstance> assertion
  ) {
    final AtomicReference<String> failureMessage = new AtomicReference<>("?");

    try {
      Awaitility.await()
          .timeout(Duration.ofSeconds(60))
          .ignoreException(ClientException.class)
          .untilAsserted(
              () -> dataSource.findDecisionInstances(actual::applyFilter),
              decisionInstances -> {
                final Optional<DecisionInstance> decisionInstance =
                    decisionInstances.stream().filter(actual::test).findFirst();

                try {
                  assertThat(decisionInstance).isPresent();
                  assertion.accept(decisionInstance.get());
                } catch (final AssertionError e) {
                  failureMessage.set(e.getMessage());
                  throw e;
                }
              });
    } catch (final ConditionTimeoutException ignore) {
      fail(failureMessage.get());
    }
  }
}

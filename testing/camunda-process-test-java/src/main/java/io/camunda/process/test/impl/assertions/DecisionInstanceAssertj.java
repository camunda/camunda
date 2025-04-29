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
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractAssert;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

// TODO assertj class for EvaluatedDecisionResponse
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
    final DecisionInstance decisionInstance = awaitDecisionInstance();

    assertThat(decisionInstance.getState())
        .withFailMessage(
            "Expected [%s] to have been evaluated, but was %s",
            actual.describe(), decisionInstance.getState())
        .isEqualTo(DecisionInstanceState.EVALUATED);

    return this;
  }

  @Override
  public DecisionInstanceAssert isFailed() {
    final DecisionInstance decisionInstance = awaitDecisionInstance();

    assertThat(decisionInstance.getState())
        .withFailMessage(
            "Expected [%s] to have been evaluated, but was %s",
            actual.describe(), decisionInstance.getState())
        .isEqualTo(DecisionInstanceState.FAILED);

    return this;
  }

  @Override
  public DecisionInstanceAssert hasOutput(final String expectedOutputValue) {
    final DecisionInstance decisionInstance = awaitDecisionInstance();

    assertThat(decisionInstance.getMatchedRules())
        .withFailMessage(
            "Expected [%s] to have output %s, but does not. Outputs:\n",
            actual.describe(), expectedOutputValue, "") // formatOutputs(actual))
        .anyMatch(rule ->
            rule.getEvaluatedOutputs().stream()
                .anyMatch(o -> o.getOutputValue().equalsIgnoreCase(expectedOutputValue)));

    return this;
  }

  @Override
  public DecisionInstanceAssert hasOutput(
      final String expectedOutputName, final String expectedOutputValue) {

    final DecisionInstance decisionInstance = awaitDecisionInstance();

    assertThat(decisionInstance.getMatchedRules())
        .withFailMessage(
            "Expected [%s] to have output %s, but does not. Outputs:\n",
            actual.describe(), expectedOutputValue, "") // formatOutputs(actual))
        .anyMatch(rule ->
            rule.getEvaluatedOutputs().stream()
                .anyMatch(o ->
                    o.getOutputValue().equalsIgnoreCase(expectedOutputValue) &&
                    o.getOutputName().equalsIgnoreCase(expectedOutputName)));

    return this;
  }

  @Override
  public DecisionInstanceAssert hasMatchedRules(final int a, final int b) {

    return this;
  }

  private DecisionInstance awaitDecisionInstance() {
    final AtomicReference<DecisionInstance> actualDecisionInstance = new AtomicReference<>();

    try {
      Awaitility.await()
          .ignoreException(ClientException.class)
          .untilAsserted(
              () -> dataSource.findDecisionInstances(actual::applyFilter),
              decisionInstances -> {
                final Optional<DecisionInstance> decisionInstance =
                    decisionInstances.stream().filter(actual::test).findFirst();
                assertThat(decisionInstance).isPresent();
                actualDecisionInstance.set(decisionInstance.get());
              });
    } catch (final ConditionTimeoutException ignore) {
      fail("No decision instance [%s] found", actual.describe());
    }

    return actualDecisionInstance.get();
  }
}

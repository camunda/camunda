/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;
import static io.camunda.util.CollectionUtil.collectValuesAsList;

import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record DecisionInstanceFilter(
    List<Operation<Long>> decisionInstanceKeyOperations,
    List<Operation<String>> decisionInstanceIdOperations,
    List<DecisionInstanceState> states,
    List<Operation<OffsetDateTime>> evaluationDateOperations,
    List<Operation<String>> evaluationFailureOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Long>> decisionDefinitionKeyOperations,
    List<Operation<String>> decisionDefinitionIdOperations,
    List<Operation<String>> decisionDefinitionNameOperations,
    List<Operation<Integer>> decisionDefinitionVersionOperations,
    List<DecisionDefinitionType> decisionTypes,
    List<Operation<String>> tenantIdOperations)
    implements FilterBase {

  public static DecisionInstanceFilter of(
      final Function<DecisionInstanceFilter.Builder, ObjectBuilder<DecisionInstanceFilter>> fn) {
    return FilterBuilders.decisionInstance(fn);
  }

  public static final class Builder implements ObjectBuilder<DecisionInstanceFilter> {

    private List<Operation<Long>> decisionInstanceKeyOperations;
    private List<Operation<String>> decisionInstanceIdOperations;
    private List<DecisionInstanceState> states;
    private List<Operation<OffsetDateTime>> evaluationDateOperations;
    private List<Operation<String>> evaluationFailureOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<Long>> decisionDefinitionKeyOperations;
    private List<Operation<String>> decisionDefinitionIdOperations;
    private List<Operation<String>> decisionDefinitionNameOperations;
    private List<Operation<Integer>> decisionDefinitionVersionOperations;
    private List<DecisionDefinitionType> decisionTypes;
    private List<Operation<String>> tenantIdOperations;

    public Builder decisionInstanceKeyOperations(final List<Operation<Long>> operations) {
      decisionInstanceKeyOperations = addValuesToList(decisionInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder decisionInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return decisionInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder decisionInstanceIdOperations(final List<Operation<String>> operations) {
      decisionInstanceIdOperations = addValuesToList(decisionInstanceIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder decisionInstanceIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return decisionInstanceIdOperations(collectValues(operation, operations));
    }

    public Builder states(final List<DecisionInstanceState> values) {
      states = addValuesToList(states, values);
      return this;
    }

    public Builder states(final DecisionInstanceState... values) {
      return states(collectValuesAsList(values));
    }

    public Builder evaluationDateOperations(final List<Operation<OffsetDateTime>> operations) {
      evaluationDateOperations = addValuesToList(evaluationDateOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder evaluationDateOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return evaluationDateOperations(collectValues(operation, operations));
    }

    public Builder evaluationFailureOperations(final List<Operation<String>> operations) {
      evaluationFailureOperations = addValuesToList(evaluationFailureOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder evaluationFailureOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return evaluationFailureOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionKeyOperations(final List<Operation<Long>> operations) {
      processDefinitionKeyOperations = addValuesToList(processDefinitionKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processDefinitionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processDefinitionKeyOperations(collectValues(operation, operations));
    }

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder decisionDefinitionKeyOperations(final List<Operation<Long>> operations) {
      decisionDefinitionKeyOperations =
          addValuesToList(decisionDefinitionKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder decisionDefinitionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return decisionDefinitionKeyOperations(collectValues(operation, operations));
    }

    public Builder decisionDefinitionIdOperations(final List<Operation<String>> operations) {
      decisionDefinitionIdOperations = addValuesToList(decisionDefinitionIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder decisionDefinitionIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return decisionDefinitionIdOperations(collectValues(operation, operations));
    }

    public Builder decisionDefinitionNameOperations(final List<Operation<String>> operations) {
      decisionDefinitionNameOperations =
          addValuesToList(decisionDefinitionNameOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder decisionDefinitionNameOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return decisionDefinitionNameOperations(collectValues(operation, operations));
    }

    public Builder decisionDefinitionVersionOperations(final List<Operation<Integer>> operations) {
      decisionDefinitionVersionOperations =
          addValuesToList(decisionDefinitionVersionOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder decisionDefinitionVersionOperations(
        final Operation<Integer> operation, final Operation<Integer>... operations) {
      return decisionDefinitionVersionOperations(collectValues(operation, operations));
    }

    public Builder decisionTypes(final List<DecisionDefinitionType> values) {
      decisionTypes = addValuesToList(decisionTypes, values);
      return this;
    }

    public Builder decisionTypes(final DecisionDefinitionType... values) {
      return decisionTypes(collectValuesAsList(values));
    }

    public Builder tenantIdOperations(final List<Operation<String>> operations) {
      tenantIdOperations = addValuesToList(tenantIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder tenantIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return tenantIdOperations(collectValues(operation, operations));
    }

    @Override
    public DecisionInstanceFilter build() {
      return new DecisionInstanceFilter(
          Objects.requireNonNullElse(decisionInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(decisionInstanceIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(states, Collections.emptyList()),
          Objects.requireNonNullElse(evaluationDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(evaluationFailureOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionNameOperations, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionVersionOperations, Collections.emptyList()),
          Objects.requireNonNullElse(decisionTypes, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()));
    }
  }
}

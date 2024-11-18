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
import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record DecisionInstanceFilter(
    List<Long> decisionInstanceKeys,
    List<String> decisionInstanceIds,
    List<DecisionInstanceState> states,
    List<Operation<OffsetDateTime>> evaluationDateOperations,
    List<String> evaluationFailures,
    List<Long> processDefinitionKeys,
    List<Long> processInstanceKeys,
    List<Operation<Long>> decisionDefinitionKeyOperations,
    List<String> decisionDefinitionIds,
    List<String> decisionDefinitionNames,
    List<Integer> decisionDefinitionVersions,
    List<DecisionDefinitionType> decisionTypes,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DecisionInstanceFilter> {

    private List<Long> decisionInstanceKeys;
    private List<String> decisionInstanceIds;
    private List<DecisionInstanceState> states;
    private List<Operation<OffsetDateTime>> evaluationDateOperations;
    private List<String> evaluationFailures;
    private List<Long> processDefinitionKeys;
    private List<Long> processInstanceKeys;
    private List<Operation<Long>> decisionDefinitionKeyOperations;
    private List<String> decisionDefinitionIds;
    private List<String> decisionDefinitionNames;
    private List<Integer> decisionDefinitionVersions;
    private List<DecisionDefinitionType> decisionTypes;
    private List<String> tenantIds;

    public Builder decisionInstanceKeys(final List<Long> values) {
      decisionInstanceKeys = addValuesToList(decisionInstanceKeys, values);
      return this;
    }

    public Builder decisionInstanceKeys(final Long... values) {
      return decisionInstanceKeys(collectValuesAsList(values));
    }

    public Builder decisionInstanceIds(final List<String> values) {
      decisionInstanceIds = addValuesToList(decisionInstanceIds, values);
      return this;
    }

    public Builder decisionInstanceIds(final String... values) {
      return decisionInstanceIds(collectValuesAsList(values));
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

    public Builder evaluationFailures(final List<String> values) {
      evaluationFailures = addValuesToList(evaluationFailures, values);
      return this;
    }

    public Builder evaluationFailures(final String... values) {
      return evaluationFailures(collectValuesAsList(values));
    }

    public Builder processDefinitionKeys(final List<Long> values) {
      processDefinitionKeys = addValuesToList(processDefinitionKeys, values);
      return this;
    }

    public Builder processDefinitionKeys(final Long... values) {
      return processDefinitionKeys(collectValuesAsList(values));
    }

    public Builder processInstanceKeys(final List<Long> values) {
      processInstanceKeys = addValuesToList(processInstanceKeys, values);
      return this;
    }

    public Builder processInstanceKeys(final Long... values) {
      return processInstanceKeys(collectValuesAsList(values));
    }

    public Builder decisionDefinitionKeyOperations(final List<Operation<Long>> operations) {
      decisionDefinitionKeyOperations =
          addValuesToList(decisionDefinitionKeyOperations, operations);
      return this;
    }

    public Builder decisionDefinitionKeys(final Long value, final Long... values) {
      return decisionDefinitionKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder decisionDefinitionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return decisionDefinitionKeyOperations(collectValues(operation, operations));
    }

    public Builder decisionDefinitionIds(final List<String> values) {
      decisionDefinitionIds = addValuesToList(decisionDefinitionIds, values);
      return this;
    }

    public Builder decisionDefinitionIds(final String... values) {
      return decisionDefinitionIds(collectValuesAsList(values));
    }

    public Builder decisionDefinitionNames(final List<String> values) {
      decisionDefinitionNames = addValuesToList(decisionDefinitionNames, values);
      return this;
    }

    public Builder decisionDefinitionNames(final String... values) {
      return decisionDefinitionNames(collectValuesAsList(values));
    }

    public Builder decisionDefinitionVersions(final List<Integer> values) {
      decisionDefinitionVersions = addValuesToList(decisionDefinitionVersions, values);
      return this;
    }

    public Builder decisionDefinitionVersions(final Integer... values) {
      return decisionDefinitionVersions(collectValuesAsList(values));
    }

    public Builder decisionTypes(final List<DecisionDefinitionType> values) {
      decisionTypes = addValuesToList(decisionTypes, values);
      return this;
    }

    public Builder decisionTypes(final DecisionDefinitionType... values) {
      return decisionTypes(collectValuesAsList(values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder tenantIds(final String... values) {
      return tenantIds(collectValuesAsList(values));
    }

    @Override
    public DecisionInstanceFilter build() {
      return new DecisionInstanceFilter(
          Objects.requireNonNullElse(decisionInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(decisionInstanceIds, Collections.emptyList()),
          Objects.requireNonNullElse(states, Collections.emptyList()),
          Objects.requireNonNullElse(evaluationDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(evaluationFailures, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionIds, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionNames, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionVersions, Collections.emptyList()),
          Objects.requireNonNullElse(decisionTypes, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}

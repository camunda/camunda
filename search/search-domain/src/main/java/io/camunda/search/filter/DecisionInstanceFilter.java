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
import java.util.function.Function;

public record DecisionInstanceFilter(
    List<Long> decisionInstanceKeys,
    List<Operation<String>> decisionInstanceIdOperations,
    List<Operation<String>> stateOperations,
    List<Operation<OffsetDateTime>> evaluationDateOperations,
    List<String> evaluationFailures,
    List<Long> processDefinitionKeys,
    List<Long> processInstanceKeys,
    List<Operation<Long>> flowNodeInstanceKeyOperations,
    List<Operation<Long>> decisionDefinitionKeyOperations,
    List<String> decisionDefinitionIds,
    List<String> decisionDefinitionNames,
    List<Integer> decisionDefinitionVersions,
    List<DecisionDefinitionType> decisionTypes,
    List<Operation<Long>> rootDecisionDefinitionKeyOperations,
    List<Operation<Long>> decisionRequirementsKeyOperations,
    List<String> tenantIds,
    Integer partitionId)
    implements FilterBase {

  public static DecisionInstanceFilter of(
      final Function<DecisionInstanceFilter.Builder, ObjectBuilder<DecisionInstanceFilter>> fn) {
    return FilterBuilders.decisionInstance(fn);
  }

  public Builder toBuilder() {
    return new Builder()
        .decisionInstanceKeys(decisionInstanceKeys)
        .decisionInstanceIdOperations(decisionInstanceIdOperations)
        .stateOperations(stateOperations)
        .evaluationDateOperations(evaluationDateOperations)
        .evaluationFailures(evaluationFailures)
        .processDefinitionKeys(processDefinitionKeys)
        .processInstanceKeys(processInstanceKeys)
        .flowNodeInstanceKeyOperations(flowNodeInstanceKeyOperations)
        .decisionDefinitionKeyOperations(decisionDefinitionKeyOperations)
        .decisionDefinitionIds(decisionDefinitionIds)
        .decisionDefinitionNames(decisionDefinitionNames)
        .decisionDefinitionVersions(decisionDefinitionVersions)
        .decisionTypes(decisionTypes)
        .rootDecisionDefinitionKeyOperations(rootDecisionDefinitionKeyOperations)
        .decisionRequirementsKeyOperations(decisionRequirementsKeyOperations)
        .tenantIds(tenantIds)
        .partitionId(partitionId);
  }

  public static final class Builder implements ObjectBuilder<DecisionInstanceFilter> {

    private List<Long> decisionInstanceKeys;
    private List<Operation<String>> decisionInstanceIdOperations;
    private List<Operation<String>> stateOperations;
    private List<Operation<OffsetDateTime>> evaluationDateOperations;
    private List<String> evaluationFailures;
    private List<Long> processDefinitionKeys;
    private List<Long> processInstanceKeys;
    private List<Operation<Long>> flowNodeInstanceKeyOperations;
    private List<Operation<Long>> decisionDefinitionKeyOperations;
    private List<String> decisionDefinitionIds;
    private List<String> decisionDefinitionNames;
    private List<Integer> decisionDefinitionVersions;
    private List<DecisionDefinitionType> decisionTypes;
    private List<Operation<Long>> rootDecisionDefinitionKeyOperations;
    private List<Operation<Long>> decisionRequirementsKeyOperations;
    private List<String> tenantIds;
    private Integer partitionId;

    public Builder decisionInstanceKeys(final List<Long> values) {
      decisionInstanceKeys = addValuesToList(decisionInstanceKeys, values);
      return this;
    }

    public Builder decisionInstanceKeys(final Long... values) {
      return decisionInstanceKeys(collectValuesAsList(values));
    }

    public Builder decisionInstanceIds(final String value, final String... values) {
      return decisionInstanceIdOperations(FilterUtil.mapDefaultToOperation(value, values));
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

    public Builder states(
        final DecisionInstanceState value, final DecisionInstanceState... values) {
      return stateOperations(FilterUtil.mapDefaultToOperation(Enum::name, value, values));
    }

    public Builder stateOperations(final List<Operation<String>> operations) {
      stateOperations = addValuesToList(stateOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder stateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return stateOperations(collectValues(operation, operations));
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

    public Builder flowNodeInstanceKeyOperations(final List<Operation<Long>> operations) {
      flowNodeInstanceKeyOperations = addValuesToList(flowNodeInstanceKeyOperations, operations);
      return this;
    }

    public Builder flowNodeInstanceKeys(final Long value, final Long... values) {
      return flowNodeInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder flowNodeInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return flowNodeInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder decisionDefinitionKeyOperations(final List<Operation<Long>> operations) {
      decisionDefinitionKeyOperations =
          addValuesToList(decisionDefinitionKeyOperations, operations);
      return this;
    }

    public Builder decisionDefinitionKeys(final Long value, final Long... values) {
      return decisionDefinitionKeyOperations(
          List.of(FilterUtil.mapDefaultToOperation(value, values)));
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

    public Builder rootDecisionDefinitionKeyOperations(final List<Operation<Long>> operations) {
      rootDecisionDefinitionKeyOperations =
          addValuesToList(rootDecisionDefinitionKeyOperations, operations);
      return this;
    }

    public Builder rootDecisionDefinitionKeys(final Long value, final Long... values) {
      return rootDecisionDefinitionKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder rootDecisionDefinitionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return rootDecisionDefinitionKeyOperations(collectValues(operation, operations));
    }

    public Builder decisionRequirementsKeyOperations(final List<Operation<Long>> operations) {
      decisionRequirementsKeyOperations =
          addValuesToList(decisionRequirementsKeyOperations, operations);
      return this;
    }

    public Builder decisionRequirementsKeys(final Long value, final Long... values) {
      return decisionRequirementsKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder decisionRequirementsKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return decisionRequirementsKeyOperations(collectValues(operation, operations));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder tenantIds(final String... values) {
      return tenantIds(collectValuesAsList(values));
    }

    public Builder partitionId(final Integer value) {
      partitionId = value;
      return this;
    }

    @Override
    public DecisionInstanceFilter build() {
      return new DecisionInstanceFilter(
          Objects.requireNonNullElse(decisionInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(decisionInstanceIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(stateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(evaluationDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(evaluationFailures, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionIds, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionNames, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionVersions, Collections.emptyList()),
          Objects.requireNonNullElse(decisionTypes, Collections.emptyList()),
          Objects.requireNonNullElse(rootDecisionDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(decisionRequirementsKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()),
          partitionId);
    }
  }
}

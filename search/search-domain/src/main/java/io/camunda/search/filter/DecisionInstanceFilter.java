/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValuesAsList;

import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record DecisionInstanceFilter(
    List<Long> decisionInstanceKeys,
    List<DecisionInstanceState> states,
    DateValueFilter evaluationDate,
    List<String> evaluationFailures,
    List<Long> processDefinitionKeys,
    List<Long> processInstanceKeys,
    List<Long> decisionDefinitionKeys,
    List<String> decisionDefinitionIds,
    List<String> decisionDefinitionNames,
    List<Integer> decisionDefinitionVersions,
    List<DecisionDefinitionType> decisionTypes,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DecisionInstanceFilter> {

    private List<Long> decisionInstanceKeys;
    private List<DecisionInstanceState> states;
    private DateValueFilter evaluationDate;
    private List<String> evaluationFailures;
    private List<Long> processDefinitionKeys;
    private List<Long> processInstanceKeys;
    private List<Long> decisionDefinitionKeys;
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

    public Builder states(final List<DecisionInstanceState> values) {
      states = addValuesToList(states, values);
      return this;
    }

    public Builder states(final DecisionInstanceState... values) {
      return states(collectValuesAsList(values));
    }

    public Builder evaluationDate(final DateValueFilter evaluationDate) {
      this.evaluationDate = evaluationDate;
      return this;
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

    public Builder decisionDefinitionKeys(final List<Long> values) {
      decisionDefinitionKeys = addValuesToList(decisionDefinitionKeys, values);
      return this;
    }

    public Builder decisionDefinitionKeys(final Long... values) {
      return decisionDefinitionKeys(collectValuesAsList(values));
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
          Objects.requireNonNullElse(states, Collections.emptyList()),
          evaluationDate,
          Objects.requireNonNullElse(evaluationFailures, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionIds, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionNames, Collections.emptyList()),
          Objects.requireNonNullElse(decisionDefinitionVersions, Collections.emptyList()),
          Objects.requireNonNullElse(decisionTypes, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}

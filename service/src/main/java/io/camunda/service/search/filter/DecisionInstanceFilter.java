/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValuesAsList;

import io.camunda.service.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.service.entities.DecisionInstanceEntity.DecisionInstanceType;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record DecisionInstanceFilter(
    List<Long> keys,
    List<DecisionInstanceState> states,
    DateValueFilter evaluationDate,
    List<String> evaluationFailures,
    List<Long> processDefinitionKeys,
    List<Long> processInstanceKeys,
    List<Long> decisionKeys,
    List<String> dmnDecisionIds,
    List<String> dmnDecisionNames,
    List<Integer> decisionVersions,
    List<DecisionInstanceType> decisionTypes,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<DecisionInstanceFilter> {
    private List<Long> keys;
    private List<DecisionInstanceState> states;
    private DateValueFilter evaluationDate;
    private List<String> evaluationFailures;
    private List<Long> processDefinitionKeys;
    private List<Long> processInstanceKeys;
    private List<Long> decisionKeys;
    private List<String> dmnDecisionIds;
    private List<String> dmnDecisionNames;
    private List<Integer> decisionVersions;
    private List<DecisionInstanceType> decisionTypes;
    private List<String> tenantIds;

    public Builder keys(final List<Long> values) {
      keys = addValuesToList(keys, values);
      return this;
    }

    public Builder keys(final Long... values) {
      return keys(collectValuesAsList(values));
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

    public Builder decisionKeys(final List<Long> values) {
      decisionKeys = addValuesToList(decisionKeys, values);
      return this;
    }

    public Builder decisionKeys(final Long... values) {
      return decisionKeys(collectValuesAsList(values));
    }

    public Builder dmnDecisionIds(final List<String> values) {
      dmnDecisionIds = addValuesToList(dmnDecisionIds, values);
      return this;
    }

    public Builder dmnDecisionIds(final String... values) {
      return dmnDecisionIds(collectValuesAsList(values));
    }

    public Builder dmnDecisionNames(final List<String> values) {
      dmnDecisionNames = addValuesToList(dmnDecisionNames, values);
      return this;
    }

    public Builder dmnDecisionNames(final String... values) {
      return dmnDecisionNames(collectValuesAsList(values));
    }

    public Builder decisionVersions(final List<Integer> values) {
      decisionVersions = addValuesToList(decisionVersions, values);
      return this;
    }

    public Builder decisionVersions(final Integer... values) {
      return decisionVersions(collectValuesAsList(values));
    }

    public Builder decisionTypes(final List<DecisionInstanceType> values) {
      decisionTypes = addValuesToList(decisionTypes, values);
      return this;
    }

    public Builder decisionTypes(final DecisionInstanceType... values) {
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
          Objects.requireNonNullElse(keys, Collections.emptyList()),
          Objects.requireNonNullElse(states, Collections.emptyList()),
          evaluationDate,
          Objects.requireNonNullElse(evaluationFailures, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(decisionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(dmnDecisionIds, Collections.emptyList()),
          Objects.requireNonNullElse(dmnDecisionNames, Collections.emptyList()),
          Objects.requireNonNullElse(decisionVersions, Collections.emptyList()),
          Objects.requireNonNullElse(decisionTypes, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}

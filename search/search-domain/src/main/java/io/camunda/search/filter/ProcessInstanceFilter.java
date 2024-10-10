/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.*;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record ProcessInstanceFilter(
    List<Long> processInstanceKeys,
    List<String> processDefinitionIds,
    List<String> processDefinitionNames,
    List<Integer> processDefinitionVersions,
    List<String> processDefinitionVersionTags,
    List<Long> processDefinitionKeys,
    List<Long> rootProcessInstanceKeys,
    List<Long> parentProcessInstanceKeys,
    List<Long> parentFlowNodeInstanceKeys,
    List<String> treePaths,
    DateValueFilter startDate,
    DateValueFilter endDate,
    List<String> states,
    Boolean hasIncident,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<ProcessInstanceFilter> {

    private List<Long> processInstanceKeys;
    private List<String> processDefinitionIds;
    private List<String> processDefinitionNames;
    private List<Integer> processDefinitionVersions;
    private List<String> processDefinitionVersionTags;
    private List<Long> processDefinitionKeys;
    private List<Long> rootProcessInstanceKeys;
    private List<Long> parentProcessInstanceKeys;
    private List<Long> parentFlowNodeInstanceKeys;
    private List<String> treePaths;
    private DateValueFilter startDate;
    private DateValueFilter endDate;
    private List<String> states;
    private Boolean hasIncident;
    private List<String> tenantIds;

    public Builder processInstanceKeys(final List<Long> values) {
      processInstanceKeys = addValuesToList(processInstanceKeys, values);
      return this;
    }

    public Builder processInstanceKeys(final Long... values) {
      return processInstanceKeys(collectValuesAsList(values));
    }

    public Builder processDefinitionIds(final List<String> values) {
      processDefinitionIds = addValuesToList(processDefinitionIds, values);
      return this;
    }

    public Builder processDefinitionIds(final String... values) {
      return processDefinitionIds(collectValuesAsList(values));
    }

    public Builder processDefinitionNames(final List<String> values) {
      processDefinitionNames = addValuesToList(processDefinitionNames, values);
      return this;
    }

    public Builder processDefinitionNames(final String... values) {
      return processDefinitionNames(collectValuesAsList(values));
    }

    public Builder processDefinitionVersions(final List<Integer> values) {
      processDefinitionVersions = addValuesToList(processDefinitionVersions, values);
      return this;
    }

    public Builder processDefinitionVersions(final Integer... values) {
      return processDefinitionVersions(collectValuesAsList(values));
    }

    public Builder processDefinitionVersionTags(final List<String> values) {
      processDefinitionVersionTags = addValuesToList(processDefinitionVersionTags, values);
      return this;
    }

    public Builder processDefinitionVersionTags(final String... values) {
      return processDefinitionVersionTags(collectValuesAsList(values));
    }

    public Builder processDefinitionKeys(final List<Long> values) {
      processDefinitionKeys = addValuesToList(processDefinitionKeys, values);
      return this;
    }

    public Builder processDefinitionKeys(final Long... values) {
      return processDefinitionKeys(collectValuesAsList(values));
    }

    public Builder rootProcessInstanceKeys(final List<Long> values) {
      rootProcessInstanceKeys = addValuesToList(rootProcessInstanceKeys, values);
      return this;
    }

    public Builder rootProcessInstanceKeys(final Long... values) {
      return rootProcessInstanceKeys(collectValuesAsList(values));
    }

    public Builder parentProcessInstanceKeys(final List<Long> values) {
      parentProcessInstanceKeys = addValuesToList(parentProcessInstanceKeys, values);
      return this;
    }

    public Builder parentProcessInstanceKeys(final Long... values) {
      return parentProcessInstanceKeys(collectValuesAsList(values));
    }

    public Builder parentFlowNodeInstanceKeys(final List<Long> values) {
      parentFlowNodeInstanceKeys = addValuesToList(parentFlowNodeInstanceKeys, values);
      return this;
    }

    public Builder parentFlowNodeInstanceKeys(final Long... values) {
      return parentFlowNodeInstanceKeys(collectValuesAsList(values));
    }

    public Builder treePaths(final List<String> values) {
      treePaths = addValuesToList(treePaths, values);
      return this;
    }

    public Builder treePaths(final String... values) {
      return treePaths(collectValuesAsList(values));
    }

    public Builder startDate(final DateValueFilter startDate) {
      this.startDate = startDate;
      return this;
    }

    public Builder startDate(
        final Function<DateValueFilter.Builder, ObjectBuilder<DateValueFilter>> fn) {
      return startDate(FilterBuilders.dateValue(fn));
    }

    public Builder endDate(final DateValueFilter endDate) {
      this.endDate = endDate;
      return this;
    }

    public Builder endDate(
        final Function<DateValueFilter.Builder, ObjectBuilder<DateValueFilter>> fn) {
      return endDate(FilterBuilders.dateValue(fn));
    }

    public Builder states(final List<String> values) {
      states = addValuesToList(states, values);
      return this;
    }

    public Builder states(final String... values) {
      return states(collectValuesAsList(values));
    }

    public Builder hasIncident(final Boolean value) {
      hasIncident = value;
      return this;
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder tenantIds(final String... values) {
      return tenantIds(collectValuesAsList(values));
    }

    @Override
    public ProcessInstanceFilter build() {
      return new ProcessInstanceFilter(
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIds, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionNames, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionVersions, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionVersionTags, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(rootProcessInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(parentProcessInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(parentFlowNodeInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(treePaths, Collections.emptyList()),
          startDate,
          endDate,
          Objects.requireNonNullElse(states, Collections.emptyList()),
          hasIncident,
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}

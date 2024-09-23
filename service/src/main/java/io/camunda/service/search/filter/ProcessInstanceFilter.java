/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import static io.camunda.util.CollectionUtil.*;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record ProcessInstanceFilter(
    List<Long> keys,
    List<String> bpmnProcessIds,
    List<String> processNames,
    List<Integer> processVersions,
    List<String> processVersionTags,
    List<Long> processDefinitionKeys,
    List<Long> rootProcessInstanceKeys,
    List<Long> parentProcessInstanceKeys,
    List<Long> parentFlowNodeInstanceKeys,
    List<String> treePaths,
    DateValueFilter startDate,
    DateValueFilter endDate,
    List<String> states,
    Boolean incident,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<ProcessInstanceFilter> {

    private List<Long> keys;
    private List<String> bpmnProcessIds;
    private List<String> processNames;
    private List<Integer> processVersions;
    private List<String> processVersionTags;
    private List<Long> processDefinitionKeys;
    private List<Long> rootProcessInstanceKeys;
    private List<Long> parentProcessInstanceKeys;
    private List<Long> parentFlowNodeInstanceKeys;
    private List<String> treePaths;
    private DateValueFilter startDate;
    private DateValueFilter endDate;
    private List<String> states;
    private Boolean incident;
    private List<String> tenantIds;

    public Builder keys(final List<Long> values) {
      keys = addValuesToList(keys, values);
      return this;
    }

    public Builder keys(final Long... values) {
      return keys(collectValuesAsList(values));
    }

    public Builder bpmnProcessIds(final List<String> values) {
      bpmnProcessIds = addValuesToList(bpmnProcessIds, values);
      return this;
    }

    public Builder bpmnProcessIds(final String... values) {
      return bpmnProcessIds(collectValuesAsList(values));
    }

    public Builder processNames(final List<String> values) {
      processNames = addValuesToList(processNames, values);
      return this;
    }

    public Builder processNames(final String... values) {
      return processNames(collectValuesAsList(values));
    }

    public Builder processVersions(final List<Integer> values) {
      processVersions = addValuesToList(processVersions, values);
      return this;
    }

    public Builder processVersions(final Integer... values) {
      return processVersions(collectValuesAsList(values));
    }

    public Builder processVersionTags(final List<String> values) {
      processVersionTags = addValuesToList(processVersionTags, values);
      return this;
    }

    public Builder processVersionTags(final String... values) {
      return processVersionTags(collectValuesAsList(values));
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

    public Builder incident(final Boolean value) {
      incident = value;
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
          Objects.requireNonNullElse(keys, Collections.emptyList()),
          Objects.requireNonNullElse(bpmnProcessIds, Collections.emptyList()),
          Objects.requireNonNullElse(processNames, Collections.emptyList()),
          Objects.requireNonNullElse(processVersions, Collections.emptyList()),
          Objects.requireNonNullElse(processVersionTags, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(rootProcessInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(parentProcessInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(parentFlowNodeInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(treePaths, Collections.emptyList()),
          startDate,
          endDate,
          Objects.requireNonNullElse(states, Collections.emptyList()),
          incident,
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}

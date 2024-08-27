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

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record FlownodeInstanceFilter(
    List<Long> keys,
    List<Long> processInstanceKeys,
    List<Long> processDefinitionKeys,
    List<String> states,
    List<String> types,
    List<String> flowNodeIds,
    List<String> flowNodeNames,
    List<String> treePaths,
    Boolean incident,
    List<Long> incidentKeys,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<FlownodeInstanceFilter> {

    private List<Long> keys;
    private List<Long> processInstanceKeys;
    private List<Long> processDefinitionKeys;
    private List<String> states;
    private List<String> types;
    private List<String> flowNodeIds;
    private List<String> flowNodeNames;
    private List<String> treePaths;
    private Boolean incident;
    private List<Long> incidentKeys;
    private List<String> tenantIds;

    public FlownodeInstanceFilter.Builder keys(final List<Long> values) {
      keys = addValuesToList(keys, values);
      return this;
    }

    public FlownodeInstanceFilter.Builder keys(final Long... values) {
      return keys(collectValuesAsList(values));
    }

    public FlownodeInstanceFilter.Builder processInstanceKeys(final List<Long> values) {
      processInstanceKeys = addValuesToList(processInstanceKeys, values);
      return this;
    }

    public FlownodeInstanceFilter.Builder processInstanceKeys(final Long... values) {
      return processInstanceKeys(collectValuesAsList(values));
    }

    public FlownodeInstanceFilter.Builder processDefinitionKeys(final List<Long> values) {
      processDefinitionKeys = addValuesToList(processDefinitionKeys, values);
      return this;
    }

    public FlownodeInstanceFilter.Builder processDefinitionKeys(final Long... values) {
      return processDefinitionKeys(collectValuesAsList(values));
    }

    public FlownodeInstanceFilter.Builder states(final List<String> values) {
      states = addValuesToList(states, values);
      return this;
    }

    public FlownodeInstanceFilter.Builder states(final String... values) {
      return states(collectValuesAsList(values));
    }

    public FlownodeInstanceFilter.Builder types(final List<String> values) {
      types = addValuesToList(types, values);
      return this;
    }

    public FlownodeInstanceFilter.Builder types(final String... values) {
      return types(collectValuesAsList(values));
    }

    public FlownodeInstanceFilter.Builder flowNodeIds(final List<String> values) {
      flowNodeIds = addValuesToList(flowNodeIds, values);
      return this;
    }

    public FlownodeInstanceFilter.Builder flowNodeIds(final String... values) {
      return flowNodeIds(collectValuesAsList(values));
    }

    public FlownodeInstanceFilter.Builder flowNodeNames(final List<String> values) {
      flowNodeNames = addValuesToList(flowNodeNames, values);
      return this;
    }

    public FlownodeInstanceFilter.Builder flowNodeNames(final String... values) {
      return flowNodeNames(collectValuesAsList(values));
    }

    public FlownodeInstanceFilter.Builder treePaths(final List<String> values) {
      treePaths = addValuesToList(treePaths, values);
      return this;
    }

    public FlownodeInstanceFilter.Builder treePaths(final String... values) {
      return treePaths(collectValuesAsList(values));
    }

    public FlownodeInstanceFilter.Builder incident(final Boolean value) {
      incident = value;
      return this;
    }

    public FlownodeInstanceFilter.Builder incidentKeys(final List<Long> values) {
      incidentKeys = addValuesToList(incidentKeys, values);
      return this;
    }

    public FlownodeInstanceFilter.Builder incidentKeys(final Long... values) {
      return incidentKeys(collectValuesAsList(values));
    }

    public FlownodeInstanceFilter.Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public FlownodeInstanceFilter.Builder tenantIds(final String... values) {
      return tenantIds(collectValuesAsList(values));
    }

    @Override
    public FlownodeInstanceFilter build() {
      return new FlownodeInstanceFilter(
          Objects.requireNonNullElse(keys, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(states, Collections.emptyList()),
          Objects.requireNonNullElse(types, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeIds, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeNames, Collections.emptyList()),
          Objects.requireNonNullElse(treePaths, Collections.emptyList()),
          Objects.requireNonNullElse(incident, false),
          Objects.requireNonNullElse(incidentKeys, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}

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

import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record FlowNodeInstanceFilter(
    List<Long> flowNodeInstanceKeys,
    List<Long> processInstanceKeys,
    List<Long> processDefinitionKeys,
    List<String> processDefinitionIds,
    List<FlowNodeState> states,
    List<FlowNodeType> types,
    List<String> flowNodeIds,
    List<String> treePaths,
    Boolean hasIncident,
    List<Long> incidentKeys,
    List<String> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<FlowNodeInstanceFilter> {

    private List<Long> flowNodeInstanceKeys;
    private List<Long> processInstanceKeys;
    private List<Long> processDefinitionKeys;
    private List<String> processDefinitionIds;
    private List<FlowNodeState> states;
    private List<FlowNodeType> types;
    private List<String> flowNodeIds;
    private List<String> treePaths;
    private Boolean hasIncident;
    private List<Long> incidentKeys;
    private List<String> tenantIds;

    public FlowNodeInstanceFilter.Builder flowNodeInstanceKeys(final List<Long> values) {
      flowNodeInstanceKeys = addValuesToList(flowNodeInstanceKeys, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder flowNodeInstanceKeys(final Long... values) {
      return flowNodeInstanceKeys(collectValuesAsList(values));
    }

    public FlowNodeInstanceFilter.Builder processInstanceKeys(final List<Long> values) {
      processInstanceKeys = addValuesToList(processInstanceKeys, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder processInstanceKeys(final Long... values) {
      return processInstanceKeys(collectValuesAsList(values));
    }

    public FlowNodeInstanceFilter.Builder processDefinitionKeys(final List<Long> values) {
      processDefinitionKeys = addValuesToList(processDefinitionKeys, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder processDefinitionKeys(final Long... values) {
      return processDefinitionKeys(collectValuesAsList(values));
    }

    public FlowNodeInstanceFilter.Builder processDefinitionIds(final List<String> values) {
      processDefinitionIds = addValuesToList(processDefinitionIds, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder processDefinitionIds(final String... values) {
      return processDefinitionIds(collectValuesAsList(values));
    }

    public FlowNodeInstanceFilter.Builder states(final List<FlowNodeState> values) {
      states = addValuesToList(states, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder states(final FlowNodeState... values) {
      return states(collectValuesAsList(values));
    }

    public FlowNodeInstanceFilter.Builder types(final List<FlowNodeType> values) {
      types = addValuesToList(types, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder types(final FlowNodeType... values) {
      return types(collectValuesAsList(values));
    }

    public FlowNodeInstanceFilter.Builder flowNodeIds(final List<String> values) {
      flowNodeIds = addValuesToList(flowNodeIds, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder flowNodeIds(final String... values) {
      return flowNodeIds(collectValuesAsList(values));
    }

    public FlowNodeInstanceFilter.Builder treePaths(final List<String> values) {
      treePaths = addValuesToList(treePaths, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder treePaths(final String... values) {
      return treePaths(collectValuesAsList(values));
    }

    public FlowNodeInstanceFilter.Builder hasIncident(final Boolean value) {
      hasIncident = value;
      return this;
    }

    public FlowNodeInstanceFilter.Builder incidentKeys(final List<Long> values) {
      incidentKeys = addValuesToList(incidentKeys, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder incidentKeys(final Long... values) {
      return incidentKeys(collectValuesAsList(values));
    }

    public FlowNodeInstanceFilter.Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder tenantIds(final String... values) {
      return tenantIds(collectValuesAsList(values));
    }

    @Override
    public FlowNodeInstanceFilter build() {
      return new FlowNodeInstanceFilter(
          Objects.requireNonNullElse(flowNodeInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIds, Collections.emptyList()),
          Objects.requireNonNullElse(states, Collections.emptyList()),
          Objects.requireNonNullElse(types, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeIds, Collections.emptyList()),
          Objects.requireNonNullElse(treePaths, Collections.emptyList()),
          hasIncident,
          Objects.requireNonNullElse(incidentKeys, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}

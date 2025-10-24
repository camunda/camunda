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

import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record FlowNodeInstanceFilter(
    List<Long> flowNodeInstanceKeys,
    List<Long> processInstanceKeys,
    List<Long> processDefinitionKeys,
    List<String> processDefinitionIds,
    List<Operation<String>> stateOperations,
    List<FlowNodeType> types,
    List<String> flowNodeIds,
    List<String> flowNodeNames,
    List<String> treePaths,
    Boolean hasIncident,
    List<Long> incidentKeys,
    List<String> tenantIds,
    List<Operation<OffsetDateTime>> startDateOperations,
    List<Operation<OffsetDateTime>> endDateOperations,
    List<Long> elementInstanceScopeKeys,
    List<Integer> levels,
    // Flag used to control whether treePath filtering should use a prefix query.
    // This is necessary while the elementInstanceScopeKey field is not universally populated
    // (pre-8.8 data).
    // Once all data has consistent elementInstanceScopeKey population, this flag and related logic
    // can be removed.
    Boolean useTreePathPrefix)
    implements FilterBase {

  public static FlowNodeInstanceFilter of(
      final Function<FlowNodeInstanceFilter.Builder, ObjectBuilder<FlowNodeInstanceFilter>> fn) {
    return FilterBuilders.flowNodeInstance(fn);
  }

  public static final class Builder implements ObjectBuilder<FlowNodeInstanceFilter> {

    private List<Long> flowNodeInstanceKeys;
    private List<Long> processInstanceKeys;
    private List<Long> processDefinitionKeys;
    private List<String> processDefinitionIds;
    private List<Operation<String>> stateOperations;
    private List<FlowNodeType> types;
    private List<String> flowNodeIds;
    private List<String> flowNodeNames;
    private List<String> treePaths;
    private Boolean hasIncident;
    private List<Long> incidentKeys;
    private List<String> tenantIds;
    private List<Operation<OffsetDateTime>> startDateOperations;
    private List<Operation<OffsetDateTime>> endDateOperations;
    private List<Long> elementInstanceScopeKeys;
    private List<Integer> levels;
    private Boolean useTreePathPrefix;

    public Builder copyFrom(final FlowNodeInstanceFilter filter) {
      flowNodeInstanceKeys(filter.flowNodeInstanceKeys());
      processInstanceKeys(filter.processInstanceKeys());
      processDefinitionKeys(filter.processDefinitionKeys());
      processDefinitionIds(filter.processDefinitionIds());
      stateOperations(filter.stateOperations());
      types(filter.types());
      flowNodeIds(filter.flowNodeIds());
      flowNodeNames(filter.flowNodeNames());
      treePaths(filter.treePaths());
      hasIncident(filter.hasIncident());
      incidentKeys(filter.incidentKeys());
      tenantIds(filter.tenantIds());
      startDateOperations(filter.startDateOperations());
      endDateOperations(filter.endDateOperations());
      elementInstanceScopeKeys(filter.elementInstanceScopeKeys());
      levels(filter.levels());
      useTreePathPrefix(filter.useTreePathPrefix());
      return this;
    }

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

    public FlowNodeInstanceFilter.Builder stateOperations(
        final List<Operation<String>> operations) {
      stateOperations = addValuesToList(stateOperations, operations);
      return this;
    }

    public FlowNodeInstanceFilter.Builder states(final String value, final String... values) {
      return stateOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final FlowNodeInstanceFilter.Builder stateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return stateOperations(collectValues(operation, operations));
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

    public FlowNodeInstanceFilter.Builder flowNodeNames(final List<String> values) {
      flowNodeNames = addValuesToList(flowNodeNames, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder flowNodeNames(final String... values) {
      return flowNodeNames(collectValuesAsList(values));
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

    public FlowNodeInstanceFilter.Builder startDateOperations(
        final List<Operation<OffsetDateTime>> operations) {
      startDateOperations = addValuesToList(startDateOperations, operations);
      return this;
    }

    @SafeVarargs
    public final FlowNodeInstanceFilter.Builder startDateOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return startDateOperations(collectValues(operation, operations));
    }

    public FlowNodeInstanceFilter.Builder endDateOperations(
        final List<Operation<OffsetDateTime>> operations) {
      endDateOperations = addValuesToList(endDateOperations, operations);
      return this;
    }

    @SafeVarargs
    public final FlowNodeInstanceFilter.Builder endDateOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return endDateOperations(collectValues(operation, operations));
    }

    public FlowNodeInstanceFilter.Builder elementInstanceScopeKeys(final List<Long> values) {
      elementInstanceScopeKeys = addValuesToList(elementInstanceScopeKeys, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder elementInstanceScopeKeys(final Long... values) {
      return elementInstanceScopeKeys(collectValuesAsList(values));
    }

    public FlowNodeInstanceFilter.Builder levels(final List<Integer> values) {
      levels = addValuesToList(levels, values);
      return this;
    }

    public FlowNodeInstanceFilter.Builder levels(final Integer... values) {
      return levels(collectValuesAsList(values));
    }

    public FlowNodeInstanceFilter.Builder useTreePathPrefix(final Boolean value) {
      useTreePathPrefix = value;
      return this;
    }

    @Override
    public FlowNodeInstanceFilter build() {
      return new FlowNodeInstanceFilter(
          Objects.requireNonNullElse(flowNodeInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeys, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIds, Collections.emptyList()),
          Objects.requireNonNullElse(stateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(types, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeIds, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeNames, Collections.emptyList()),
          Objects.requireNonNullElse(treePaths, Collections.emptyList()),
          hasIncident,
          Objects.requireNonNullElse(incidentKeys, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()),
          Objects.requireNonNullElse(startDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(endDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(elementInstanceScopeKeys, Collections.emptyList()),
          Objects.requireNonNullElse(levels, Collections.emptyList()),
          useTreePathPrefix);
    }
  }
}

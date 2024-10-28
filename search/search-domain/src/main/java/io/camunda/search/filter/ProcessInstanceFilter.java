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
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record ProcessInstanceFilter(
    List<Operation<Long>> processInstanceKeys,
    List<Operation<String>> processDefinitionIds,
    List<Operation<String>> processDefinitionNames,
    List<Operation<Integer>> processDefinitionVersions,
    List<Operation<String>> processDefinitionVersionTags,
    List<Operation<Long>> processDefinitionKeys,
    List<Operation<Long>> parentProcessInstanceKeys,
    List<Operation<Long>> parentFlowNodeInstanceKeys,
    List<Operation<String>> treePaths,
    List<Operation<OffsetDateTime>> startDate,
    List<Operation<OffsetDateTime>> endDate,
    List<Operation<String>> states,
    Boolean hasIncident,
    List<Operation<String>> tenantIds)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<ProcessInstanceFilter> {

    private List<Operation<Long>> processInstanceKeys;
    private List<Operation<String>> processDefinitionIds;
    private List<Operation<String>> processDefinitionNames;
    private List<Operation<Integer>> processDefinitionVersions;
    private List<Operation<String>> processDefinitionVersionTags;
    private List<Operation<Long>> processDefinitionKeys;
    private List<Operation<Long>> parentProcessInstanceKeys;
    private List<Operation<Long>> parentFlowNodeInstanceKeys;
    private List<Operation<String>> treePaths;
    private List<Operation<OffsetDateTime>> startDate;
    private List<Operation<OffsetDateTime>> endDate;
    private List<Operation<String>> states;
    private Boolean hasIncident;
    private List<Operation<String>> tenantIds;

    @SafeVarargs
    private <T> List<Operation<T>> mapToOperationEq(final T... values) {
      return Arrays.stream(values).map(Operation::eq).collect(Collectors.toList());
    }

    public Builder processInstanceKeys(final List<Operation<Long>> operations) {
      processInstanceKeys = addValuesToList(processInstanceKeys, operations);
      return this;
    }

    public Builder processInstanceKeys(final Long... values) {
      return processInstanceKeys(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder processInstanceKeys(final Operation<Long>... operations) {
      return processInstanceKeys(collectValuesAsList(operations));
    }

    public Builder processDefinitionIds(final List<Operation<String>> operations) {
      processDefinitionIds = addValuesToList(processDefinitionIds, operations);
      return this;
    }

    public Builder processDefinitionIds(final String... values) {
      return processDefinitionIds(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder processDefinitionIds(final Operation<String>... operations) {
      return processDefinitionIds(collectValuesAsList(operations));
    }

    public Builder processDefinitionNames(final List<Operation<String>> operations) {
      processDefinitionNames = addValuesToList(processDefinitionNames, operations);
      return this;
    }

    public Builder processDefinitionNames(final String... values) {
      return processDefinitionNames(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder processDefinitionNames(final Operation<String>... operations) {
      return processDefinitionNames(collectValuesAsList(operations));
    }

    public Builder processDefinitionVersions(final List<Operation<Integer>> operations) {
      processDefinitionVersions = addValuesToList(processDefinitionVersions, operations);
      return this;
    }

    public Builder processDefinitionVersions(final Integer... values) {
      return processDefinitionVersions(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder processDefinitionVersions(final Operation<Integer>... operations) {
      return processDefinitionVersions(collectValuesAsList(operations));
    }

    public Builder processDefinitionVersionTags(final List<Operation<String>> values) {
      processDefinitionVersionTags = addValuesToList(processDefinitionVersionTags, values);
      return this;
    }

    public Builder processDefinitionVersionTags(final String... values) {
      return processDefinitionVersionTags(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder processDefinitionVersionTags(final Operation<String>... operations) {
      return processDefinitionVersionTags(collectValuesAsList(operations));
    }

    public Builder processDefinitionKeys(final List<Operation<Long>> operations) {
      processDefinitionKeys = addValuesToList(processDefinitionKeys, operations);
      return this;
    }

    public Builder processDefinitionKeys(final Long processDefinitionKeys) {
      return processDefinitionKeys(mapToOperationEq(processDefinitionKeys));
    }

    @SafeVarargs
    public final Builder processDefinitionKeys(final Operation<Long>... operations) {
      return processDefinitionKeys(collectValuesAsList(operations));
    }

    public Builder parentProcessInstanceKeys(final List<Operation<Long>> operations) {
      parentProcessInstanceKeys = addValuesToList(parentProcessInstanceKeys, operations);
      return this;
    }

    public Builder parentProcessInstanceKeys(final Long... values) {
      return parentProcessInstanceKeys(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder parentProcessInstanceKeys(final Operation<Long>... operations) {
      return parentProcessInstanceKeys(collectValuesAsList(operations));
    }

    public Builder parentFlowNodeInstanceKeys(final List<Operation<Long>> operations) {
      parentFlowNodeInstanceKeys = addValuesToList(parentFlowNodeInstanceKeys, operations);
      return this;
    }

    public Builder parentFlowNodeInstanceKeys(final Long... values) {
      return parentFlowNodeInstanceKeys(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder parentFlowNodeInstanceKeys(final Operation<Long>... operations) {
      return parentFlowNodeInstanceKeys(collectValuesAsList(operations));
    }

    public Builder treePaths(final List<Operation<String>> operations) {
      treePaths = addValuesToList(treePaths, operations);
      return this;
    }

    public Builder treePaths(final String... values) {
      return treePaths(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder treePaths(final Operation<String>... operations) {
      return treePaths(collectValuesAsList(operations));
    }

    public Builder startDate(final List<Operation<OffsetDateTime>> operations) {
      startDate = addValuesToList(startDate, operations);
      return this;
    }

    @SafeVarargs
    public final Builder startDate(final Operation<OffsetDateTime>... operations) {
      return startDate(List.of(operations));
    }

    public Builder endDate(final List<Operation<OffsetDateTime>> operations) {
      endDate = addValuesToList(endDate, operations);
      return this;
    }

    @SafeVarargs
    public final Builder endDate(final Operation<OffsetDateTime>... operations) {
      return endDate(List.of(operations));
    }

    public Builder states(final List<Operation<String>> operations) {
      states = addValuesToList(states, operations);
      return this;
    }

    public Builder states(final String... values) {
      return states(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder states(final Operation<String>... operations) {
      return states(collectValuesAsList(operations));
    }

    public Builder hasIncident(final Boolean value) {
      hasIncident = value;
      return this;
    }

    public Builder tenantIds(final List<Operation<String>> operations) {
      tenantIds = addValuesToList(tenantIds, operations);
      return this;
    }

    public Builder tenantIds(final String... values) {
      return tenantIds(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder tenantIds(final Operation<String>... operations) {
      return tenantIds(collectValuesAsList(operations));
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
          Objects.requireNonNullElse(parentProcessInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(parentFlowNodeInstanceKeys, Collections.emptyList()),
          Objects.requireNonNullElse(treePaths, Collections.emptyList()),
          Objects.requireNonNullElse(startDate, Collections.emptyList()),
          Objects.requireNonNullElse(endDate, Collections.emptyList()),
          Objects.requireNonNullElse(states, Collections.emptyList()),
          hasIncident,
          Objects.requireNonNullElse(tenantIds, Collections.emptyList()));
    }
  }
}

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
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<String>> processDefinitionIdOperations,
    List<Operation<String>> processDefinitionNameOperations,
    List<Operation<Integer>> processDefinitionVersionOperations,
    List<Operation<String>> processDefinitionVersionTagOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<Long>> parentProcessInstanceKeyOperations,
    List<Operation<Long>> parentFlowNodeInstanceKeyOperations,
    List<Operation<String>> treePathOperations,
    List<Operation<OffsetDateTime>> startDateOperations,
    List<Operation<OffsetDateTime>> endDateOperations,
    List<Operation<String>> stateOperations,
    Boolean hasIncident,
    List<Operation<String>> tenantIdOperations)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<ProcessInstanceFilter> {

    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<String>> processDefinitionIdOperations;
    private List<Operation<String>> processDefinitionNameOperations;
    private List<Operation<Integer>> processDefinitionVersionOperations;
    private List<Operation<String>> processDefinitionVersionTagOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<Long>> parentProcessInstanceKeyOperations;
    private List<Operation<Long>> parentFlowNodeInstanceKeyOperations;
    private List<Operation<String>> treePathOperations;
    private List<Operation<OffsetDateTime>> startDateOperations;
    private List<Operation<OffsetDateTime>> endDateOperations;
    private List<Operation<String>> stateOperations;
    private Boolean hasIncident;
    private List<Operation<String>> tenantIdOperations;

    @SafeVarargs
    private <T> List<Operation<T>> mapToOperationEq(final T... values) {
      return Arrays.stream(values).map(Operation::eq).collect(Collectors.toList());
    }

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    public Builder processInstanceKeys(final Long... values) {
      return processInstanceKeyOperations(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder processInstanceKeyOperations(final Operation<Long>... operations) {
      return processInstanceKeyOperations(collectValuesAsList(operations));
    }

    public Builder processDefinitionIdOperations(final List<Operation<String>> operations) {
      processDefinitionIdOperations = addValuesToList(processDefinitionIdOperations, operations);
      return this;
    }

    public Builder processDefinitionIds(final String... values) {
      return processDefinitionIdOperations(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder processDefinitionIdOperations(final Operation<String>... operations) {
      return processDefinitionIdOperations(collectValuesAsList(operations));
    }

    public Builder processDefinitionNameOperations(final List<Operation<String>> operations) {
      processDefinitionNameOperations =
          addValuesToList(processDefinitionNameOperations, operations);
      return this;
    }

    public Builder processDefinitionNames(final String... values) {
      return processDefinitionNameOperations(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder processDefinitionNameOperations(final Operation<String>... operations) {
      return processDefinitionNameOperations(collectValuesAsList(operations));
    }

    public Builder processDefinitionVersionOperations(final List<Operation<Integer>> operations) {
      processDefinitionVersionOperations =
          addValuesToList(processDefinitionVersionOperations, operations);
      return this;
    }

    public Builder processDefinitionVersions(final Integer... values) {
      return processDefinitionVersionOperations(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder processDefinitionVersionOperations(
        final Operation<Integer>... operations) {
      return processDefinitionVersionOperations(collectValuesAsList(operations));
    }

    public Builder processDefinitionVersionTagOperations(final List<Operation<String>> values) {
      processDefinitionVersionTagOperations =
          addValuesToList(processDefinitionVersionTagOperations, values);
      return this;
    }

    public Builder processDefinitionVersionTags(final String... values) {
      return processDefinitionVersionTagOperations(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder processDefinitionVersionTagOperations(
        final Operation<String>... operations) {
      return processDefinitionVersionTagOperations(collectValuesAsList(operations));
    }

    public Builder processDefinitionKeyOperations(final List<Operation<Long>> operations) {
      processDefinitionKeyOperations = addValuesToList(processDefinitionKeyOperations, operations);
      return this;
    }

    public Builder processDefinitionKeys(final Long processDefinitionKeys) {
      return processDefinitionKeyOperations(mapToOperationEq(processDefinitionKeys));
    }

    @SafeVarargs
    public final Builder processDefinitionKeyOperations(final Operation<Long>... operations) {
      return processDefinitionKeyOperations(collectValuesAsList(operations));
    }

    public Builder parentProcessInstanceKeyOperations(final List<Operation<Long>> operations) {
      parentProcessInstanceKeyOperations =
          addValuesToList(parentProcessInstanceKeyOperations, operations);
      return this;
    }

    public Builder parentProcessInstanceKeys(final Long... values) {
      return parentProcessInstanceKeyOperations(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder parentProcessInstanceKeyOperations(final Operation<Long>... operations) {
      return parentProcessInstanceKeyOperations(collectValuesAsList(operations));
    }

    public Builder parentFlowNodeInstanceKeyOperations(final List<Operation<Long>> operations) {
      parentFlowNodeInstanceKeyOperations =
          addValuesToList(parentFlowNodeInstanceKeyOperations, operations);
      return this;
    }

    public Builder parentFlowNodeInstanceKeys(final Long... values) {
      return parentFlowNodeInstanceKeyOperations(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder parentFlowNodeInstanceKeyOperations(final Operation<Long>... operations) {
      return parentFlowNodeInstanceKeyOperations(collectValuesAsList(operations));
    }

    public Builder treePathOperations(final List<Operation<String>> operations) {
      treePathOperations = addValuesToList(treePathOperations, operations);
      return this;
    }

    public Builder treePaths(final String... values) {
      return treePathOperations(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder treePathOperations(final Operation<String>... operations) {
      return treePathOperations(collectValuesAsList(operations));
    }

    public Builder startDateOperations(final List<Operation<OffsetDateTime>> operations) {
      startDateOperations = addValuesToList(startDateOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder startDateOperations(final Operation<OffsetDateTime>... operations) {
      return startDateOperations(List.of(operations));
    }

    public Builder endDateOperations(final List<Operation<OffsetDateTime>> operations) {
      endDateOperations = addValuesToList(endDateOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder endDateOperations(final Operation<OffsetDateTime>... operations) {
      return endDateOperations(List.of(operations));
    }

    public Builder stateOperations(final List<Operation<String>> operations) {
      stateOperations = addValuesToList(stateOperations, operations);
      return this;
    }

    public Builder states(final String... values) {
      return stateOperations(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder stateOperations(final Operation<String>... operations) {
      return stateOperations(collectValuesAsList(operations));
    }

    public Builder hasIncident(final Boolean value) {
      hasIncident = value;
      return this;
    }

    public Builder tenantIdOperations(final List<Operation<String>> operations) {
      tenantIdOperations = addValuesToList(tenantIdOperations, operations);
      return this;
    }

    public Builder tenantIds(final String... values) {
      return tenantIdOperations(mapToOperationEq(values));
    }

    @SafeVarargs
    public final Builder tenantIdOperations(final Operation<String>... operations) {
      return tenantIdOperations(collectValuesAsList(operations));
    }

    @Override
    public ProcessInstanceFilter build() {
      return new ProcessInstanceFilter(
          Objects.requireNonNullElse(processInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionNameOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionVersionOperations, Collections.emptyList()),
          Objects.requireNonNullElse(
              processDefinitionVersionTagOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(parentProcessInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(parentFlowNodeInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(treePathOperations, Collections.emptyList()),
          Objects.requireNonNullElse(startDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(endDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(stateOperations, Collections.emptyList()),
          hasIncident,
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()));
    }
  }
}

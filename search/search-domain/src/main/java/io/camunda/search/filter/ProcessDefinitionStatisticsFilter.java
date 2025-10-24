/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.*;

import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ProcessDefinitionStatisticsFilter(
    long processDefinitionKey,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Long>> parentProcessInstanceKeyOperations,
    List<Operation<Long>> parentFlowNodeInstanceKeyOperations,
    List<Operation<OffsetDateTime>> startDateOperations,
    List<Operation<OffsetDateTime>> endDateOperations,
    List<Operation<String>> stateOperations,
    Boolean hasIncident,
    List<Operation<String>> tenantIdOperations,
    List<VariableValueFilter> variableFilters,
    List<Operation<String>> errorMessageOperations,
    List<Operation<String>> batchOperationIdOperations,
    Boolean hasRetriesLeft,
    List<Operation<String>> flowNodeIdOperations,
    Boolean hasFlowNodeInstanceIncident,
    List<Operation<String>> flowNodeInstanceStateOperations,
    List<Operation<Integer>> incidentErrorHashCodeOperations,
    List<ProcessDefinitionStatisticsFilter> orFilters)
    implements FilterBase {

  public Builder toBuilder() {
    return new Builder(processDefinitionKey)
        .processInstanceKeyOperations(processInstanceKeyOperations)
        .parentProcessInstanceKeyOperations(parentProcessInstanceKeyOperations)
        .parentFlowNodeInstanceKeyOperations(parentFlowNodeInstanceKeyOperations)
        .startDateOperations(startDateOperations)
        .endDateOperations(endDateOperations)
        .stateOperations(stateOperations)
        .hasIncident(hasIncident)
        .tenantIdOperations(tenantIdOperations)
        .variables(variableFilters)
        .errorMessageOperations(errorMessageOperations)
        .batchOperationIdOperations(batchOperationIdOperations)
        .hasRetriesLeft(hasRetriesLeft)
        .flowNodeIdOperations(flowNodeIdOperations)
        .hasFlowNodeInstanceIncident(hasFlowNodeInstanceIncident)
        .flowNodeInstanceStateOperations(flowNodeInstanceStateOperations)
        .incidentErrorHashCodeOperations(incidentErrorHashCodeOperations)
        .orFilters(orFilters);
  }

  public static final class Builder implements ObjectBuilder<ProcessDefinitionStatisticsFilter> {

    private final long processDefinitionKey;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<Long>> parentProcessInstanceKeyOperations;
    private List<Operation<Long>> parentFlowNodeInstanceKeyOperations;
    private List<Operation<OffsetDateTime>> startDateOperations;
    private List<Operation<OffsetDateTime>> endDateOperations;
    private List<Operation<String>> stateOperations;
    private Boolean hasIncident;
    private List<Operation<String>> tenantIdOperations;
    private List<VariableValueFilter> variableFilters;
    private List<Operation<String>> errorMessageOperations;
    private List<Operation<String>> batchOperationIdOperations;
    private Boolean hasRetriesLeft;
    private List<Operation<String>> flowNodeIdOperations;
    private Boolean hasFlowNodeInstanceIncident;
    private List<Operation<String>> flowNodeInstanceStateOperations;
    private List<Operation<Integer>> incidentErrorHashCodeOperations;
    private List<ProcessDefinitionStatisticsFilter> orFilters;

    public Builder(final long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
    }

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder replaceErrorMessageOperations(final List<Operation<String>> operations) {
      errorMessageOperations = new ArrayList<>(operations);
      return this;
    }

    @SafeVarargs
    public final Builder processInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder parentProcessInstanceKeyOperations(final List<Operation<Long>> operations) {
      parentProcessInstanceKeyOperations =
          addValuesToList(parentProcessInstanceKeyOperations, operations);
      return this;
    }

    public Builder parentProcessInstanceKeys(final Long value, final Long... values) {
      return parentProcessInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder parentProcessInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return parentProcessInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder parentFlowNodeInstanceKeyOperations(final List<Operation<Long>> operations) {
      parentFlowNodeInstanceKeyOperations =
          addValuesToList(parentFlowNodeInstanceKeyOperations, operations);
      return this;
    }

    public Builder parentFlowNodeInstanceKeys(final Long value, final Long... values) {
      return parentFlowNodeInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder parentFlowNodeInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return parentFlowNodeInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder startDateOperations(final List<Operation<OffsetDateTime>> operations) {
      startDateOperations = addValuesToList(startDateOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder startDateOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return startDateOperations(collectValues(operation, operations));
    }

    public Builder endDateOperations(final List<Operation<OffsetDateTime>> operations) {
      endDateOperations = addValuesToList(endDateOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder endDateOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return endDateOperations(collectValues(operation, operations));
    }

    public Builder stateOperations(final List<Operation<String>> operations) {
      stateOperations = addValuesToList(stateOperations, operations);
      return this;
    }

    public Builder states(final String value, final String... values) {
      return stateOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder stateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return stateOperations(collectValues(operation, operations));
    }

    public Builder hasIncident(final Boolean value) {
      hasIncident = value;
      return this;
    }

    public Builder tenantIdOperations(final List<Operation<String>> operations) {
      tenantIdOperations = addValuesToList(tenantIdOperations, operations);
      return this;
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder tenantIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return tenantIdOperations(collectValues(operation, operations));
    }

    public Builder variables(final List<VariableValueFilter> values) {
      variableFilters = addValuesToList(variableFilters, values);
      return this;
    }

    public Builder batchOperationIdOperations(final List<Operation<String>> operations) {
      batchOperationIdOperations = addValuesToList(batchOperationIdOperations, operations);
      return this;
    }

    public Builder batchOperationIds(final String value, final String... values) {
      return batchOperationIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder batchOperationIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return batchOperationIdOperations(collectValues(operation, operations));
    }

    public Builder errorMessages(final String value, final String... values) {
      return errorMessageOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder errorMessageOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return errorMessageOperations(collectValues(operation, operations));
    }

    public Builder errorMessageOperations(final List<Operation<String>> operations) {
      errorMessageOperations = addValuesToList(errorMessageOperations, operations);
      return this;
    }

    public Builder hasRetriesLeft(final Boolean value) {
      hasRetriesLeft = value;
      return this;
    }

    @SafeVarargs
    public final Builder flowNodeIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return flowNodeIdOperations(collectValues(operation, operations));
    }

    public Builder flowNodeIdOperations(final List<Operation<String>> values) {
      flowNodeIdOperations = addValuesToList(flowNodeIdOperations, values);
      return this;
    }

    public Builder flowNodeIds(final String value, final String... values) {
      return flowNodeIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder hasFlowNodeInstanceIncident(final Boolean value) {
      hasFlowNodeInstanceIncident = value;
      return this;
    }

    public Builder flowNodeInstanceStateOperations(final List<Operation<String>> operations) {
      flowNodeInstanceStateOperations =
          addValuesToList(flowNodeInstanceStateOperations, operations);
      return this;
    }

    public Builder flowNodeInstanceState(final String value, final String... values) {
      return flowNodeInstanceStateOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder flowNodeInstanceStateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return flowNodeInstanceStateOperations(collectValues(operation, operations));
    }

    public Builder incidentErrorHashCodeOperations(final Integer value, final Integer... values) {
      return incidentErrorHashCodeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder incidentErrorHashCodeOperations(final List<Operation<Integer>> operations) {
      incidentErrorHashCodeOperations =
          addValuesToList(incidentErrorHashCodeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder incidentErrorHashCodeOperations(
        final Operation<Integer> operation, final Operation<Integer>... operations) {
      return incidentErrorHashCodeOperations(collectValues(operation, operations));
    }

    public Builder addOrOperation(final ProcessDefinitionStatisticsFilter orOperation) {
      if (orFilters == null) {
        orFilters = new ArrayList<>();
      }
      orFilters.add(orOperation);
      return this;
    }

    public Builder orFilters(final List<ProcessDefinitionStatisticsFilter> orFilters) {
      this.orFilters = orFilters;
      return this;
    }

    @Override
    public ProcessDefinitionStatisticsFilter build() {
      return new ProcessDefinitionStatisticsFilter(
          processDefinitionKey,
          Objects.requireNonNullElse(processInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(parentProcessInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(parentFlowNodeInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(startDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(endDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(stateOperations, Collections.emptyList()),
          hasIncident,
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(variableFilters, Collections.emptyList()),
          Objects.requireNonNullElse(errorMessageOperations, Collections.emptyList()),
          Objects.requireNonNullElse(batchOperationIdOperations, Collections.emptyList()),
          hasRetriesLeft,
          Objects.requireNonNullElse(flowNodeIdOperations, Collections.emptyList()),
          hasFlowNodeInstanceIncident,
          Objects.requireNonNullElse(flowNodeInstanceStateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(incidentErrorHashCodeOperations, Collections.emptyList()),
          orFilters);
    }
  }
}

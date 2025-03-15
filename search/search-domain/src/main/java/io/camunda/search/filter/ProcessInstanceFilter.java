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

import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ProcessInstanceFilter(
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<String>> processDefinitionIdOperations,
    List<Operation<String>> processDefinitionNameOperations,
    List<Operation<Integer>> processDefinitionVersionOperations,
    List<Operation<String>> processDefinitionVersionTagOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
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
    List<Operation<String>> errorMessageOperations,
    Boolean hasRetriesLeft,
    List<Operation<String>> flowNodeIdOperations,
    Boolean hasFlowNodeInstanceIncident,
    List<Operation<String>> flowNodeInstanceStateOperations,
    List<Integer> incidentErrorHashCodes)
    implements FilterBase {

  public Builder toBuilder() {
    return new Builder()
        .processInstanceKeyOperations(processInstanceKeyOperations)
        .processDefinitionIdOperations(processDefinitionIdOperations)
        .processDefinitionNameOperations(processDefinitionNameOperations)
        .processDefinitionVersionOperations(processDefinitionVersionOperations)
        .processDefinitionVersionTagOperations(processDefinitionVersionTagOperations)
        .processDefinitionKeyOperations(processDefinitionKeyOperations)
        .parentProcessInstanceKeyOperations(parentProcessInstanceKeyOperations)
        .parentFlowNodeInstanceKeyOperations(parentFlowNodeInstanceKeyOperations)
        .startDateOperations(startDateOperations)
        .endDateOperations(endDateOperations)
        .stateOperations(stateOperations)
        .hasIncident(hasIncident)
        .tenantIdOperations(tenantIdOperations)
        .variables(variableFilters)
        .batchOperationIdOperations(batchOperationIdOperations);
  }

  public static final class Builder implements ObjectBuilder<ProcessInstanceFilter> {

    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<String>> processDefinitionIdOperations;
    private List<Operation<String>> processDefinitionNameOperations;
    private List<Operation<Integer>> processDefinitionVersionOperations;
    private List<Operation<String>> processDefinitionVersionTagOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<Long>> parentProcessInstanceKeyOperations;
    private List<Operation<Long>> parentFlowNodeInstanceKeyOperations;
    private List<Operation<OffsetDateTime>> startDateOperations;
    private List<Operation<OffsetDateTime>> endDateOperations;
    private List<Operation<String>> stateOperations;
    private Boolean hasIncident;
    private List<Operation<String>> tenantIdOperations;
    private List<VariableValueFilter> variableFilters;
    private List<Operation<String>> errorMessageOperations;
    private Boolean hasRetriesLeft;
    private List<Operation<String>> flowNodeIdOperations;
    private Boolean hasFlowNodeInstanceIncident;
    private List<Operation<String>> flowNodeInstanceStateOperations;
    private List<Integer> incidentErrorHashCodes;

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionIdOperations(final List<Operation<String>> operations) {
      processDefinitionIdOperations = addValuesToList(processDefinitionIdOperations, operations);
      return this;
    }

    public Builder processDefinitionIds(final String value, final String... values) {
      return processDefinitionIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processDefinitionIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionIdOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionNameOperations(final List<Operation<String>> operations) {
      processDefinitionNameOperations =
          addValuesToList(processDefinitionNameOperations, operations);
      return this;
    }

    public Builder processDefinitionNames(final String value, final String... values) {
      return processDefinitionNameOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processDefinitionNameOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionNameOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionVersionOperations(final List<Operation<Integer>> operations) {
      processDefinitionVersionOperations =
          addValuesToList(processDefinitionVersionOperations, operations);
      return this;
    }

    public Builder processDefinitionVersions(final Integer value, final Integer... values) {
      return processDefinitionVersionOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processDefinitionVersionOperations(
        final Operation<Integer> operation, final Operation<Integer>... operations) {
      return processDefinitionVersionOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionVersionTagOperations(final List<Operation<String>> values) {
      processDefinitionVersionTagOperations =
          addValuesToList(processDefinitionVersionTagOperations, values);
      return this;
    }

    public Builder processDefinitionVersionTags(final String value, final String... values) {
      return processDefinitionVersionTagOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder processDefinitionVersionTagOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionVersionTagOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionKeyOperations(final List<Operation<Long>> operations) {
      processDefinitionKeyOperations = addValuesToList(processDefinitionKeyOperations, operations);
      return this;
    }

    public Builder processDefinitionKeys(final Long processDefinitionKeys) {
      return processDefinitionKeyOperations(
          FilterUtil.mapDefaultToOperation(processDefinitionKeys));
    }

    @SafeVarargs
    public final Builder processDefinitionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processDefinitionKeyOperations(collectValues(operation, operations));
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

    public Builder incidentErrorHashCodes(final Integer value, final Integer... values) {
      return incidentErrorHashCodes(collectValues(value, values));
    }

    public Builder incidentErrorHashCodes(final List<Integer> values) {
      incidentErrorHashCodes = addValuesToList(incidentErrorHashCodes, values);
      return this;
    }

    public static Builder from(final ProcessInstanceFilter original) {
      return new Builder()
          .processInstanceKeyOperations(original.processInstanceKeyOperations)
          .processDefinitionIdOperations(original.processDefinitionIdOperations)
          .processDefinitionNameOperations(original.processDefinitionNameOperations)
          .processDefinitionVersionOperations(original.processDefinitionVersionOperations)
          .processDefinitionVersionTagOperations(original.processDefinitionVersionTagOperations)
          .processDefinitionKeyOperations(original.processDefinitionKeyOperations)
          .parentProcessInstanceKeyOperations(original.parentProcessInstanceKeyOperations)
          .parentFlowNodeInstanceKeyOperations(original.parentFlowNodeInstanceKeyOperations)
          .startDateOperations(original.startDateOperations)
          .endDateOperations(original.endDateOperations)
          .stateOperations(original.stateOperations)
          .hasIncident(original.hasIncident)
          .variables(original.variableFilters)
          .tenantIdOperations(original.tenantIdOperations)
          .batchOperationIdOperations(original.batchOperationIdOperations)
          .errorMessageOperations(original.errorMessageOperations)
          .incidentErrorHashCodes(original.incidentErrorHashCodes);
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
          Objects.requireNonNullElse(startDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(endDateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(stateOperations, Collections.emptyList()),
          hasIncident,
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(variableFilters, Collections.emptyList()),
          Objects.requireNonNullElse(errorMessageOperations, Collections.emptyList()),
          Objects.requireNonNullElse(batchOperationIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(errorMessageOperations, Collections.emptyList()),
          hasRetriesLeft,
          Objects.requireNonNullElse(flowNodeIdOperations, Collections.emptyList()),
          hasFlowNodeInstanceIncident,
          Objects.requireNonNullElse(flowNodeInstanceStateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(incidentErrorHashCodes, Collections.emptyList()));
    }
  }
}

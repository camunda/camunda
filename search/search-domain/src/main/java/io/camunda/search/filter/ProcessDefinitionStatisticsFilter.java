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
    List<VariableValueFilter> variableFilters)
    implements FilterBase {

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
          Objects.requireNonNullElse(variableFilters, Collections.emptyList()));
    }
  }
}

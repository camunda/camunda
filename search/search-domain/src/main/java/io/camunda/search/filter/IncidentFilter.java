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

public record IncidentFilter(
    List<Operation<Long>> incidentKeyOperations,
    List<Operation<Long>> processDefinitionKeyOperations,
    List<Operation<String>> processDefinitionIdOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<String>> errorTypeOperations,
    List<Operation<String>> errorMessageOperations,
    List<Operation<Integer>> errorMessageHashOperations,
    List<Operation<String>> flowNodeIdOperations,
    List<Operation<Long>> flowNodeInstanceKeyOperations,
    List<Operation<OffsetDateTime>> creationTimeOperations,
    List<Operation<String>> stateOperations,
    List<Operation<String>> treePathOperations,
    List<Operation<Long>> jobKeyOperations,
    List<Operation<String>> tenantIdOperations)
    implements FilterBase {

  public Builder toBuilder() {
    return new Builder()
        .incidentKeyOperations(incidentKeyOperations)
        .processDefinitionKeyOperations(processDefinitionKeyOperations)
        .processDefinitionIdOperations(processDefinitionIdOperations)
        .processInstanceKeyOperations(processInstanceKeyOperations)
        .errorTypeOperations(errorTypeOperations)
        .errorMessageOperations(errorMessageOperations)
        .errorMessageHashOperations(errorMessageHashOperations)
        .flowNodeIdOperations(flowNodeIdOperations)
        .flowNodeInstanceKeyOperations(flowNodeInstanceKeyOperations)
        .creationTimeOperations(creationTimeOperations)
        .stateOperations(stateOperations)
        .treePathOperations(treePathOperations)
        .jobKeyOperations(jobKeyOperations)
        .tenantIdOperations(tenantIdOperations);
  }

  public static final class Builder implements ObjectBuilder<IncidentFilter> {

    private List<Operation<Long>> incidentKeyOperations;
    private List<Operation<Long>> processDefinitionKeyOperations;
    private List<Operation<String>> processDefinitionIdOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<String>> errorTypeOperations;
    private List<Operation<String>> errorMessageOperations;
    private List<Operation<Integer>> errorMessageHashOperations;
    private List<Operation<String>> flowNodeIdOperations;
    private List<Operation<Long>> flowNodeInstanceKeyOperations;
    private List<Operation<OffsetDateTime>> creationTimeOperations;
    private List<Operation<String>> stateOperations;
    private List<Operation<String>> treePathOperations;
    private List<Operation<Long>> jobKeyOperations;
    private List<Operation<String>> tenantIdOperations;

    public Builder incidentKeys(final Long value, final Long... values) {
      return incidentKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder incidentKeyOperations(final List<Operation<Long>> operations) {
      incidentKeyOperations = addValuesToList(incidentKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder incidentKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return incidentKeyOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionKeys(final Long value, final Long... values) {
      return processDefinitionKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder processDefinitionKeyOperations(final List<Operation<Long>> operations) {
      processDefinitionKeyOperations = addValuesToList(processDefinitionKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processDefinitionKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processDefinitionKeyOperations(collectValues(operation, operations));
    }

    public Builder processDefinitionIds(final String value, final String... values) {
      return processDefinitionIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder processDefinitionIdOperations(final List<Operation<String>> operations) {
      processDefinitionIdOperations = addValuesToList(processDefinitionIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processDefinitionIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return processDefinitionIdOperations(collectValues(operation, operations));
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder processInstanceKeyOperations(final List<Operation<Long>> operations) {
      processInstanceKeyOperations = addValuesToList(processInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder processInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return processInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder errorTypes(final String value, final String... values) {
      return errorTypeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder errorTypeOperations(final List<Operation<String>> operations) {
      errorTypeOperations = addValuesToList(errorTypeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder errorTypeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return errorTypeOperations(collectValues(operation, operations));
    }

    public Builder errorMessages(final String value, final String... values) {
      return errorMessageOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder errorMessageOperations(final List<Operation<String>> values) {
      errorMessageOperations = addValuesToList(errorMessageOperations, values);
      return this;
    }

    @SafeVarargs
    public final Builder errorMessageOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return errorMessageOperations(collectValues(operation, operations));
    }

    public Builder errorMessageHashes(final Integer value, final Integer... values) {
      return errorMessageHashOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder errorMessageHashOperations(final List<Operation<Integer>> operations) {
      errorMessageHashOperations = addValuesToList(errorMessageHashOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder errorMessageHashOperations(
        final Operation<Integer> operation, final Operation<Integer>... operations) {
      return errorMessageHashOperations(collectValues(operation, operations));
    }

    public Builder creationTime(final OffsetDateTime value) {
      return creationTimeOperations(FilterUtil.mapDefaultToOperation(value));
    }

    public Builder creationTimeOperations(final List<Operation<OffsetDateTime>> operations) {
      creationTimeOperations = addValuesToList(creationTimeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder creationTimeOperations(
        final Operation<OffsetDateTime> operation, final Operation<OffsetDateTime>... operations) {
      return creationTimeOperations(collectValues(operation, operations));
    }

    public Builder flowNodeIds(final String value, final String... values) {
      return flowNodeIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder flowNodeIdOperations(final List<Operation<String>> operations) {
      flowNodeIdOperations = addValuesToList(flowNodeIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder flowNodeIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return flowNodeIdOperations(collectValues(operation, operations));
    }

    public Builder flowNodeInstanceKeys(final Long value, final Long... values) {
      return flowNodeInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder flowNodeInstanceKeyOperations(final List<Operation<Long>> operations) {
      flowNodeInstanceKeyOperations = addValuesToList(flowNodeInstanceKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder flowNodeInstanceKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return flowNodeInstanceKeyOperations(collectValues(operation, operations));
    }

    public Builder states(final String value, final String... values) {
      return stateOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder stateOperations(final List<Operation<String>> values) {
      stateOperations = addValuesToList(stateOperations, values);
      return this;
    }

    @SafeVarargs
    public final Builder stateOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return stateOperations(collectValues(operation, operations));
    }

    public Builder treePaths(final String value, final String... values) {
      return treePathOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder treePathOperations(final List<Operation<String>> operations) {
      treePathOperations = addValuesToList(treePathOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder treePathOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return treePathOperations(collectValues(operation, operations));
    }

    public Builder jobKeys(final Long value, final Long... values) {
      return jobKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder jobKeyOperations(final List<Operation<Long>> operations) {
      jobKeyOperations = addValuesToList(jobKeyOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder jobKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return jobKeyOperations(collectValues(operation, operations));
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder tenantIdOperations(final List<Operation<String>> operations) {
      tenantIdOperations = addValuesToList(tenantIdOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder tenantIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return tenantIdOperations(collectValues(operation, operations));
    }

    @Override
    public IncidentFilter build() {
      return new IncidentFilter(
          Objects.requireNonNullElse(incidentKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processDefinitionIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(processInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(errorTypeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(errorMessageOperations, Collections.emptyList()),
          Objects.requireNonNullElse(errorMessageHashOperations, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeIdOperations, Collections.emptyList()),
          Objects.requireNonNullElse(flowNodeInstanceKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(creationTimeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(stateOperations, Collections.emptyList()),
          Objects.requireNonNullElse(treePathOperations, Collections.emptyList()),
          Objects.requireNonNullElse(jobKeyOperations, Collections.emptyList()),
          Objects.requireNonNullElse(tenantIdOperations, Collections.emptyList()));
    }
  }
}

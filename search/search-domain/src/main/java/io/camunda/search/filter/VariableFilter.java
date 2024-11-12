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
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import io.camunda.util.FilterUtil;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record VariableFilter(
    Map<String, List<UntypedOperation>> variableOperations,
    List<Operation<Long>> scopeKeyOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Long>> variableKeyOperations,
    List<String> tenantIds,
    Boolean isTruncated)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<VariableFilter> {
    private Map<String, List<UntypedOperation>> variableOperations;
    private List<Operation<Long>> scopeKeyOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<Long>> variableKeyOperations;
    private List<String> tenantIds;
    private Boolean isTruncated;

    public Builder variableOperations(final String name, final List<Operation<Object>> operations) {
      return variableUntypedOperations(
          name,
          ofNullable(operations).orElse(emptyList()).stream().map(UntypedOperation::of).toList());
    }

    private Builder variableUntypedOperations(
        final String name, final List<UntypedOperation> operations) {
      variableOperations = Objects.requireNonNullElse(variableOperations, new HashMap<>());
      variableOperations.compute(name, (k, list) -> addValuesToList(list, operations));
      return this;
    }

    @SafeVarargs
    public final Builder variableOperations(
        final String name,
        final Operation<Object> operation,
        final Operation<Object>... operations) {
      return variableOperations(name, collectValues(operation, operations));
    }

    public Builder variable(final String name) {
      return variableOperations(name, Operation.exists(true));
    }

    public Builder scopeKeyOperations(final List<Operation<Long>> operations) {
      scopeKeyOperations = addValuesToList(scopeKeyOperations, operations);
      return this;
    }

    public Builder scopeKeys(final Long value, final Long... values) {
      return scopeKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder scopeKeys(final List<Long> values) {
      return scopeKeyOperations(FilterUtil.mapDefaultToOperation(values));
    }

    @SafeVarargs
    public final Builder scopeKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return scopeKeyOperations(collectValues(operation, operations));
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

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder variableKeyOperations(final List<Operation<Long>> operations) {
      variableKeyOperations = addValuesToList(variableKeyOperations, operations);
      return this;
    }

    public Builder variableKeys(final Long value, final Long... values) {
      return variableKeyOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    @SafeVarargs
    public final Builder variableKeyOperations(
        final Operation<Long> operation, final Operation<Long>... operations) {
      return variableKeyOperations(collectValues(operation, operations));
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIds(collectValues(value, values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder isTruncated(final Boolean value) {
      isTruncated = value;
      return this;
    }

    public Builder copyFrom(final VariableFilter sourceFilter) {
      sourceFilter.variableOperations().forEach(this::variableUntypedOperations);
      return scopeKeyOperations(sourceFilter.scopeKeyOperations())
          .processInstanceKeyOperations(sourceFilter.processInstanceKeyOperations())
          .variableKeyOperations(sourceFilter.variableKeyOperations())
          .tenantIds(sourceFilter.tenantIds())
          .isTruncated(sourceFilter.isTruncated());
    }

    @Override
    public VariableFilter build() {
      return new VariableFilter(
          Objects.requireNonNullElseGet(variableOperations, Collections::emptyMap),
          Objects.requireNonNullElseGet(scopeKeyOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(processInstanceKeyOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(variableKeyOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(tenantIds, Collections::emptyList),
          isTruncated);
    }
  }
}

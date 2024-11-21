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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record VariableFilter(
    List<Operation<String>> nameOperations,
    List<UntypedOperation> valueOperations,
    List<Operation<Long>> scopeKeyOperations,
    List<Operation<Long>> processInstanceKeyOperations,
    List<Operation<Long>> variableKeyOperations,
    List<String> tenantIds,
    Boolean isTruncated)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<VariableFilter> {
    private List<Operation<String>> nameOperations;
    private List<UntypedOperation> valueOperations;
    private List<Operation<Long>> scopeKeyOperations;
    private List<Operation<Long>> processInstanceKeyOperations;
    private List<Operation<Long>> variableKeyOperations;
    private List<String> tenantIds;
    private Boolean isTruncated;

    public Builder nameOperations(final List<Operation<String>> operations) {
      nameOperations = addValuesToList(nameOperations, operations);
      return this;
    }

    public Builder names(final String value, final String... values) {
      return nameOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder names(final List<String> values) {
      return nameOperations(FilterUtil.mapDefaultToOperation(values));
    }

    @SafeVarargs
    public final Builder nameOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return nameOperations(collectValues(operation, operations));
    }

    public Builder valueOperations(final List<Operation<String>> operations) {
      final List<Operation<String>> ops =
          Objects.requireNonNullElse(operations, Collections.emptyList());
      valueOperations =
          addValuesToList(valueOperations, ops.stream().map(UntypedOperation::of).toList());
      return this;
    }

    private Builder valueUntypedOperations(final List<UntypedOperation> operations) {
      valueOperations = addValuesToList(valueOperations, operations);
      return this;
    }

    public Builder values(final String value, final String... values) {
      return valueOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder values(final List<String> values) {
      return valueOperations(FilterUtil.mapDefaultToOperation(values));
    }

    @SafeVarargs
    public final Builder valueOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return valueOperations(collectValues(operation, operations));
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
      return nameOperations(sourceFilter.nameOperations)
          .valueUntypedOperations(sourceFilter.valueOperations)
          .scopeKeyOperations(sourceFilter.scopeKeyOperations())
          .processInstanceKeyOperations(sourceFilter.processInstanceKeyOperations())
          .variableKeyOperations(sourceFilter.variableKeyOperations())
          .tenantIds(sourceFilter.tenantIds())
          .isTruncated(sourceFilter.isTruncated());
    }

    @Override
    public VariableFilter build() {
      return new VariableFilter(
          Objects.requireNonNullElseGet(nameOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(valueOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(scopeKeyOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(processInstanceKeyOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(variableKeyOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(tenantIds, Collections::emptyList),
          isTruncated);
    }
  }
}

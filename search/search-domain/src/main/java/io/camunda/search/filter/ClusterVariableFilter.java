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

public record ClusterVariableFilter(
    List<Operation<String>> nameOperations,
    List<UntypedOperation> valueOperations,
    List<Operation<String>> scopeOperations,
    List<Operation<String>> tenantIdOperations,
    Boolean isTruncated)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<ClusterVariableFilter> {
    List<Operation<String>> nameOperations;
    List<UntypedOperation> valueOperations;
    List<Operation<String>> scopeOperations;
    List<Operation<String>> tenantIdOperations;
    Boolean isTruncated;

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

    public Builder valueOperation(final UntypedOperation operation) {
      valueOperations = addValuesToList(valueOperations, Collections.singletonList(operation));
      return this;
    }

    public Builder scopeOperations(final List<Operation<String>> operations) {
      scopeOperations = addValuesToList(scopeOperations, operations);
      return this;
    }

    public Builder scopes(final String value, final String... values) {
      return scopeOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder scopes(final List<String> values) {
      return scopeOperations(FilterUtil.mapDefaultToOperation(values));
    }

    @SafeVarargs
    public final Builder scopeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return scopeOperations(collectValues(operation, operations));
    }

    public Builder tenantIdOperations(final List<Operation<String>> operations) {
      tenantIdOperations = addValuesToList(tenantIdOperations, operations);
      return this;
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIdOperations(FilterUtil.mapDefaultToOperation(value, values));
    }

    public Builder tenantIds(final List<String> values) {
      return tenantIdOperations(FilterUtil.mapDefaultToOperation(values));
    }

    @SafeVarargs
    public final Builder tenantIdOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return tenantIdOperations(collectValues(operation, operations));
    }

    public Builder isTruncated(final Boolean value) {
      isTruncated = value;
      return this;
    }

    @Override
    public ClusterVariableFilter build() {
      return new ClusterVariableFilter(
          Objects.requireNonNullElseGet(nameOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(valueOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(scopeOperations, Collections::emptyList),
          Objects.requireNonNullElseGet(tenantIdOperations, Collections::emptyList),
          isTruncated);
    }
  }
}

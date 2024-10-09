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

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final record VariableFilter(
    List<VariableValueFilter> variableFilters,
    List<Long> scopeKeys,
    List<Long> processInstanceKeys,
    List<Long> variableKeys,
    List<String> tenantIds,
    boolean isTruncated)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<VariableFilter> {
    private List<VariableValueFilter> variableFilters;
    private List<Long> scopeKeys;
    private List<Long> processInstanceKeys;
    private List<Long> variableKeys;
    private List<String> tenantIds;
    private boolean isTruncated;

    public Builder variable(final List<VariableValueFilter> values) {
      variableFilters = addValuesToList(variableFilters, values);
      return this;
    }

    public Builder variable(final VariableValueFilter value, final VariableValueFilter... values) {
      return variable(collectValues(value, values));
    }

    public Builder variable(
        final Function<VariableValueFilter.Builder, ObjectBuilder<VariableValueFilter>> fn) {
      return variable(fn.apply(new VariableValueFilter.Builder()).build());
    }

    public Builder scopeKeys(final Long value, final Long... values) {
      return scopeKeys(collectValues(value, values));
    }

    public Builder scopeKeys(final List<Long> values) {
      scopeKeys = addValuesToList(scopeKeys, values);
      return this;
    }

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeys(collectValues(value, values));
    }

    public Builder processInstanceKeys(final List<Long> values) {
      processInstanceKeys = addValuesToList(processInstanceKeys, values);
      return this;
    }

    public Builder variableKeys(final Long value, final Long... values) {
      return variableKeys(collectValues(value, values));
    }

    public Builder variableKeys(final List<Long> values) {
      variableKeys = addValuesToList(variableKeys, values);
      return this;
    }

    public Builder tenantIds(final String value, final String... values) {
      return tenantIds(collectValues(value, values));
    }

    public Builder tenantIds(final List<String> values) {
      tenantIds = addValuesToList(tenantIds, values);
      return this;
    }

    public Builder isTruncated(final boolean value) {
      isTruncated = value;
      return this;
    }

    @Override
    public VariableFilter build() {
      return new VariableFilter(
          Objects.requireNonNullElseGet(variableFilters, Collections::emptyList),
          Objects.requireNonNullElseGet(scopeKeys, Collections::emptyList),
          Objects.requireNonNullElseGet(processInstanceKeys, Collections::emptyList),
          Objects.requireNonNullElseGet(variableKeys, Collections::emptyList),
          Objects.requireNonNullElseGet(tenantIds, Collections::emptyList),
          isTruncated);
    }
  }
}

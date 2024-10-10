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
import static io.camunda.util.CollectionUtil.collectValuesAsList;

import io.camunda.util.ObjectBuilder;
import io.camunda.util.advanced.query.filter.FieldFilter;
import io.camunda.util.advanced.query.filter.Operator;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final record VariableFilter(
    List<VariableValueFilter> variableFilters,
    FieldFilter<Object> scopeKeys,
    FieldFilter<Object> processInstanceKeys,
    FieldFilter<Object> variableKeys,
    FieldFilter<Object> tenantIds,
    boolean isTruncated)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<VariableFilter> {
    private List<VariableValueFilter> variableFilters;
    private FieldFilter<Object> scopeKeys;
    private FieldFilter<Object> processInstanceKeys;
    private FieldFilter<Object> variableKeys;
    private FieldFilter<Object> tenantIds;
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

    // new builders for scopeKeys
    public Builder scopeKeys(final Operator operator, final List<Long> values) {
      scopeKeys = new FieldFilter<>(operator, values);
      return this;
    }

    public Builder scopeKeys(final Operator operator, final Long... values) {
      scopeKeys = new FieldFilter<>(operator, collectValuesAsList(values));
      return this;
    }

    // new builders for ProcessInstanceKeys
    public Builder processInstanceKeys(final Operator operator, final List<Long> values) {
      processInstanceKeys = new FieldFilter<>(operator, values);
      return this;
    }

    public Builder processInstanceKeys(final Operator operator, final Long... values) {
      processInstanceKeys = new FieldFilter<>(operator, collectValuesAsList(values));
      return this;
    }

    public Builder variableKeys(final Operator operator, final List<Long> values) {
      variableKeys = new FieldFilter<>(operator, values);
      return this;
    }

    public Builder variableKeys(final Operator operator, final Long... values) {
      variableKeys = new FieldFilter<>(operator, collectValuesAsList(values));
      return this;
    }

    // new builders for tenantId
    public Builder tenantIds(final Operator operator, final List<String> values) {
      tenantIds = new FieldFilter<>(operator, values);
      return this;
    }

    public Builder tenantIds(final Operator operator, final String... values) {
      tenantIds = new FieldFilter<>(operator, collectValuesAsList(values));
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
          Objects.requireNonNullElse(
              scopeKeys, new FieldFilter<>(Operator.EQ, Collections.emptyList())),
          Objects.requireNonNullElse(
              processInstanceKeys, new FieldFilter<>(Operator.EQ, Collections.emptyList())),
          Objects.requireNonNullElse(
              variableKeys, new FieldFilter<>(Operator.EQ, Collections.emptyList())),
          Objects.requireNonNullElse(
              tenantIds, new FieldFilter<>(Operator.EQ, Collections.emptyList())),
          isTruncated);
    }
  }
}

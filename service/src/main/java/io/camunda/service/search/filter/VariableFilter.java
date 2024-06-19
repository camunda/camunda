/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

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
    boolean orConditions,
    boolean onlyRuntimeVariables)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<VariableFilter> {
    private List<VariableValueFilter> variableFilters;
    private List<Long> scopeKeys;
    private List<Long> processInstanceKeys;
    private boolean orConditions;
    private boolean
        onlyRuntimeVariables; // internally used to restric the search to variables for running

    // tasks/processes to improve the performance

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

    public Builder orConditions(final boolean orConditions) {
      this.orConditions = orConditions;
      return this;
    }

    public Builder onlyRuntimeVariables(final boolean onlyRuntimeVariables) {
      this.onlyRuntimeVariables = onlyRuntimeVariables;
      return this;
    }

    @Override
    public VariableFilter build() {
      return new VariableFilter(
          Objects.requireNonNullElseGet(variableFilters, Collections::emptyList),
          Objects.requireNonNullElseGet(scopeKeys, Collections::emptyList),
          Objects.requireNonNullElseGet(processInstanceKeys, Collections::emptyList),
          orConditions,
          onlyRuntimeVariables);
    }
  }
}

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

public final record ProcessInstanceFilter(
    List<Long> processInstanceKeys,
    boolean running,
    boolean active,
    boolean incidents,
    boolean finished,
    boolean completed,
    boolean canceled,
    boolean retriesLeft,
    List<VariableValueFilter> variableFilters,
    DateValueFilter startDateFilter,
    DateValueFilter endDateFilter)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<ProcessInstanceFilter> {

    private List<Long> processInstanceKeys;
    private boolean running;
    private boolean active;
    private boolean incidents;
    private boolean finished;
    private boolean completed;
    private boolean canceled;
    private boolean retriesLeft;
    private List<VariableValueFilter> variableFilters;
    private DateValueFilter startDateFilter;
    private DateValueFilter endDateFilter;

    public Builder processInstanceKeys(final Long value, final Long... values) {
      return processInstanceKeys(collectValues(value, values));
    }

    public Builder processInstanceKeys(final List<Long> values) {
      processInstanceKeys = addValuesToList(processInstanceKeys, values);
      return this;
    }

    public Builder running() {
      running = true;
      return this;
    }

    public Builder active() {
      active = true;
      return this;
    }

    public Builder incidents() {
      incidents = true;
      return this;
    }

    public Builder finished() {
      finished = true;
      return this;
    }

    public Builder completed() {
      completed = true;
      return this;
    }

    public Builder canceled() {
      canceled = true;
      return this;
    }

    public Builder retriesLeft() {
      retriesLeft = true;
      return this;
    }

    public Builder variable(final List<VariableValueFilter> values) {
      variableFilters = addValuesToList(variableFilters, values);
      return this;
    }

    public Builder variable(final VariableValueFilter value, final VariableValueFilter... values) {
      return variable(collectValues(value, values));
    }

    public Builder variable(
        final Function<VariableValueFilter.Builder, ObjectBuilder<VariableValueFilter>> fn) {
      return variable(FilterBuilders.variableValue(fn));
    }

    public Builder startDate(final DateValueFilter value) {
      startDateFilter = value;
      return this;
    }

    public Builder startDate(
        final Function<DateValueFilter.Builder, ObjectBuilder<DateValueFilter>> fn) {
      return startDate(FilterBuilders.dateValue(fn));
    }

    public Builder endDate(final DateValueFilter value) {
      endDateFilter = value;
      return this;
    }

    public Builder endDate(
        final Function<DateValueFilter.Builder, ObjectBuilder<DateValueFilter>> fn) {
      return endDate(FilterBuilders.dateValue(fn));
    }

    @Override
    public ProcessInstanceFilter build() {
      return new ProcessInstanceFilter(
          Objects.requireNonNullElse(processInstanceKeys, Collections.emptyList()),
          running,
          active,
          incidents,
          finished,
          completed,
          canceled,
          retriesLeft,
          Objects.requireNonNullElse(variableFilters, Collections.emptyList()),
          startDateFilter,
          endDateFilter);
    }
  }
}

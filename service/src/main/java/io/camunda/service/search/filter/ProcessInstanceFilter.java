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

import io.camunda.service.entities.AuthorizationEntity;
import io.camunda.service.search.filter.VariableValueFilter.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class ProcessInstanceFilter implements FilterBase {
  private final List<Long> processInstanceKeys;
  private final boolean running;
  private final boolean active;
  private final boolean incidents;
  private final boolean finished;
  private final boolean completed;
  private final boolean canceled;
  private final boolean retriesLeft;
  private final List<VariableValueFilter> variableFilters;
  private final DateValueFilter startDateFilter;
  private final DateValueFilter endDateFilter;

  private List<AuthorizationEntity> authorizations;

  public ProcessInstanceFilter(
      final List<Long> processInstanceKeys,
      final boolean running,
      final boolean active,
      final boolean incidents,
      final boolean finished,
      final boolean completed,
      final boolean canceled,
      final boolean retriesLeft,
      final List<VariableValueFilter> variableFilters,
      final DateValueFilter startDateFilter,
      final DateValueFilter endDateFilter) {
    this.processInstanceKeys = processInstanceKeys;
    this.running = running;
    this.active = active;
    this.incidents = incidents;
    this.finished = finished;
    this.completed = completed;
    this.canceled = canceled;
    this.retriesLeft = retriesLeft;
    this.variableFilters = variableFilters;
    this.startDateFilter = startDateFilter;
    this.endDateFilter = endDateFilter;
  }

  public List<Long> processInstanceKeys() {
    return processInstanceKeys;
  }

  public boolean running() {
    return running;
  }

  public boolean active() {
    return active;
  }

  public boolean incidents() {
    return incidents;
  }

  public boolean finished() {
    return finished;
  }

  public boolean completed() {
    return completed;
  }

  public boolean canceled() {
    return canceled;
  }

  public boolean retriesLeft() {
    return retriesLeft;
  }

  public List<VariableValueFilter> variableFilters() {
    return variableFilters;
  }

  public DateValueFilter startDateFilter() {
    return startDateFilter;
  }

  public DateValueFilter endDateFilter() {
    return endDateFilter;
  }

  public List<AuthorizationEntity> authorizations() {
    return authorizations;
  }

  public void setAuthorizations(List<AuthorizationEntity> authorizations) {
    this.authorizations = authorizations;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        processInstanceKeys,
        running,
        active,
        incidents,
        finished,
        completed,
        canceled,
        retriesLeft,
        variableFilters,
        startDateFilter,
        endDateFilter);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (ProcessInstanceFilter) obj;
    return Objects.equals(processInstanceKeys, that.processInstanceKeys)
        && running == that.running
        && active == that.active
        && incidents == that.incidents
        && finished == that.finished
        && completed == that.completed
        && canceled == that.canceled
        && retriesLeft == that.retriesLeft
        && Objects.equals(variableFilters, that.variableFilters)
        && Objects.equals(startDateFilter, that.startDateFilter)
        && Objects.equals(endDateFilter, that.endDateFilter);
  }

  @Override
  public String toString() {
    return "ProcessInstanceFilter["
        + "processInstanceKeys="
        + processInstanceKeys
        + ", "
        + "running="
        + running
        + ", "
        + "active="
        + active
        + ", "
        + "incidents="
        + incidents
        + ", "
        + "finished="
        + finished
        + ", "
        + "completed="
        + completed
        + ", "
        + "canceled="
        + canceled
        + ", "
        + "retriesLeft="
        + retriesLeft
        + ", "
        + "variableFilters="
        + variableFilters
        + ", "
        + "startDateFilter="
        + startDateFilter
        + ", "
        + "endDateFilter="
        + endDateFilter
        + ']';
  }

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

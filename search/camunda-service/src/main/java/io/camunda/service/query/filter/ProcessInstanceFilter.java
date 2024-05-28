/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static io.camunda.data.clients.query.DataStoreQueryBuilders.and;
import static io.camunda.data.clients.query.DataStoreQueryBuilders.exists;
import static io.camunda.data.clients.query.DataStoreQueryBuilders.hasChildQuery;
import static io.camunda.data.clients.query.DataStoreQueryBuilders.longTerms;
import static io.camunda.data.clients.query.DataStoreQueryBuilders.matchAll;
import static io.camunda.data.clients.query.DataStoreQueryBuilders.not;
import static io.camunda.data.clients.query.DataStoreQueryBuilders.or;
import static io.camunda.data.clients.query.DataStoreQueryBuilders.term;

import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class ProcessInstanceFilter implements FilterBody {

  private final List<Long> processInstanceKeys;

  private final boolean running;
  private final boolean active;
  private final boolean incidents;

  private final boolean finished;
  private final boolean completed;
  private final boolean canceled;

  private final boolean retriesLeft;
  private final List<VariableValueFilter> variableFilters;

  private ProcessInstanceFilter(final Builder builder) {
    processInstanceKeys = builder.processInstanceKeys;
    running = builder.running;
    active = builder.active;
    incidents = builder.incidents;
    finished = builder.finished;
    completed = builder.completed;
    canceled = builder.canceled;
    retriesLeft = builder.retriesLeft;
    variableFilters = builder.variableFilters;
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

  @Override
  public List<String> index() {
    if (finished || completed || canceled) {
      return Arrays.asList("operate-list-view-8.3.0_alias");
    } else {
      return Arrays.asList("operate-list-view-8.3.0_");
    }
  }

  @Override
  public DataStoreQuery toSearchQuery() {
    final var joinRelationQuery = term("joinRelation", "processInstance");
    final var processInstanceKeysQuery = longTerms("processInstanceKey", processInstanceKeys);
    final var processInstanceStateQuery = getProcessInstanceStateQuery();
    final var retriesLeftQuery = getRetriesLeftQuery();
    final var variablesQuery = getVariablesQuery();

    return and(
        joinRelationQuery,
        processInstanceKeysQuery,
        processInstanceStateQuery,
        retriesLeftQuery,
        variablesQuery);
  }

  private DataStoreQuery getProcessInstanceStateQuery() {
    if (running && finished && active && incidents && completed && canceled) {
      // select all
      return matchAll();
    }

    DataStoreQuery runningQuery = null;

    if (running && (active || incidents)) {
      // running query

      runningQuery = not(exists("endDate"));

      final var activeQuery = getActiveQuery();
      final var incidentsQuery = getIncidentsQuery();

      runningQuery = and(runningQuery, or(activeQuery, incidentsQuery));
    }

    DataStoreQuery finishedQuery = null;

    if (finished && (completed || canceled)) {

      // add finished query
      finishedQuery = exists("endDate");

      final var completedQuery = getCompletedQuery();
      final var canceledQuery = getCanceledQuery();

      finishedQuery = and(finishedQuery, or(completedQuery, canceledQuery));
    }

    final var processInstanceQuery = or(runningQuery, finishedQuery);

    return processInstanceQuery;
  }

  private DataStoreQuery getCanceledQuery() {
    if (canceled) {
      return term("state", "CANCELED");
    }

    return null;
  }

  private DataStoreQuery getCompletedQuery() {
    if (completed) {
      return term("state", "COMPLETED");
    }

    return null;
  }

  private DataStoreQuery getIncidentsQuery() {
    if (incidents) {
      return term("incident", true);
    }

    return null;
  }

  private DataStoreQuery getActiveQuery() {
    if (active) {
      return term("incident", false);
    }

    return null;
  }

  private DataStoreQuery getRetriesLeftQuery() {
    if (retriesLeft) {
      final var retriesLeftQuery = term("jobFailedWithRetriesLeft", true);
      return hasChildQuery("activity", retriesLeftQuery);
    }

    return null;
  }

  private DataStoreQuery getVariablesQuery() {
    if (hasVariableFiltersDefined()) {
      final var queries =
          variableFilters.stream()
              .map(VariableValueFilter::toSearchQuery)
              .map((q) -> hasChildQuery("variable", q))
              .toList();
      return and(queries);
    }

    return null;
  }

  private boolean hasVariableFiltersDefined() {
    return variableFilters != null && !variableFilters.isEmpty();
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
        variableFilters);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessInstanceFilter that = (ProcessInstanceFilter) o;
    return running == that.running
        && active == that.active
        && incidents == that.incidents
        && finished == that.finished
        && completed == that.completed
        && canceled == that.canceled
        && retriesLeft == that.retriesLeft
        && Objects.equals(processInstanceKeys, that.processInstanceKeys)
        && Objects.equals(variableFilters, that.variableFilters);
  }

  public static final class Builder implements DataStoreObjectBuilder<ProcessInstanceFilter> {

    private List<Long> processInstanceKeys;
    private boolean running;
    private boolean active;
    private boolean incidents;

    private boolean finished;
    private boolean completed;
    private boolean canceled;

    private boolean retriesLeft;

    private final List<VariableValueFilter> variableFilters = new ArrayList<>();

    public Builder processInstanceKeys(
        final Long processInstanceKey, final Long... processInstanceKeys) {
      final var keys = new ArrayList<Long>();
      keys.add(processInstanceKey);
      keys.addAll(Arrays.asList(processInstanceKeys));
      processInstanceKeys(keys);
      return this;
    }

    public Builder processInstanceKeys(final List<Long> processInstanceKeys) {
      this.processInstanceKeys = processInstanceKeys;
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

    public Builder variable(final List<VariableValueFilter> filters) {
      if (filters != null && !filters.isEmpty()) {
        variableFilters.addAll(filters);
      }
      return this;
    }

    public Builder variable(
        final VariableValueFilter filter, final VariableValueFilter... filters) {
      variableFilters.add(filter);
      if (filters != null && filters.length > 0) {
        variableFilters.addAll(Arrays.asList(filters));
      }
      return this;
    }

    public Builder variable(
        final Function<VariableValueFilter.Builder, DataStoreObjectBuilder<VariableValueFilter>>
            fn) {
      return variable(FilterBuilders.variable(fn));
    }

    @Override
    public ProcessInstanceFilter build() {
      return new ProcessInstanceFilter(this);
    }
  }
}

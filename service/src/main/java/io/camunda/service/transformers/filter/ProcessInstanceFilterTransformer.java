/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.exists;
import static io.camunda.search.clients.query.SearchQueryBuilders.hasChildQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.DateValueFilter;
import io.camunda.service.search.filter.ProcessInstanceFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.service.transformers.filter.DateValueFilterTransformer.DateFieldFilter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class ProcessInstanceFilterTransformer
    implements FilterTransformer<ProcessInstanceFilter> {

  private final ServiceTransformers transformers;

  public ProcessInstanceFilterTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final ProcessInstanceFilter filter) {
    final var processInstanceKeys = filter.processInstanceKeys();

    final var joinRelationQuery = term("joinRelation", "processInstance");

    final var processInstanceKeysQuery = getProcessInstanceKeysQuery(processInstanceKeys);
    final var processInstanceStateQuery = getProcessInstanceStateQuery(filter);
    final var retriesLeftQuery = getRetriesLeftQuery(filter.retriesLeft());
    final var variablesQuery = getVariablesQuery(filter.variableFilters());
    final var startDateQuery = getStartDateQuery(filter.startDateFilter());
    final var endDateQuery = getEndDateQuery(filter.endDateFilter());

    return and(
        joinRelationQuery,
        processInstanceKeysQuery,
        processInstanceStateQuery,
        retriesLeftQuery,
        variablesQuery,
        startDateQuery,
        endDateQuery);
  }

  private SearchQuery getProcessInstanceKeysQuery(final List<Long> processInstanceKeys) {
    return longTerms("processInstanceKey", processInstanceKeys);
  }

  private SearchQuery getProcessInstanceStateQuery(final ProcessInstanceFilter filter) {
    final var running = filter.running();
    final var finished = filter.finished();
    final var active = filter.active();
    final var incidents = filter.incidents();
    final var completed = filter.completed();
    final var canceled = filter.canceled();

    if (running && finished && active && incidents && completed && canceled) {
      // select all
      return matchAll();
    }

    SearchQuery runningQuery = null;

    if (running && (active || incidents)) {
      // running query

      runningQuery = not(exists("endDate"));

      final var activeQuery = getActiveQuery(active);
      final var incidentsQuery = getIncidentsQuery(incidents);

      runningQuery = and(runningQuery, or(activeQuery, incidentsQuery));
    }

    SearchQuery finishedQuery = null;

    if (finished && (completed || canceled)) {

      // add finished query
      finishedQuery = exists("endDate");

      final var completedQuery = getCompletedQuery(completed);
      final var canceledQuery = getCanceledQuery(canceled);

      finishedQuery = and(finishedQuery, or(completedQuery, canceledQuery));
    }

    final var processInstanceQuery = or(runningQuery, finishedQuery);

    return processInstanceQuery;
  }

  private SearchQuery getCanceledQuery(final boolean canceled) {
    if (canceled) {
      return term("state", "CANCELED");
    }

    return null;
  }

  private SearchQuery getCompletedQuery(final boolean completed) {
    if (completed) {
      return term("state", "COMPLETED");
    }

    return null;
  }

  private SearchQuery getIncidentsQuery(final boolean incidents) {
    if (incidents) {
      return term("incident", true);
    }

    return null;
  }

  private SearchQuery getActiveQuery(final boolean active) {
    if (active) {
      return term("incident", false);
    }

    return null;
  }

  private SearchQuery getRetriesLeftQuery(final boolean retriesLeft) {
    if (retriesLeft) {
      final var retriesLeftQuery = term("jobFailedWithRetriesLeft", true);
      return hasChildQuery("activity", retriesLeftQuery);
    }

    return null;
  }

  private SearchQuery getVariablesQuery(final List<VariableValueFilter> variableFilters) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var transformer = getVariableValueFilterTransformer();
      final var queries =
          variableFilters.stream()
              .map(transformer::apply)
              .map((q) -> hasChildQuery("variable", q))
              .collect(Collectors.toList());
      return and(queries);
    }
    return null;
  }

  private SearchQuery getStartDateQuery(final DateValueFilter filter) {
    if (filter != null) {
      final var transformer = getDateValueFilterTransformer();
      return transformer.apply(new DateFieldFilter("startDate", filter));
    }
    return null;
  }

  private SearchQuery getEndDateQuery(final DateValueFilter filter) {
    if (filter != null) {
      final var transformer = getDateValueFilterTransformer();
      return transformer.apply(new DateFieldFilter("endDate", filter));
    }
    return null;
  }

  private FilterTransformer<DateFieldFilter> getDateValueFilterTransformer() {
    return transformers.getFilterTransformer(DateValueFilter.class);
  }

  private FilterTransformer<VariableValueFilter> getVariableValueFilterTransformer() {
    return transformers.getFilterTransformer(VariableValueFilter.class);
  }

  @Override
  public List<String> toIndices(ProcessInstanceFilter filter) {
    final var finished = filter.finished();
    final var completed = filter.completed();
    final var canceled = filter.canceled();

    if (finished || completed || canceled) {
      return Arrays.asList("operate-list-view-8.3.0_alias");
    } else {
      return Arrays.asList("operate-list-view-8.3.0_");
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.*;

import io.camunda.search.clients.query.SearchMatchQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.DateValueFilter;
import io.camunda.service.search.filter.ProcessInstanceFilter;
import io.camunda.service.search.filter.ProcessInstanceVariableFilter;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.service.transformers.filter.DateValueFilterTransformer.DateFieldFilter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public final class ProcessInstanceFilterTransformer
    implements FilterTransformer<ProcessInstanceFilter> {

  private static final String WILD_CARD = "*";

  private final ServiceTransformers transformers;

  public ProcessInstanceFilterTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final ProcessInstanceFilter filter) {

    final var isProcessInstanceQuery = getIsProcessInstanceQuery();
    final var runningFinishedQuery = getRunningFinishedQuery(filter);
    final var retriesLeftQuery = getRetriesLeftQuery(filter.retriesLeft());
    final var errorMessageQuery = getErrorMessageQuery(filter.errorMessage());
    final var activityIdQuery = getActivityIdQuery(filter);
    final var startDateQuery = getStartDateQuery(filter.startDate());
    final var endDateQuery = getEndDateQuery(filter.endDate());
    final var bpmnProcessIdsQuery = getBpmnProcessIdsQuery(filter.bpmnProcessIds());
    final var processDefinitionVersionsQuery =
        getProcessDefinitionVersionsQuery(filter.processDefinitionVersions());
    final var variableQuery = getProcessInstanceVariableQuery(filter.variable());
    final var batchOperationIdsQuery = getBatchOperationIdsQuery(filter.batchOperationIds());
    final var parentProcessInstanceKeys =
        getParentProcessInstanceKeysQuery(filter.parentProcessInstanceKeys());
    final var tenantIdsQuery = getTenantIdsQuery(filter.tenantIds());

    return and(
        isProcessInstanceQuery,
        runningFinishedQuery,
        retriesLeftQuery,
        errorMessageQuery,
        activityIdQuery,
        startDateQuery,
        endDateQuery,
        bpmnProcessIdsQuery,
        processDefinitionVersionsQuery,
        variableQuery,
        batchOperationIdsQuery,
        parentProcessInstanceKeys,
        tenantIdsQuery);
  }

  private SearchQuery getIsProcessInstanceQuery() {
    return term("joinRelation", "processInstance");
  }

  private SearchQuery getRunningFinishedQuery(final ProcessInstanceFilter filter) {
    final var running = filter.running();
    final var active = filter.active();
    final var incidents = filter.incidents();
    final var finished = filter.finished();
    final var completed = filter.completed();
    final var canceled = filter.canceled();

    if (!running && !finished) {
      // empty list should be returned
      return matchNone();
    }

    if (running && active && incidents && finished && completed && canceled) {
      // select all
      return null;
    }

    SearchQuery runningQuery = null;
    if (running && (active || incidents)) {
      // running query
      runningQuery = not(exists("endDate"));
      final var activeQuery = getActiveQuery(active);
      final var incidentsQuery = getIncidentsQuery(incidents);
      if (filter.activityId() == null && filter.active() && filter.incidents()) {
        // we request all running instances
      } else {
        // some of the queries may be null
        runningQuery = and(runningQuery, or(activeQuery, incidentsQuery));
      }
    }

    SearchQuery finishedQuery = null;
    if (finished && (completed || canceled)) {
      // finished query
      finishedQuery = exists("endDate");
      final var completedQuery = getCompletedQuery(completed);
      final var canceledQuery = getCanceledQuery(canceled);
      if (filter.activityId() == null && filter.completed() && filter.canceled()) {
        // we request all finished instances
      } else {
        finishedQuery = and(finishedQuery, or(completedQuery, canceledQuery));
      }
    }

    final var processInstanceQuery = or(runningQuery, finishedQuery);
    if (processInstanceQuery == null) {
      return matchNone();
    }

    return processInstanceQuery;
  }

  private SearchQuery getActiveQuery(final boolean active) {
    if (active) {
      return term("incident", false);
    }
    return null;
  }

  private SearchQuery getIncidentsQuery(final boolean incidents) {
    if (incidents) {
      return term("incident", true);
    }
    return null;
  }

  private SearchQuery getCompletedQuery(final boolean completed) {
    if (completed) {
      return term("state", "COMPLETED");
    }
    return null;
  }

  private SearchQuery getCanceledQuery(final boolean canceled) {
    if (canceled) {
      return term("state", "CANCELED");
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

  private SearchQuery getErrorMessageQuery(final String errorMessage) {
    if (StringUtils.isEmpty(errorMessage)) {
      return null;
    }

    if (errorMessage.contains(WILD_CARD)) {
      return getErrorMessageAsWildcardQuery(errorMessage.toLowerCase());
    } else {
      return getErrorMessageAsAndMatchQuery(errorMessage);
    }
  }

  private SearchQuery getErrorMessageAsWildcardQuery(final String errorMessage) {
    return hasChildQuery("activity", wildcardQuery("errorMessage", errorMessage));
  }

  private SearchQuery getErrorMessageAsAndMatchQuery(final String errorMessage) {
    return hasChildQuery(
        "activity",
        match("errorMessage", errorMessage, SearchMatchQuery.SearchMatchQueryOperator.AND));
  }

  private SearchQuery getActivityIdQuery(final ProcessInstanceFilter filter) {
    final String activityId = filter.activityId();
    if (StringUtils.isEmpty(activityId)) {
      return null;
    }

    SearchQuery activeActivityIdQuery = null;
    if (filter.active()) {
      activeActivityIdQuery = createActivityIdQuery(activityId, "ACTIVE");
    }
    SearchQuery incidentActivityIdQuery = null;
    if (filter.incidents()) {
      incidentActivityIdQuery = createActivityIdIncidentQuery(activityId);
    }
    SearchQuery completedActivityIdQuery = null;
    if (filter.completed()) {
      completedActivityIdQuery = createActivityIdQuery(activityId, "COMPLETED");
    }
    SearchQuery canceledActivityIdQuery = null;
    if (filter.canceled()) {
      canceledActivityIdQuery = createActivityIdQuery(activityId, "TERMINATED");
    }
    return or(
        activeActivityIdQuery,
        incidentActivityIdQuery,
        completedActivityIdQuery,
        canceledActivityIdQuery);
  }

  private SearchQuery createActivityIdQuery(String activityId, String flowNodeState) {
    final SearchQuery activitiesQuery = term("activityState", flowNodeState);
    final SearchQuery activityIdQuery = term("activityId", activityId);
    SearchQuery activityIsEndNodeQuery = null;
    if (Objects.equals(flowNodeState, "COMPLETED")) {
      activityIsEndNodeQuery = term("activityType", "END_EVENT");
    }

    return hasChildQuery("activity", and(activitiesQuery, activityIdQuery, activityIsEndNodeQuery));
  }

  private SearchQuery createActivityIdIncidentQuery(String activityId) {
    final SearchQuery activitiesQuery = term("activityState", "ACTIVE");
    final SearchQuery activityIdQuery = term("activityId", activityId);
    final SearchQuery incidentExists = exists("errorMessage");

    return hasChildQuery("activity", and(activitiesQuery, activityIdQuery, incidentExists));
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

  private SearchQuery getBpmnProcessIdsQuery(final List<String> bpmnProcessIds) {
    return stringTerms("bpmnProcessId", bpmnProcessIds);
  }

  private SearchQuery getProcessDefinitionVersionsQuery(
      final List<Integer> processDefinitionVersions) {
    return intTerms("processVersion", processDefinitionVersions);
  }

  private SearchQuery getProcessInstanceVariableQuery(
      final ProcessInstanceVariableFilter variable) {
    if (variable != null) {
      final var query =
          and(term("varName", variable.name()), stringTerms("varValue", variable.values()));
      return hasChildQuery("variable", query);
    }
    return null;
  }

  private SearchQuery getBatchOperationIdsQuery(final List<String> batchOperationIds) {
    return stringTerms("batchOperationIds", batchOperationIds);
  }

  private SearchQuery getParentProcessInstanceKeysQuery(
      final List<Long> parentProcessInstanceKeys) {
    return longTerms("parentProcessInstanceKey", parentProcessInstanceKeys);
  }

  private SearchQuery getTenantIdsQuery(final List<String> tenantIds) {
    return stringTerms("tenantId", tenantIds);
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

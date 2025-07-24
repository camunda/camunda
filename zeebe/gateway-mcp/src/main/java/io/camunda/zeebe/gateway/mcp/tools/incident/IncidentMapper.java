/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tools.incident;

import static io.camunda.search.query.SearchQueryBuilders.incidentSearchQuery;

import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.IncidentQuery;

public class IncidentMapper {

  public static IncidentQuery buildIncidentQuery(final IncidentSearchRequest request) {
    if (request == null) {
      return incidentSearchQuery().build();
    }

    final var queryBuilder = incidentSearchQuery();

    // Apply filter if present
    if (request.filter() != null) {
      queryBuilder.filter(buildIncidentFilter(request.filter()));
    }

    // Apply sort if present
    if (request.sort() != null && !request.sort().isEmpty()) {
      queryBuilder.sort(buildIncidentSort(request.sort()));
    }

    // Apply pagination if present
    if (request.page() != null) {
      queryBuilder.page(buildSearchQueryPage(request.page()));
    }

    return queryBuilder.build();
  }

  private static io.camunda.search.filter.IncidentFilter buildIncidentFilter(
      final IncidentSearchRequest.IncidentFilter filter) {
    final var filterBuilder = FilterBuilders.incident();

    if (filter.processInstanceKeys() != null && !filter.processInstanceKeys().isEmpty()) {
      filterBuilder.processInstanceKeys(
          filter.processInstanceKeys().getFirst(),
          filter
              .processInstanceKeys()
              .subList(1, filter.processInstanceKeys().size())
              .toArray(new Long[0]));
    }

    if (filter.processDefinitionKeys() != null && !filter.processDefinitionKeys().isEmpty()) {
      filterBuilder.processDefinitionKeys(
          filter.processDefinitionKeys().getFirst(),
          filter
              .processDefinitionKeys()
              .subList(1, filter.processDefinitionKeys().size())
              .toArray(new Long[0]));
    }

    if (filter.processDefinitionIds() != null && !filter.processDefinitionIds().isEmpty()) {
      filterBuilder.processDefinitionIds(
          filter.processDefinitionIds().getFirst(),
          filter
              .processDefinitionIds()
              .subList(1, filter.processDefinitionIds().size())
              .toArray(new String[0]));
    }

    if (filter.incidentKeys() != null && !filter.incidentKeys().isEmpty()) {
      filterBuilder.incidentKeys(
          filter.incidentKeys().getFirst(),
          filter.incidentKeys().subList(1, filter.incidentKeys().size()).toArray(new Long[0]));
    }

    if (filter.states() != null && !filter.states().isEmpty()) {
      filterBuilder.states(
          filter.states().getFirst(),
          filter.states().subList(1, filter.states().size()).toArray(new String[0]));
    }

    if (filter.errorTypes() != null && !filter.errorTypes().isEmpty()) {
      filterBuilder.errorTypes(
          filter.errorTypes().getFirst(),
          filter.errorTypes().subList(1, filter.errorTypes().size()).toArray(new String[0]));
    }

    if (filter.errorMessages() != null && !filter.errorMessages().isEmpty()) {
      filterBuilder.errorMessages(
          filter.errorMessages().getFirst(),
          filter.errorMessages().subList(1, filter.errorMessages().size()).toArray(new String[0]));
    }

    if (filter.flowNodeIds() != null && !filter.flowNodeIds().isEmpty()) {
      filterBuilder.flowNodeIds(
          filter.flowNodeIds().getFirst(),
          filter.flowNodeIds().subList(1, filter.flowNodeIds().size()).toArray(new String[0]));
    }

    if (filter.flowNodeInstanceKeys() != null && !filter.flowNodeInstanceKeys().isEmpty()) {
      filterBuilder.flowNodeInstanceKeys(
          filter.flowNodeInstanceKeys().getFirst(),
          filter
              .flowNodeInstanceKeys()
              .subList(1, filter.flowNodeInstanceKeys().size())
              .toArray(new Long[0]));
    }

    if (filter.creationTimeFrom() != null) {
      filterBuilder.creationTimeOperations(
          io.camunda.search.filter.Operation.gte(filter.creationTimeFrom()));
    }

    if (filter.creationTimeTo() != null) {
      filterBuilder.creationTimeOperations(
          io.camunda.search.filter.Operation.lte(filter.creationTimeTo()));
    }

    if (filter.jobKeys() != null && !filter.jobKeys().isEmpty()) {
      filterBuilder.jobKeys(
          filter.jobKeys().getFirst(),
          filter.jobKeys().subList(1, filter.jobKeys().size()).toArray(new Long[0]));
    }

    if (filter.tenantIds() != null && !filter.tenantIds().isEmpty()) {
      filterBuilder.tenantIds(
          filter.tenantIds().getFirst(),
          filter.tenantIds().subList(1, filter.tenantIds().size()).toArray(new String[0]));
    }

    return filterBuilder.build();
  }

  private static io.camunda.search.sort.IncidentSort buildIncidentSort(
      final java.util.List<IncidentSearchRequest.IncidentSort> sorts) {
    final var sortBuilder = io.camunda.search.sort.SortOptionBuilders.incident();

    for (final var sort : sorts) {
      if (sort.field() != null) {
        final boolean isDesc = "desc".equalsIgnoreCase(sort.order());

        switch (sort.field().toLowerCase()) {
          case "key":
            if (isDesc) {
              sortBuilder.incidentKey().desc();
            } else {
              sortBuilder.incidentKey().asc();
            }
            break;
          case "creationtime":
            if (isDesc) {
              sortBuilder.creationTime().desc();
            } else {
              sortBuilder.creationTime().asc();
            }
            break;
          case "state":
            if (isDesc) {
              sortBuilder.state().desc();
            } else {
              sortBuilder.state().asc();
            }
            break;
          case "errortype":
            if (isDesc) {
              sortBuilder.errorType().desc();
            } else {
              sortBuilder.errorType().asc();
            }
            break;
          case "processinstancekey":
            if (isDesc) {
              sortBuilder.processInstanceKey().desc();
            } else {
              sortBuilder.processInstanceKey().asc();
            }
            break;
          case "processdefinitionkey":
            if (isDesc) {
              sortBuilder.processDefinitionKey().desc();
            } else {
              sortBuilder.processDefinitionKey().asc();
            }
            break;
          default:
            // Default to sorting by key if field is not recognized
            if (isDesc) {
              sortBuilder.incidentKey().desc();
            } else {
              sortBuilder.incidentKey().asc();
            }
            break;
        }
      }
    }

    return sortBuilder.build();
  }

  private static SearchQueryPage buildSearchQueryPage(
      final IncidentSearchRequest.IncidentPage page) {
    return SearchQueryPage.of(
        p -> {
          if (page.size() != null && page.size() > 0) {
            p.size(Math.min(page.size(), 100)); // Cap at 100
          }
          // Note: searchAfter functionality may need to be implemented differently
          // based on the actual SearchQueryPage builder capabilities
          return p;
        });
  }

  public static Incident toIncident(final IncidentEntity entity) {
    return new Incident(
        entity.incidentKey(),
        entity.processInstanceKey(),
        entity.processDefinitionKey(),
        entity.errorType() != null ? entity.errorType().name() : null,
        entity.state() != null ? entity.state().name() : null,
        entity.errorMessage(),
        entity.errorType() != null ? entity.errorType().name() : null,
        entity.flowNodeId(),
        entity.flowNodeInstanceKey(),
        entity.creationTime() != null ? entity.creationTime().toString() : null,
        entity.jobKey(),
        entity.tenantId());
  }
}

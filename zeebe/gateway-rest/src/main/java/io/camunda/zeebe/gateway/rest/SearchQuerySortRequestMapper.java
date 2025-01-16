/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.gateway.protocol.rest.*;
import java.util.List;

public class SearchQuerySortRequestMapper {

  public static List<SearchQuerySortRequest> fromProcessDefinitionSearchQuerySortRequest(
      final List<ProcessDefinitionSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromProcessInstanceSearchQuerySortRequest(
      final List<ProcessInstanceSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromRoleSearchQuerySortRequest(
      final List<RoleSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromGroupSearchQuerySortRequest(
      final List<GroupSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromTenantSearchQuerySortRequest(
      final List<TenantSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromMappingSearchQuerySortRequest(
      final List<MappingSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromDecisionDefinitionSearchQuerySortRequest(
      final List<DecisionDefinitionSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromDecisionRequirementsSearchQuerySortRequest(
      final List<DecisionRequirementsSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromFlowNodeInstanceSearchQuerySortRequest(
      final List<FlowNodeInstanceSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromDecisionInstanceSearchQuerySortRequest(
      final List<DecisionInstanceSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromUserTaskSearchQuerySortRequest(
      final List<UserTaskSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromUserTaskVariableSearchQuerySortRequest(
      final List<UserTaskVariableSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromVariableSearchQuerySortRequest(
      final List<VariableSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromUserSearchQuerySortRequest(
      final List<UserSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromIncidentSearchQuerySortRequest(
      final List<IncidentSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromAuthorizationSearchQuerySortRequest(
      final List<AuthorizationSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  private static SearchQuerySortRequest createFrom(final Object field, final SortOrderEnum order) {
    return new SearchQuerySortRequest((field == null) ? null : field.toString(), order);
  }
}

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
      List<ProcessDefinitionSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromProcessInstanceSearchQuerySortRequest(
      List<ProcessInstanceSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromRoleSearchQuerySortRequest(
      List<RoleSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromGroupSearchQuerySortRequest(
      List<GroupSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromTenantSearchQuerySortRequest(
      List<TenantSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromMappingSearchQuerySortRequest(
      List<MappingSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromDecisionDefinitionSearchQuerySortRequest(
      List<DecisionDefinitionSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromDecisionRequirementsSearchQuerySortRequest(
      List<DecisionRequirementsSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromFlowNodeInstanceSearchQuerySortRequest(
      List<FlowNodeInstanceSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromDecisionInstanceSearchQuerySortRequest(
      List<DecisionInstanceSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromUserTaskSearchQuerySortRequest(
      List<UserTaskSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromUserTaskVariableSearchQuerySortRequest(
      List<UserTaskVariableSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromVariableSearchQuerySortRequest(
      List<VariableSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromUserSearchQuerySortRequest(
      List<UserSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromIncidentSearchQuerySortRequest(
      List<IncidentSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest> fromAuthorizationSearchQuerySortRequest(
      List<AuthorizationSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  private static SearchQuerySortRequest createFrom(Object field, SortOrderEnum order) {
    final SearchQuerySortRequest request = new SearchQuerySortRequest();
    request.setField((field == null) ? null : field.toString());
    request.setOrder(order);
    return request;
  }
}

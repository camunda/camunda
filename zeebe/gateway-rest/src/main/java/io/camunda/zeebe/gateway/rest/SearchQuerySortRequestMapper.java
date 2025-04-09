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

  public static List<SearchQuerySortRequest<ProcessDefinitionSearchQuerySortRequest.FieldEnum>>
      fromProcessDefinitionSearchQuerySortRequest(
          final List<ProcessDefinitionSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<ProcessInstanceSearchQuerySortRequest.FieldEnum>>
      fromProcessInstanceSearchQuerySortRequest(
          final List<ProcessInstanceSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<RoleSearchQuerySortRequest.FieldEnum>>
      fromRoleSearchQuerySortRequest(final List<RoleSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<GroupSearchQuerySortRequest.FieldEnum>>
      fromGroupSearchQuerySortRequest(final List<GroupSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<TenantSearchQuerySortRequest.FieldEnum>>
      fromTenantSearchQuerySortRequest(final List<TenantSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<MappingSearchQuerySortRequest.FieldEnum>>
      fromMappingSearchQuerySortRequest(final List<MappingSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<DecisionDefinitionSearchQuerySortRequest.FieldEnum>>
      fromDecisionDefinitionSearchQuerySortRequest(
          final List<DecisionDefinitionSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<DecisionRequirementsSearchQuerySortRequest.FieldEnum>>
      fromDecisionRequirementsSearchQuerySortRequest(
          final List<DecisionRequirementsSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<ElementInstanceSearchQuerySortRequest.FieldEnum>>
      fromElementInstanceSearchQuerySortRequest(
          final List<ElementInstanceSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<DecisionInstanceSearchQuerySortRequest.FieldEnum>>
      fromDecisionInstanceSearchQuerySortRequest(
          final List<DecisionInstanceSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<UserTaskSearchQuerySortRequest.FieldEnum>>
      fromUserTaskSearchQuerySortRequest(final List<UserTaskSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<UserTaskVariableSearchQuerySortRequest.FieldEnum>>
      fromUserTaskVariableSearchQuerySortRequest(
          final List<UserTaskVariableSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<VariableSearchQuerySortRequest.FieldEnum>>
      fromVariableSearchQuerySortRequest(final List<VariableSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<UserSearchQuerySortRequest.FieldEnum>>
      fromUserSearchQuerySortRequest(final List<UserSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<IncidentSearchQuerySortRequest.FieldEnum>>
      fromIncidentSearchQuerySortRequest(final List<IncidentSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<AuthorizationSearchQuerySortRequest.FieldEnum>>
      fromAuthorizationSearchQuerySortRequest(
          final List<AuthorizationSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<BatchOperationSearchQuerySortRequest.FieldEnum>>
      fromBatchOperationSearchQuerySortRequest(
          final List<BatchOperationSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  private static <T> SearchQuerySortRequest<T> createFrom(
      final T field, final SortOrderEnum order) {
    return new SearchQuerySortRequest<T>(field, order);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.gateway.protocol.rest.*;
import jakarta.validation.Valid;
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

  public static List<SearchQuerySortRequest<JobSearchQuerySortRequest.FieldEnum>>
      fromJobSearchQuerySortRequest(final List<JobSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<RoleSearchQuerySortRequest.FieldEnum>>
      fromRoleSearchQuerySortRequest(final List<RoleSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<RoleUserSearchQuerySortRequest.FieldEnum>>
      fromRoleUserSearchQuerySortRequest(final List<RoleUserSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<RoleGroupSearchQuerySortRequest.FieldEnum>>
      fromRoleGroupSearchQuerySortRequest(
          final @Valid List<RoleGroupSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<RoleClientSearchQuerySortRequest.FieldEnum>>
      fromRoleClientSearchQuerySortRequest(final List<RoleClientSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<GroupSearchQuerySortRequest.FieldEnum>>
      fromGroupSearchQuerySortRequest(final List<GroupSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<GroupUserSearchQuerySortRequest.FieldEnum>>
      fromGroupUserSearchQuerySortRequest(final List<GroupUserSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<GroupClientSearchQuerySortRequest.FieldEnum>>
      fromGroupClientSearchQuerySortRequest(
          final List<GroupClientSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<TenantSearchQuerySortRequest.FieldEnum>>
      fromTenantSearchQuerySortRequest(final List<TenantSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<TenantUserSearchQuerySortRequest.FieldEnum>>
      fromTenantUserSearchQuerySortRequest(final List<TenantUserSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<TenantGroupSearchQuerySortRequest.FieldEnum>>
      fromTenantGroupSearchQuerySortRequest(
          final List<TenantGroupSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<TenantClientSearchQuerySortRequest.FieldEnum>>
      fromTenantClientSearchQuerySortRequest(
          final List<TenantClientSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<MappingRuleSearchQuerySortRequest.FieldEnum>>
      fromMappingRuleSearchQuerySortRequest(
          final List<MappingRuleSearchQuerySortRequest> requests) {
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

  public static List<SearchQuerySortRequest<BatchOperationItemSearchQuerySortRequest.FieldEnum>>
      fromBatchOperationItemSearchQuerySortRequest(
          final List<BatchOperationItemSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  public static List<SearchQuerySortRequest<MessageSubscriptionSearchQuerySortRequest.FieldEnum>>
      fromMessageSubscriptionSearchQuerySortRequest(
          final List<MessageSubscriptionSearchQuerySortRequest> requests) {
    return requests.stream().map(r -> createFrom(r.getField(), r.getOrder())).toList();
  }

  private static <T> SearchQuerySortRequest<T> createFrom(
      final T field, final SortOrderEnum order) {
    return new SearchQuerySortRequest<T>(field, order);
  }
}

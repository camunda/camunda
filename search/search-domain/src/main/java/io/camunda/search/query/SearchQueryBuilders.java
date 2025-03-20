/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class SearchQueryBuilders {

  private SearchQueryBuilders() {}

  public static UsageMetricsQuery.Builder usageMetricsSearchQuery() {
    return new UsageMetricsQuery.Builder();
  }

  public static UsageMetricsQuery usageMetricsSearchQuery(
      final Function<UsageMetricsQuery.Builder, ObjectBuilder<UsageMetricsQuery>> fn) {
    return fn.apply(usageMetricsSearchQuery()).build();
  }

  public static ProcessDefinitionQuery.Builder processDefinitionSearchQuery() {
    return new ProcessDefinitionQuery.Builder();
  }

  public static ProcessDefinitionQuery processDefinitionSearchQuery(
      final Function<ProcessDefinitionQuery.Builder, ObjectBuilder<ProcessDefinitionQuery>> fn) {
    return fn.apply(processDefinitionSearchQuery()).build();
  }

  public static ProcessInstanceQuery.Builder processInstanceSearchQuery() {
    return new ProcessInstanceQuery.Builder();
  }

  public static ProcessInstanceQuery processInstanceSearchQuery(
      final Function<ProcessInstanceQuery.Builder, ObjectBuilder<ProcessInstanceQuery>> fn) {
    return fn.apply(processInstanceSearchQuery()).build();
  }

  public static UserTaskQuery.Builder userTaskSearchQuery() {
    return new UserTaskQuery.Builder();
  }

  public static UserTaskQuery userTaskSearchQuery(
      final Function<UserTaskQuery.Builder, ObjectBuilder<UserTaskQuery>> fn) {
    return fn.apply(userTaskSearchQuery()).build();
  }

  public static FormQuery.Builder formSearchQuery() {
    return new FormQuery.Builder();
  }

  public static FormQuery formSearchQuery(
      final Function<FormQuery.Builder, ObjectBuilder<FormQuery>> fn) {
    return fn.apply(formSearchQuery()).build();
  }

  public static VariableQuery.Builder variableSearchQuery() {
    return new VariableQuery.Builder();
  }

  public static VariableQuery variableSearchQuery(
      final Function<VariableQuery.Builder, ObjectBuilder<VariableQuery>> fn) {
    return fn.apply(variableSearchQuery()).build();
  }

  public static DecisionDefinitionQuery.Builder decisionDefinitionSearchQuery() {
    return new DecisionDefinitionQuery.Builder();
  }

  public static DecisionDefinitionQuery decisionDefinitionSearchQuery(
      final Function<DecisionDefinitionQuery.Builder, ObjectBuilder<DecisionDefinitionQuery>> fn) {
    return fn.apply(decisionDefinitionSearchQuery()).build();
  }

  public static DecisionRequirementsQuery.Builder decisionRequirementsSearchQuery() {
    return new DecisionRequirementsQuery.Builder();
  }

  public static DecisionRequirementsQuery decisionRequirementsSearchQuery(
      final Function<DecisionRequirementsQuery.Builder, ObjectBuilder<DecisionRequirementsQuery>>
          fn) {
    return fn.apply(decisionRequirementsSearchQuery()).build();
  }

  public static DecisionInstanceQuery.Builder decisionInstanceSearchQuery() {
    return new DecisionInstanceQuery.Builder();
  }

  public static DecisionInstanceQuery decisionInstanceSearchQuery(
      final Function<DecisionInstanceQuery.Builder, ObjectBuilder<DecisionInstanceQuery>> fn) {
    return fn.apply(decisionInstanceSearchQuery()).build();
  }

  public static FlowNodeInstanceQuery.Builder flownodeInstanceSearchQuery() {
    return new FlowNodeInstanceQuery.Builder();
  }

  public static FlowNodeInstanceQuery flownodeInstanceSearchQuery(
      final Function<FlowNodeInstanceQuery.Builder, ObjectBuilder<FlowNodeInstanceQuery>> fn) {
    return fn.apply(flownodeInstanceSearchQuery()).build();
  }

  public static UserQuery.Builder userSearchQuery() {
    return new UserQuery.Builder();
  }

  public static UserQuery userSearchQuery(
      final Function<UserQuery.Builder, ObjectBuilder<UserQuery>> fn) {
    return fn.apply(userSearchQuery()).build();
  }

  public static MappingQuery.Builder mappingSearchQuery() {
    return new MappingQuery.Builder();
  }

  public static MappingQuery mappingSearchQuery(
      final Function<MappingQuery.Builder, ObjectBuilder<MappingQuery>> fn) {
    return fn.apply(mappingSearchQuery()).build();
  }

  public static RoleQuery.Builder roleSearchQuery() {
    return new RoleQuery.Builder();
  }

  public static RoleQuery roleSearchQuery(
      final Function<RoleQuery.Builder, ObjectBuilder<RoleQuery>> fn) {
    return fn.apply(roleSearchQuery()).build();
  }

  public static TenantQuery.Builder tenantSearchQuery() {
    return new TenantQuery.Builder();
  }

  public static TenantQuery tenantSearchQuery(
      final Function<TenantQuery.Builder, ObjectBuilder<TenantQuery>> fn) {
    return fn.apply(tenantSearchQuery()).build();
  }

  public static GroupQuery.Builder groupSearchQuery() {
    return new GroupQuery.Builder();
  }

  public static GroupQuery groupSearchQuery(
      final Function<GroupQuery.Builder, ObjectBuilder<GroupQuery>> fn) {
    return fn.apply(groupSearchQuery()).build();
  }

  public static AuthorizationQuery.Builder authorizationSearchQuery() {
    return new AuthorizationQuery.Builder();
  }

  public static AuthorizationQuery authorizationSearchQuery(
      final Function<AuthorizationQuery.Builder, ObjectBuilder<AuthorizationQuery>> fn) {
    return fn.apply(authorizationSearchQuery()).build();
  }

  public static IncidentQuery.Builder incidentSearchQuery() {
    return new IncidentQuery.Builder();
  }

  public static IncidentQuery incidentSearchQuery(
      final Function<IncidentQuery.Builder, ObjectBuilder<IncidentQuery>> fn) {
    return fn.apply(incidentSearchQuery()).build();
  }

  public static BatchOperationQuery.Builder batchOperationQuery() {
    return new BatchOperationQuery.Builder();
  }

  public static BatchOperationQuery batchOperationQuery(
      final Function<BatchOperationQuery.Builder, ObjectBuilder<BatchOperationQuery>> fn) {
    return fn.apply(batchOperationQuery()).build();
  }
}

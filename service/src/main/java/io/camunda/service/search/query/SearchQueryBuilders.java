/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.query;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class SearchQueryBuilders {

  private SearchQueryBuilders() {}

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

  public static UserQuery.Builder userSearchQuery() {
    return new UserQuery.Builder();
  }

  public static UserQuery userSearchQuery(
      final Function<UserQuery.Builder, ObjectBuilder<UserQuery>> fn) {
    return fn.apply(userSearchQuery()).build();
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
}

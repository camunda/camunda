/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.sort;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class SortOptionBuilders {

  private SortOptionBuilders() {}

  public static ProcessInstanceSort.Builder processInstance() {
    return new ProcessInstanceSort.Builder();
  }

  public static UserTaskSort.Builder userTask() {
    return new UserTaskSort.Builder();
  }

  public static VariableSort.Builder variable() {
    return new VariableSort.Builder();
  }

  public static DecisionDefinitionSort.Builder decisionDefinition() {
    return new DecisionDefinitionSort.Builder();
  }

  public static DecisionRequirementsSort.Builder decisionRequirements() {
    return new DecisionRequirementsSort.Builder();
  }

  public static UserSort.Builder user() {
    return new UserSort.Builder();
  }

  public static AuthorizationSort.Builder authorization() {
    return new AuthorizationSort.Builder();
  }

  public static ProcessInstanceSort processInstance(
      final Function<ProcessInstanceSort.Builder, ObjectBuilder<ProcessInstanceSort>> fn) {
    return fn.apply(processInstance()).build();
  }

  public static UserTaskSort userTask(
      final Function<UserTaskSort.Builder, ObjectBuilder<UserTaskSort>> fn) {
    return fn.apply(userTask()).build();
  }

  public static VariableSort variable(
      final Function<VariableSort.Builder, ObjectBuilder<VariableSort>> fn) {
    return fn.apply(variable()).build();
  }

  public static DecisionDefinitionSort decisionDefinition(
      final Function<DecisionDefinitionSort.Builder, ObjectBuilder<DecisionDefinitionSort>> fn) {
    return fn.apply(decisionDefinition()).build();
  }

  public static DecisionRequirementsSort decisionRequirements(
      final Function<DecisionRequirementsSort.Builder, ObjectBuilder<DecisionRequirementsSort>>
          fn) {
    return fn.apply(decisionRequirements()).build();
  }

  public static UserSort user(final Function<UserSort.Builder, ObjectBuilder<UserSort>> fn) {
    return fn.apply(user()).build();
  }

  public static IncidentSort.Builder incident() {
    return new IncidentSort.Builder();
  }

  public static IncidentSort incident(
      final Function<IncidentSort.Builder, ObjectBuilder<IncidentSort>> fn) {
    return fn.apply(incident()).build();
  }

  public static AuthorizationSort authorization(
      final Function<AuthorizationSort.Builder, ObjectBuilder<AuthorizationSort>> fn) {
    return fn.apply(authorization()).build();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class FilterBuilders {

  private FilterBuilders() {}

  public static ProcessInstanceFilter.Builder processInstance() {
    return new ProcessInstanceFilter.Builder();
  }

  public static ProcessInstanceVariableFilter.Builder processInstanceVariable() {
    return new ProcessInstanceVariableFilter.Builder();
  }

  public static UserTaskFilter.Builder userTask() {
    return new UserTaskFilter.Builder();
  }

  public static DecisionDefinitionFilter.Builder decisionDefinition() {
    return new DecisionDefinitionFilter.Builder();
  }

  public static DecisionRequirementsFilter.Builder decisionRequirements() {
    return new DecisionRequirementsFilter.Builder();
  }

  public static UserFilter.Builder user() {
    return new UserFilter.Builder();
  }

  public static AuthorizationFilter.Builder authorization() {
    return new AuthorizationFilter.Builder();
  }

  public static ProcessInstanceFilter processInstance(
      final Function<ProcessInstanceFilter.Builder, ObjectBuilder<ProcessInstanceFilter>> fn) {
    return fn.apply(processInstance()).build();
  }

  public static ProcessInstanceVariableFilter processInstanceVariable(
      final Function<
              ProcessInstanceVariableFilter.Builder, ObjectBuilder<ProcessInstanceVariableFilter>>
          fn) {
    return fn.apply(processInstanceVariable()).build();
  }

  public static UserTaskFilter userTask(
      final Function<UserTaskFilter.Builder, ObjectBuilder<UserTaskFilter>> fn) {
    return fn.apply(userTask()).build();
  }

  public static DecisionDefinitionFilter decisionDefinition(
      final Function<DecisionDefinitionFilter.Builder, ObjectBuilder<DecisionDefinitionFilter>>
          fn) {
    return fn.apply(decisionDefinition()).build();
  }

  public static DecisionRequirementsFilter decisionRequirements(
      final Function<DecisionRequirementsFilter.Builder, ObjectBuilder<DecisionRequirementsFilter>>
          fn) {
    return fn.apply(decisionRequirements()).build();
  }

  public static UserFilter user(final Function<UserFilter.Builder, ObjectBuilder<UserFilter>> fn) {
    return fn.apply(new UserFilter.Builder()).build();
  }

  public static AuthorizationFilter authorization(
      final Function<AuthorizationFilter.Builder, ObjectBuilder<AuthorizationFilter>> fn) {
    return fn.apply(new AuthorizationFilter.Builder()).build();
  }

  public static VariableFilter.Builder variable() {
    return new VariableFilter.Builder();
  }

  public static VariableFilter variable(
      final Function<VariableFilter.Builder, ObjectBuilder<VariableFilter>> fn) {
    return fn.apply(variable()).build();
  }

  public static VariableValueFilter.Builder variableValue() {
    return new VariableValueFilter.Builder();
  }

  public static VariableValueFilter variableValue(
      final Function<VariableValueFilter.Builder, ObjectBuilder<VariableValueFilter>> fn) {
    return fn.apply(variableValue()).build();
  }

  public static DateValueFilter.Builder dateValue() {
    return new DateValueFilter.Builder();
  }

  public static DateValueFilter dateValue(
      final Function<DateValueFilter.Builder, ObjectBuilder<DateValueFilter>> fn) {
    return fn.apply(dateValue()).build();
  }

  public static ComparableValueFilter.Builder comparableValue() {
    return new ComparableValueFilter.Builder();
  }

  public static ComparableValueFilter comparableValue(
      final Function<ComparableValueFilter.Builder, ObjectBuilder<ComparableValueFilter>> fn) {
    return fn.apply(comparableValue()).build();
  }

  public static IncidentFilter.Builder incident() {
    return new IncidentFilter.Builder();
  }

  public static IncidentFilter incident(
      final Function<IncidentFilter.Builder, ObjectBuilder<IncidentFilter>> fn) {
    return fn.apply(incident()).build();
  }
}

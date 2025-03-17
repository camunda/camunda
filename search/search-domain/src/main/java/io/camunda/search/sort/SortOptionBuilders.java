/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.sort;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class SortOptionBuilders {

  private SortOptionBuilders() {}

  public static UsageMetricsSort.Builder usageMetrics() {
    return new UsageMetricsSort.Builder();
  }

  public static UsageMetricsSort usageMetrics(
      final Function<UsageMetricsSort.Builder, ObjectBuilder<UsageMetricsSort>> fn) {
    return fn.apply(usageMetrics()).build();
  }

  public static ProcessDefinitionSort.Builder processDefinition() {
    return new ProcessDefinitionSort.Builder();
  }

  public static ProcessDefinitionSort processDefinition(
      final Function<ProcessDefinitionSort.Builder, ObjectBuilder<ProcessDefinitionSort>> fn) {
    return fn.apply(processDefinition()).build();
  }

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

  public static DecisionInstanceSort.Builder decisionInstance() {
    return new DecisionInstanceSort.Builder();
  }

  public static FlowNodeInstanceSort.Builder flowNodeInstance() {
    return new FlowNodeInstanceSort.Builder();
  }

  public static FlowNodeInstanceSort flowNodeInstance(
      final Function<FlowNodeInstanceSort.Builder, ObjectBuilder<FlowNodeInstanceSort>> fn) {
    return fn.apply(flowNodeInstance()).build();
  }

  public static UserSort.Builder user() {
    return new UserSort.Builder();
  }

  public static MappingSort.Builder mapping() {
    return new MappingSort.Builder();
  }

  public static RoleSort.Builder role() {
    return new RoleSort.Builder();
  }

  public static TenantSort.Builder tenant() {
    return new TenantSort.Builder();
  }

  public static GroupSort.Builder group() {
    return new GroupSort.Builder();
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

  public static DecisionInstanceSort decisionInstance(
      final Function<DecisionInstanceSort.Builder, ObjectBuilder<DecisionInstanceSort>> fn) {
    return fn.apply(decisionInstance()).build();
  }

  public static UserSort user(final Function<UserSort.Builder, ObjectBuilder<UserSort>> fn) {
    return fn.apply(user()).build();
  }

  public static MappingSort mapping(
      final Function<MappingSort.Builder, ObjectBuilder<MappingSort>> fn) {
    return fn.apply(mapping()).build();
  }

  public static RoleSort role(final Function<RoleSort.Builder, ObjectBuilder<RoleSort>> fn) {
    return fn.apply(role()).build();
  }

  public static TenantSort tenant(
      final Function<TenantSort.Builder, ObjectBuilder<TenantSort>> fn) {
    return fn.apply(tenant()).build();
  }

  public static GroupSort group(final Function<GroupSort.Builder, ObjectBuilder<GroupSort>> fn) {
    return fn.apply(group()).build();
  }

  public static FormSort.Builder form() {
    return new FormSort.Builder();
  }

  public static FormSort form(final Function<FormSort.Builder, ObjectBuilder<FormSort>> fn) {
    return fn.apply(form()).build();
  }

  public static IncidentSort.Builder incident() {
    return new IncidentSort.Builder();
  }

  public static IncidentSort incident(
      final Function<IncidentSort.Builder, ObjectBuilder<IncidentSort>> fn) {
    return fn.apply(incident()).build();
  }

  public static BatchOperationSort.Builder batchOperation() {
    return new BatchOperationSort.Builder();
  }

  public static BatchOperationSort batchOperation(
      final Function<BatchOperationSort.Builder, ObjectBuilder<BatchOperationSort>> fn) {
    return fn.apply(batchOperation()).build();
  }

  public static AuthorizationSort authorization(
      final Function<AuthorizationSort.Builder, ObjectBuilder<AuthorizationSort>> fn) {
    return fn.apply(authorization()).build();
  }
}

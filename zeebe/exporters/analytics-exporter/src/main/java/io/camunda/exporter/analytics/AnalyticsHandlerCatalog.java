/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.camunda.exporter.analytics.handler.AgentInstanceCompletedHandler;
import io.camunda.exporter.analytics.handler.AgentInstanceCreatedHandler;
import io.camunda.exporter.analytics.handler.DecisionDefinitionCreatedHandler;
import io.camunda.exporter.analytics.handler.DecisionDefinitionDeletedHandler;
import io.camunda.exporter.analytics.handler.DecisionInstanceEvaluatedHandler;
import io.camunda.exporter.analytics.handler.FormDefinitionCreatedHandler;
import io.camunda.exporter.analytics.handler.FormDefinitionDeletedHandler;
import io.camunda.exporter.analytics.handler.IncidentCreatedHandler;
import io.camunda.exporter.analytics.handler.IncidentResolvedHandler;
import io.camunda.exporter.analytics.handler.ProcessDefinitionCreatedHandler;
import io.camunda.exporter.analytics.handler.ProcessDefinitionDeletedHandler;
import io.camunda.exporter.analytics.handler.ProcessInstanceElementActivatedHandler;
import io.camunda.exporter.analytics.handler.TenantCreatedHandler;
import io.camunda.exporter.analytics.handler.TenantDeletedHandler;
import io.camunda.exporter.analytics.handler.UserTaskAssignedHandler;
import io.camunda.exporter.analytics.handler.UserTaskCreatedHandler;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.Set;

/**
 * Registers all analytics event handlers. This is the only file to edit when adding a new event:
 * create a handler class in {@code handler/}, add one {@code .register()} call here, and add the
 * event name constant to {@link AnalyticsAttributes.Event}.
 *
 * <p>{@link AnalyticsExporter} never needs to change when new events are added.
 */
final class AnalyticsHandlerCatalog {

  private AnalyticsHandlerCatalog() {}

  static HandlerRegistry build(
      final OtelSdkManager otelSdkManager, final Set<AnalyticsCategory> activeCategories) {
    return new HandlerRegistry(activeCategories)
        .register(
            ValueType.PROCESS_INSTANCE,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            new ProcessInstanceElementActivatedHandler(otelSdkManager))
        .register(
            ValueType.USER_TASK, UserTaskIntent.CREATED, new UserTaskCreatedHandler(otelSdkManager))
        .register(
            ValueType.USER_TASK,
            UserTaskIntent.ASSIGNED,
            new UserTaskAssignedHandler(otelSdkManager))
        .register(ValueType.TENANT, TenantIntent.CREATED, new TenantCreatedHandler(otelSdkManager))
        .register(ValueType.TENANT, TenantIntent.DELETED, new TenantDeletedHandler(otelSdkManager))
        .register(
            ValueType.INCIDENT, IncidentIntent.CREATED, new IncidentCreatedHandler(otelSdkManager))
        .register(
            ValueType.INCIDENT,
            IncidentIntent.RESOLVED,
            new IncidentResolvedHandler(otelSdkManager))
        .register(
            ValueType.PROCESS,
            ProcessIntent.CREATED,
            new ProcessDefinitionCreatedHandler(otelSdkManager))
        .register(
            ValueType.PROCESS,
            ProcessIntent.DELETED,
            new ProcessDefinitionDeletedHandler(otelSdkManager))
        .register(
            ValueType.DECISION,
            DecisionIntent.CREATED,
            new DecisionDefinitionCreatedHandler(otelSdkManager))
        .register(
            ValueType.DECISION,
            DecisionIntent.DELETED,
            new DecisionDefinitionDeletedHandler(otelSdkManager))
        .register(
            ValueType.DECISION_EVALUATION,
            DecisionEvaluationIntent.EVALUATED,
            new DecisionInstanceEvaluatedHandler(otelSdkManager))
        .register(
            ValueType.FORM, FormIntent.CREATED, new FormDefinitionCreatedHandler(otelSdkManager))
        .register(
            ValueType.FORM, FormIntent.DELETED, new FormDefinitionDeletedHandler(otelSdkManager))
        .register(
            ValueType.AGENT_INSTANCE,
            AgentInstanceIntent.CREATED,
            new AgentInstanceCreatedHandler(otelSdkManager))
        .register(
            ValueType.AGENT_INSTANCE,
            AgentInstanceIntent.COMPLETED,
            new AgentInstanceCompletedHandler(otelSdkManager));
  }
}

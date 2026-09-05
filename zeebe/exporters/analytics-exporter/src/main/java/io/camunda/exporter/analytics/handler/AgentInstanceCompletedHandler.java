/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import io.camunda.exporter.analytics.AnalyticsAttributes;
import io.camunda.exporter.analytics.AnalyticsCategory;
import io.camunda.exporter.analytics.AnalyticsHandler;
import io.camunda.exporter.analytics.OtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Emits a {@code camunda.agent.instance.completed} OTel event for each completed agent instance.
 * Carries the same attributes as {@code camunda.agent.instance.created}, so agent run duration is a
 * downstream join on the agent instance key. Token counts and tool calls are deliberately not
 * exported.
 */
public final class AgentInstanceCompletedHandler
    implements AnalyticsHandler<AgentInstanceRecordValue> {

  private final OtelSdkManager otelSdkManager;

  public AgentInstanceCompletedHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public AnalyticsCategory category() {
    return AnalyticsCategory.OPTIONAL;
  }

  @Override
  public void handle(final Record<AgentInstanceRecordValue> record) {
    final var value = record.getValue();

    otelSdkManager.logEvent(
        AnalyticsAttributes.Event.AGENT_INSTANCE_COMPLETED,
        record.getPosition(),
        log ->
            log.setAttribute(AnalyticsAttributes.Agent.INSTANCE_KEY, value.getAgentInstanceKey())
                .setAttribute(
                    AnalyticsAttributes.Agent.DEFINITION_KEY, value.getAgentDefinitionKey())
                .setAttribute(AnalyticsAttributes.Agent.STATUS, value.getStatus().name())
                .setAttribute(AnalyticsAttributes.Process.BPMN_PROCESS_ID, value.getBpmnProcessId())
                .setAttribute(
                    AnalyticsAttributes.Process.DEFINITION_KEY, value.getProcessDefinitionKey())
                .setAttribute(
                    AnalyticsAttributes.Process.INSTANCE_KEY, value.getProcessInstanceKey())
                .setAttribute(
                    AnalyticsAttributes.Process.ROOT_INSTANCE_KEY,
                    value.getRootProcessInstanceKey())
                .setAttribute(AnalyticsAttributes.Tenant.ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.BPMN_PROCESS_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.DEFINITION_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.INSTANCE_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.ROOT_INSTANCE_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.VERSION;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Tenant.ID;

import io.camunda.exporter.analytics.AnalyticsAttributes;
import io.camunda.exporter.analytics.AnalyticsCategory;
import io.camunda.exporter.analytics.AnalyticsHandler;
import io.camunda.exporter.analytics.OtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Handles {@code PROCESS_INSTANCE/ELEMENT_ACTIVATED}, emitting {@code
 * camunda.process.instance.activated} for a root process element. All other element types are
 * skipped silently.
 */
public final class ProcessInstanceElementActivatedHandler
    implements AnalyticsHandler<ProcessInstanceRecordValue> {

  /** Sentinel {@code getParentProcessInstanceKey()} returns when no parent instance exists. */
  private static final long NO_PARENT_INSTANCE = -1L;

  private final OtelSdkManager otelSdkManager;

  public ProcessInstanceElementActivatedHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public AnalyticsCategory category() {
    // Successor of the retired ProcessInstanceCreationHandler: sole source of the contractual
    // root-process-instance (RPI) count.
    return AnalyticsCategory.CONTRACTUAL;
  }

  @Override
  public void handle(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    // Counts once per root process instance regardless of start trigger (client API, message,
    // timer, signal, conditional); the parent-key guard excludes call-activity children. This
    // population is assumed equal to the license RPI population, pending engine-team validation.
    if (value.getBpmnElementType() == BpmnElementType.PROCESS
        && value.getParentProcessInstanceKey() == NO_PARENT_INSTANCE) {
      emitProcessInstanceActivated(record, value);
    }
  }

  private void emitProcessInstanceActivated(
      final Record<ProcessInstanceRecordValue> record, final ProcessInstanceRecordValue value) {
    otelSdkManager.logEvent(
        AnalyticsAttributes.Event.PROCESS_INSTANCE_ACTIVATED,
        record.getPosition(),
        log ->
            log.setAttribute(BPMN_PROCESS_ID, value.getBpmnProcessId())
                .setAttribute(VERSION, (long) value.getVersion())
                .setAttribute(DEFINITION_KEY, value.getProcessDefinitionKey())
                .setAttribute(INSTANCE_KEY, value.getProcessInstanceKey())
                .setAttribute(ROOT_INSTANCE_KEY, value.getRootProcessInstanceKey())
                .setAttribute(ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}

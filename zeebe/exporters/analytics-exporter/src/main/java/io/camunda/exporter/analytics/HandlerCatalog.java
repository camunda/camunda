/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.camunda.exporter.analytics.handler.AdHocSubProcessHandler;
import io.camunda.exporter.analytics.handler.ProcessInstanceCreationHandler;
import io.camunda.exporter.analytics.handler.UsageMetricHandler;
import io.camunda.exporter.analytics.handler.UserTaskCreatedHandler;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

/**
 * Registers all analytics event handlers. This is the only file to edit when adding a new event:
 * create a handler class in {@code handler/}, add one {@code .register()} call here, and add the
 * event name constant to {@link AnalyticsAttributes.Event}.
 *
 * <p>{@link AnalyticsExporter} never needs to change when new events are added.
 */
final class HandlerCatalog {

  private HandlerCatalog() {}

  static HandlerRegistry build(final OtelSdkManager otelSdkManager) {
    return new HandlerRegistry()
        .register(
            ValueType.PROCESS_INSTANCE_CREATION,
            ProcessInstanceCreationIntent.CREATED,
            new ProcessInstanceCreationHandler(otelSdkManager))
        .register(
            ValueType.PROCESS_INSTANCE,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            new AdHocSubProcessHandler(otelSdkManager))
        .register(
            ValueType.USAGE_METRIC,
            UsageMetricIntent.EXPORTED,
            new UsageMetricHandler(otelSdkManager))
        .register(
            ValueType.USER_TASK,
            UserTaskIntent.CREATED,
            new UserTaskCreatedHandler(otelSdkManager));
  }
}

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
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Emits a {@code camunda.form.definition.deleted} OTel event for each deleted form. Carries the
 * same attributes as {@code camunda.form.definition.created}.
 */
public final class FormDefinitionDeletedHandler implements AnalyticsHandler<Form> {

  private final OtelSdkManager otelSdkManager;

  public FormDefinitionDeletedHandler(final OtelSdkManager otelSdkManager) {
    this.otelSdkManager = Objects.requireNonNull(otelSdkManager);
  }

  @Override
  public AnalyticsCategory category() {
    return AnalyticsCategory.OPTIONAL;
  }

  @Override
  public void handle(final Record<Form> record) {
    final var value = record.getValue();

    otelSdkManager.logEvent(
        AnalyticsAttributes.Event.FORM_DEFINITION_DELETED,
        record.getPosition(),
        log ->
            log.setAttribute(AnalyticsAttributes.Form.ID, value.getFormId())
                .setAttribute(AnalyticsAttributes.Form.KEY, value.getFormKey())
                .setAttribute(AnalyticsAttributes.Form.VERSION, (long) value.getVersion())
                .setAttribute(AnalyticsAttributes.Tenant.ID, value.getTenantId())
                .setTimestamp(record.getTimestamp(), TimeUnit.MILLISECONDS));
  }
}

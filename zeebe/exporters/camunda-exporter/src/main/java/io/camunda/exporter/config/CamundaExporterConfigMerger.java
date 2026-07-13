/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.config;

import io.camunda.exporter.CamundaExporter;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import io.camunda.zeebe.exporter.support.ExporterConfigMergeSupport;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Enables per-physical-tenant partial overrides of an <em>explicitly declared</em> CamundaExporter
 * catalog entry's {@code args} — the multi-region duplication setup, where a second CamundaExporter
 * is declared under {@code data.exporters} (ADR-0008 §3). The <em>autoconfigured</em> {@code
 * camundaexporter} entry never goes through a merger: its configuration is derived from the
 * tenant's (already per-tenant-resolved) secondary-storage properties. Registered via {@code
 * META-INF/services}, discovered with {@link java.util.ServiceLoader} at configuration-resolution
 * time.
 */
@NullMarked
public final class CamundaExporterConfigMerger implements ExporterConfigMerger {

  @Override
  public boolean supports(final String className) {
    return CamundaExporter.class.getName().equals(className);
  }

  @Override
  public Map<String, Object> merge(
      final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
    return ExporterConfigMergeSupport.merge(ExporterConfiguration.class, rootArgs, tenantArgs);
  }
}

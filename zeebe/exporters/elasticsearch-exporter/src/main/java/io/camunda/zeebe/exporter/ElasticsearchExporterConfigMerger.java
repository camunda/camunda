/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import io.camunda.zeebe.exporter.support.ExporterConfigMergeSupport;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Enables per-physical-tenant partial overrides of a root-declared Elasticsearch exporter's {@code
 * args} (ADR-0008 §3): registered via {@code META-INF/services}, discovered with {@link
 * java.util.ServiceLoader} at configuration-resolution time.
 */
@NullMarked
public final class ElasticsearchExporterConfigMerger implements ExporterConfigMerger {

  @Override
  public boolean supports(final String className) {
    return ElasticsearchExporter.class.getName().equals(className);
  }

  @Override
  public Map<String, Object> merge(
      final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
    return ExporterConfigMergeSupport.merge(
        ElasticsearchExporterConfiguration.class, rootArgs, tenantArgs);
  }
}

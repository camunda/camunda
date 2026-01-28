/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCacheImpl;
import io.camunda.zeebe.exporter.common.cache.decisionRequirements.CachedDecisionRequirementsEntity;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import java.util.Collection;
import java.util.Set;
import java.util.function.BiConsumer;

public class TestExporterResourceProvider implements ExporterResourceProvider {

  private final IndexDescriptors indexDescriptors;

  public TestExporterResourceProvider(final String indexPrefix, final boolean isElasticsearch) {
    indexDescriptors = new IndexDescriptors(indexPrefix, isElasticsearch);
  }

  @Override
  public void init(
      final ExporterConfiguration configuration,
      final ExporterEntityCacheProvider entityCacheProvider,
      final Context context,
      final ExporterMetadata exporterMetadata,
      final ObjectMapper objectMapper) {}

  @Override
  public void reset() {}

  @Override
  public Collection<IndexDescriptor> getIndexDescriptors() {
    return indexDescriptors.indices();
  }

  @Override
  public Collection<IndexTemplateDescriptor> getIndexTemplateDescriptors() {
    return indexDescriptors.templates();
  }

  @Override
  public <T extends IndexTemplateDescriptor> T getIndexTemplateDescriptor(
      final Class<T> descriptorClass) {
    return indexDescriptors.get(descriptorClass);
  }

  @Override
  public <T extends IndexDescriptor> T getIndexDescriptor(final Class<T> descriptorClass) {
    return indexDescriptors.get(descriptorClass);
  }

  @Override
  public Set<ExportHandler<?, ?>> getExportHandlers() {
    return Set.of();
  }

  @Override
  public BiConsumer<String, Error> getCustomErrorHandlers() {
    return null;
  }

  @Override
  public ExporterEntityCacheImpl<Long, CachedProcessEntity> getProcessCache() {
    return null;
  }

  @Override
  public ExporterEntityCacheImpl<Long, CachedDecisionRequirementsEntity>
      getDecisionRequirementsCache() {
    return null;
  }

  @Override
  public ExporterEntityCacheImpl<String, CachedFormEntity> getFormCache() {
    return null;
  }
}

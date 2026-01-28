/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCacheImpl;
import io.camunda.zeebe.exporter.common.cache.decisionRequirements.CachedDecisionRequirementsEntity;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import java.util.Collection;
import java.util.Set;
import java.util.function.BiConsumer;

public interface ExporterResourceProvider {

  void init(
      final ExporterConfiguration configuration,
      final ExporterEntityCacheProvider entityCacheProvider,
      final Context context,
      final ExporterMetadata exporterMetadata,
      final ObjectMapper objectMapper);

  /** Resets the provider to its initial state. To avoid unnecessary resources consumptions. */
  void reset();

  /**
   * This should return descriptors describing the desired state of all indices provided.
   *
   * @return A {@link Collection} of {@link IndexDescriptor}
   */
  Collection<IndexDescriptor> getIndexDescriptors();

  /**
   * This should return descriptors describing the desired state of all index templates provided.
   *
   * @return A {@link Collection} of {@link IndexTemplateDescriptor}
   */
  Collection<IndexTemplateDescriptor> getIndexTemplateDescriptors();

  /**
   * @param descriptorClass the expected descriptor type
   * @return the index template descriptor instance for the given class.
   * @param <T> the expected descriptor type
   */
  <T extends IndexTemplateDescriptor> T getIndexTemplateDescriptor(Class<T> descriptorClass);

  /**
   * @param descriptorClass the expected descriptor type
   * @return the index descriptor instance for the given class.
   * @param <T> the expected descriptor type
   */
  <T extends IndexDescriptor> T getIndexDescriptor(Class<T> descriptorClass);

  /**
   * @return A {@link Set} of {@link ExportHandler} to be registered with the exporter
   */
  Set<ExportHandler<?, ?>> getExportHandlers();

  /**
   * Possible custom error handlers to be used if certain indices threw persistence errors. The
   * first parameter is the name of the index that threw the error and the second is the error
   * details
   *
   * @return A {@link BiConsumer} of {@link String} and {@link Error} to handle custom errors
   */
  BiConsumer<String, Error> getCustomErrorHandlers();

  /**
   * Returns the reference to the Process Cache
   *
   * @return {@link ExporterEntityCacheImpl} of {@link CachedProcessEntity}
   */
  ExporterEntityCacheImpl<Long, CachedProcessEntity> getProcessCache();

  /**
   * Returns the reference to the Decision Cache
   *
   * @return {@link ExporterEntityCacheImpl} of {@link CachedDecisionRequirementsEntity}
   */
  ExporterEntityCacheImpl<Long, CachedDecisionRequirementsEntity> getDecisionRequirementsCache();

  /**
   * Returns the reference to the Form Cache
   *
   * @return {@link ExporterEntityCacheImpl} of {@link CachedFormEntity}
   */
  ExporterEntityCacheImpl<String, CachedFormEntity> getFormCache();
}

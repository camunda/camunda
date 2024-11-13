/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.Collection;
import java.util.Set;

public interface ExporterResourceProvider {

  void init(
      final ExporterConfiguration configuration,
      final ExporterEntityCacheProvider entityCacheProvider);

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
   * @return A {@link Set} of {@link ExportHandler} to be registered with the exporter
   */
  Set<ExportHandler<?, ?>> getExportHandlers();
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import io.camunda.exporter.config.ElasticsearchProperties.IndexSettings;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;
import java.util.Set;

public interface SearchEngineClient {
  void createIndex(final IndexDescriptor indexDescriptor);

  void createIndexTemplate(
      final IndexTemplateDescriptor indexDescriptor,
      final IndexSettings settings,
      final boolean create);

  /**
   * @param indexDescriptor Representing index of which to update the mappings
   * @param newProperties New properties to be appended to the index
   */
  void putMapping(
      final IndexDescriptor indexDescriptor, final Set<IndexMappingProperty> newProperties);
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SearchEngineClient {
  void createIndex(final IndexDescriptor indexDescriptor, final IndexSettings settings);

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

  Map<String, IndexMapping> getMappings(
      final String namePattern, final MappingSource mappingSource);

  void putSettings(
      final List<IndexDescriptor> indexDescriptors, final Map<String, String> toAppendSettings);

  void putIndexLifeCyclePolicy(final String policyName, final String deletionMinAge);
}

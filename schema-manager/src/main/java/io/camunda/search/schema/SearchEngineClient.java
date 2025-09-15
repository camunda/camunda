/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SearchEngineClient extends CloseableSilently {
  void createIndex(final IndexDescriptor indexDescriptor, final IndexConfiguration settings);

  /**
   * @param indexDescriptor indexDescriptor
   * @param indexConfiguration indexConfiguration
   * @param create If true, this request cannot replace or update existing index templates.
   */
  void createIndexTemplate(
      final IndexTemplateDescriptor indexDescriptor,
      final IndexConfiguration indexConfiguration,
      final boolean create);

  /**
   * @param indexDescriptor Representing index of which to update the mappings
   * @param newProperties New properties to be appended to the index
   */
  void putMapping(
      final IndexDescriptor indexDescriptor, final Collection<IndexMappingProperty> newProperties);

  Map<String, IndexMapping> getMappings(
      final String namePattern, final MappingSource mappingSource);

  void putSettings(
      final List<IndexDescriptor> indexDescriptors, final Map<String, String> toAppendSettings);

  void putIndexLifeCyclePolicy(final String policyName, final String deletionMinAge);

  void putIndexMeta(final String indexName, Map<String, Object> meta);

  boolean importersCompleted(
      final int partitionId, final List<IndexDescriptor> importPositionIndices);

  void updateIndexTemplateSettings(
      final IndexTemplateDescriptor indexTemplateDescriptor,
      final IndexConfiguration indexConfiguration);

  void deleteIndex(final String indexName);

  void truncateIndex(final String indexName);

  boolean isHealthy();
}

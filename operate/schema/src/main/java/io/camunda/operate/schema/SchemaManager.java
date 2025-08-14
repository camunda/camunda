/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import java.util.Map;
import java.util.Set;

public interface SchemaManager {

  String REFRESH_INTERVAL = "index.refresh_interval";
  String NO_REFRESH = "-1";
  String NUMBERS_OF_REPLICA = "index.number_of_replicas";
  String NO_REPLICA = "0";

  String OPERATE_DELETE_ARCHIVED_INDICES = "operate_delete_archived_indices";
  String INDEX_LIFECYCLE_NAME = "index.lifecycle.name";
  String DELETE_PHASE = "delete";

  void createSchema();

  void createDefaults();

  void createIndex(IndexDescriptor indexDescriptor, String indexClasspathResource);

  void createTemplate(TemplateDescriptor templateDescriptor, String templateClasspathResource);

  boolean setIndexSettingsFor(Map<String, ?> settings, String indexPattern);

  String getOrDefaultRefreshInterval(String indexName, String defaultValue);

  String getOrDefaultNumbersOfReplica(String indexName, String defaultValue);

  void refresh(final String indexPattern);

  boolean isHealthy();

  Set<String> getIndexNames(final String indexPattern);

  Set<String> getAliasesNames(final String indexPattern);

  long getNumberOfDocumentsFor(final String... indexPatterns);

  boolean deleteIndicesFor(final String indexPattern);

  boolean deleteTemplatesFor(final String deleteTemplatePattern);

  void removePipeline(String pipelineName);

  boolean addPipeline(String name, String pipelineDefinition);

  Map<String, String> getIndexSettingsFor(String s, String... fields);

  String getIndexPrefix();

  Map<String, IndexMapping> getIndexMappings(String indexNamePattern);

  void updateSchema(Map<IndexDescriptor, Set<IndexMappingProperty>> newFields);

  IndexMapping getExpectedIndexFields(IndexDescriptor indexDescriptor);

  void updateIndexSettings();
}

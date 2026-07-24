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
  String NUMBER_OF_SHARDS = "index.number_of_shards";
  String DEFAULT_SHARDS = "1";

  String OPERATE_DELETE_ARCHIVED_INDICES = "operate_delete_archived_indices";
  String INDEX_LIFECYCLE_NAME = "index.lifecycle.name";
  String DELETE_PHASE = "delete";

  void createSchema();

  /**
   * Creates the ILM/ISM retention policies, the component template and the index templates that
   * make up the schema, but not the data indices themselves.
   *
   * <p>Index templates and ILM/ISM policies are not included in Elasticsearch/OpenSearch snapshots.
   * After restoring a backup into an empty cluster the restored indices exist while the templates
   * and policies are missing; without them the archiver would later create indices with an
   * incorrect structure. This method (re)creates any missing templates and policies. Templates are
   * created idempotently: existing ones are left untouched (created with {@code overwrite=false}).
   *
   * <p>This is only ever invoked when schema creation is enabled ({@code createSchema=true}), i.e.
   * when the application is permitted to manage the schema and its retention policies. Guarding on
   * that flag is what makes touching ILM/ISM safe here: it avoids altering (or failing to alter,
   * for lack of permissions) a pre-existing retention configuration on restart (see <a
   * href="https://github.com/camunda/camunda/issues/28571">#28571</a>).
   *
   * @see <a href="https://github.com/camunda/camunda/issues/32806">#32806</a>
   */
  void createSchemaTemplatesAndPolicies();

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

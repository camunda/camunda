/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema;


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
}

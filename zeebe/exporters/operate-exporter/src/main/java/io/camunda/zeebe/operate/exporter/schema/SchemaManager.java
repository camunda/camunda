/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.schema;

import io.camunda.zeebe.operate.exporter.schema.IndexMapping.IndexMappingProperty;
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

  void checkAndUpdateIndices();

  void createDefaults();

  void createIndex(IndexDescriptor indexDescriptor, String indexClasspathResource);

  void createTemplate(TemplateDescriptor templateDescriptor, String templateClasspathResource);

  void updateSchema(Map<IndexDescriptor, Set<IndexMappingProperty>> newFields);

  Map<String, IndexMapping> getIndexMappings(String s);

  String getIndexPrefix();

  IndexMapping getExpectedIndexFields(IndexDescriptor indexDescriptor);

  Set<String> getIndexNames(String s);

  Set<String> getAliasesNames(String s);
}

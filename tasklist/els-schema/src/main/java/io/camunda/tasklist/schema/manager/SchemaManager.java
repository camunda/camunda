/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.manager;

import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public interface SchemaManager {

  void createSchema();

  IndexMapping getExpectedIndexFields(IndexDescriptor indexDescriptor);

  Map<String, IndexMapping> getIndexMappings(String s) throws IOException;

  String getIndexPrefix();

  void updateSchema(Map<IndexDescriptor, Set<IndexMappingProperty>> newFields);

  void createIndex(IndexDescriptor testIndex);

  void updateIndexSettings();
}

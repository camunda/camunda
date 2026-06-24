/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder;
import java.util.Set;

public interface IndexRepository {
  void createMissingIndices(
      final IndexMappingCreatorBuilder indexMappingCreatorBuilder,
      final Set<String> readOnlyAliases,
      final Set<String> keys);

  boolean indexExists(IndexMappingCreatorBuilder indexMappingCreatorBuilder, String key);
}

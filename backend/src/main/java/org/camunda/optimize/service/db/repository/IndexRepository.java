/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository;

import java.util.Set;
import org.camunda.optimize.service.db.schema.index.IndexMappingCreatorBuilder;

public interface IndexRepository {
  void createMissingIndices(
      final IndexMappingCreatorBuilder indexMappingCreatorBuilder,
      final Set<String> readOnlyAliases,
      final Set<String> keys);

  boolean indexExists(IndexMappingCreatorBuilder indexMappingCreatorBuilder, String key);
}

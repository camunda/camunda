/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import java.util.Set;

public interface MappingMetadataRepository {
  String[] getIndexAliasesWithImportIndexFlag(final boolean isImportIndex);

  /**
   * Returns the process definition keys that currently have a process instance index, resolved in a
   * single request against the index pattern rather than one existence check per key.
   *
   * <p>Instance indices are created by {@code ProcessInstanceWriter} when the first instance for a
   * definition is imported, so a definition that is deployed but never run has none. Naming a
   * missing index in a search makes Elasticsearch reject the whole request with {@code
   * index_not_found}, which callers evaluating several definitions at once need to avoid.
   */
  Set<String> getProcessDefinitionKeysWithInstanceIndex();
}

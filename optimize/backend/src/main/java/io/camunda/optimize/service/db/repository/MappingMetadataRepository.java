/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.service.db.schema.BackupPriority;
import java.util.Set;

public interface MappingMetadataRepository {
  String[] getIndexAliasesWithBackupPriority(final BackupPriority backupPriority);

  /**
   * Returns the process definition keys that currently have an instance index, <strong>lowercased
   * </strong>, resolved in a single request against the index pattern rather than one existence
   * check per key.
   *
   * <p>The keys are lowercased because that is how they exist in the index names these values are
   * derived from — {@code ProcessInstanceIndex#constructIndexName} lowercases the definition key,
   * and the engines reject uppercase index names outright. A definition keeps the casing of its
   * BPMN process id, so callers comparing against a definition key must lowercase that side;
   * comparing directly silently drops every mixed-case key.
   *
   * <p>Instance indices are created by {@code ProcessInstanceWriter} when the first instance for a
   * definition is imported, so a definition that is deployed but never run has none. Naming a
   * missing index in a search fails the request with {@code index_not_found}; a caller pinning
   * several definitions recovers via a retry against the instance multi alias, at the cost of
   * opening every instance index in the cluster, so it is cheaper to leave those definitions out.
   */
  Set<String> getProcessDefinitionKeysWithInstanceIndex();
}

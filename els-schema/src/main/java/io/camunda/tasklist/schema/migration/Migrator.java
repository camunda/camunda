/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.schema.migration;

import io.camunda.tasklist.exceptions.MigrationException;

/**
 * Migrates an Tasklist schema from one version to another. Requires an already created destination
 * schema provided by a schema manager.
 *
 * <p>Tries to detect source/previous schema if not provided.
 */
public interface Migrator {

  void migrate() throws MigrationException;
}

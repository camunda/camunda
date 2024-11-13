/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.migration;

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

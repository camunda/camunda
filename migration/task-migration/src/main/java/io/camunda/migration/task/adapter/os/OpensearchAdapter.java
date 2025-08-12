/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter.os;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.task.adapter.TaskMigrationAdapter;
import io.camunda.migration.task.config.TaskMigrationProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class OpensearchAdapter implements TaskMigrationAdapter {
  private final TaskMigrationProperties properties;
  private final ConnectConfiguration connect;

  public OpensearchAdapter(
      final TaskMigrationProperties properties, final ConnectConfiguration connect) {
    this.properties = properties;
    this.connect = connect;
  }

  @Override
  public List<String> getDatedTaskIndices() {
    return List.of();
  }

  @Override
  public void reindexDatedIndex(final String sourceDatedIndex) throws MigrationException {}

  @Override
  public void reindexMainIndex() throws MigrationException {}

  @Override
  public void deleteIndex(final String indexName) throws MigrationException {}

  @Override
  public String getLastMigratedTaskKey() throws MigrationException {
    return "";
  }

  @Override
  public void writeLastMigratedEntity(final String processDefinitionKey)
      throws MigrationException {}

  @Override
  public List<TaskEntity> nextBatch(final String lastMigratedTaskKey) throws MigrationException {
    return List.of();
  }

  @Override
  public String updateEntities(final List<TaskEntity> entities) throws MigrationException {
    return "";
  }

  @Override
  public Set<ImportPositionEntity> readImportPosition() throws MigrationException {
    return Set.of();
  }

  @Override
  public void close() throws IOException {}
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter.os;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.task.adapter.TaskEntityPair;
import io.camunda.migration.task.adapter.TaskMigrationAdapter;
import io.camunda.migration.task.config.TaskMigrationProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.opensearch.client.opensearch.OpenSearchClient;

public class OpensearchAdapter implements TaskMigrationAdapter {
  private final OpenSearchClient client;
  private final TaskMigrationProperties properties;
  private final ConnectConfiguration connect;

  public OpensearchAdapter(
      final TaskMigrationProperties properties, final ConnectConfiguration connectConfiguration) {
    this.properties = properties;
    connect = connectConfiguration;
    client = new OpensearchConnector(connectConfiguration).createClient();
  }

  @Override
  public boolean migrationIndexExists() {
    return false;
  }

  @Override
  public boolean migrationIsCompleted() {
    return false;
  }

  @Override
  public void markMigrationAsCompleted() throws MigrationException {}

  @Override
  public List<String> getLegacyDatedIndices() {
    return List.of();
  }

  @Override
  public void reindexLegacyDatedIndex(final String legacyDatedIndex) throws MigrationException {}

  @Override
  public void reindexLegacyMainIndex() throws MigrationException {}

  @Override
  public void deleteLegacyIndex(final String legacyIndex) throws MigrationException {}

  @Override
  public void deleteLegacyMainIndex() throws MigrationException {}

  @Override
  public String getLastMigratedTaskId() throws MigrationException {
    return "";
  }

  @Override
  public void writeLastMigratedTaskId(final String taskId) throws MigrationException {}

  @Override
  public List<TaskEntityPair> nextBatch(final String lastMigratedTaskId) throws MigrationException {
    return List.of();
  }

  @Override
  public String updateInNewMainIndex(final List<TaskEntity> tasks) throws MigrationException {
    return "";
  }

  @Override
  public Set<ImportPositionEntity> getImportPositions() throws MigrationException {
    return Set.of();
  }

  @Override
  public void close() throws IOException {}
}

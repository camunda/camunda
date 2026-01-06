/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema.strategy;

import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestStandaloneBackupManager;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import java.io.IOException;
import java.util.List;
import org.testcontainers.containers.GenericContainer;

public interface SearchBackendStrategy extends AutoCloseable {

  @Override
  default void close() {
    final var container = getContainer();
    if (container != null) {
      container.stop();
    }
  }

  // Methods to bootstrap the search backend
  void startContainer() throws Exception;

  GenericContainer<?> getContainer();

  void createAdminClient() throws Exception;

  void createSchema() throws Exception;

  void configureStandaloneSchemaManager(final TestStandaloneSchemaManager schemaManager);

  void configureStandaloneBackupManager(
      final TestStandaloneBackupManager schemaManager, final String repositoryName);

  void configureCamundaApplication(final TestCamundaApplication camunda);

  // Methods to interact with the search backend
  long countDocuments(final String indexPattern) throws Exception;

  long countTemplates(final String namePattern) throws Exception;

  long searchByKey(final String indexPattern, final long key) throws Exception;

  void deleteIndices(final String indexName, final String... indexNames) throws IOException;

  boolean indicesExist(final String indexName, final String... indexNames) throws Exception;

  List<String> getSuccessSnapshots(final String repositoryName, final String snapshotNamePrefix)
      throws Exception;

  void restoreBackup(final String repositoryName, final String snapshot) throws IOException;

  void createSnapshotRepository(final String repositoryName) throws IOException;
}

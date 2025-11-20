/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema.strategy;

import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import org.testcontainers.containers.GenericContainer;

public interface SearchBackendStrategy extends AutoCloseable {

  default void initialize(
      final TestStandaloneSchemaManager schemaManager, final TestCamundaApplication camunda)
      throws Exception {
    startContainer();
    createAdminClient();
    createSchema();
    configureStandaloneSchemaManager(schemaManager);
    configureCamundaApplication(camunda);
    schemaManager.start();
    camunda.start();
  }

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

  void configureCamundaApplication(final TestCamundaApplication camunda);

  // Methods to interact with the search backend
  long countDocuments(final String indexPattern) throws Exception;

  long countTemplates(final String namePattern) throws Exception;

  long searchByKey(final String indexPattern, final long key) throws Exception;
}

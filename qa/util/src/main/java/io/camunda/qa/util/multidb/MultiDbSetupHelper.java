/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

/**
 * Helper used in {@link CamundaMultiDBExtension} to manage the multi database setup.
 *
 * <p>Different implementations might exist related to different targets, like OpenSearch,
 * ElasticSearch, RDBMS, etc.
 */
public interface MultiDbSetupHelper extends AutoCloseable {

  /**
   * Validates that the connection can be established to the secondary storage
   *
   * @return true if connection can be established, false otherwise
   */
  boolean validateConnection();

  /**
   * To validate schema creation, used to store related test data, which can be identified under
   * given test prefix.
   *
   * @param prefix prefix used to identify related test data
   * @return true if schema has been created successfully, false otherwise
   */
  boolean validateSchemaCreation(final String prefix);

  /**
   * Clean up test data. Can be called after all tests have been executed.
   *
   * <p>Implementations should make sure to clean all related test data (related to the test
   * prefix).
   *
   * @param prefix prefix used to identify related test data
   */
  void cleanup(final String prefix);
}

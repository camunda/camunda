/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store;

public interface MetadataStore {
  // Schema version metadata constants
  String SCHEMA_VERSION_METADATA_ID = "schema-version";

  /**
   * Retrieves the schema version from metadata storage. Returns null if no version is stored
   * (indicating a fresh installation).
   */
  String getSchemaVersion();

  /**
   * Stores the current application version as the schema version in metadata storage. This should
   * be called after a successful schema upgrade.
   */
  void storeSchemaVersion(final String version);
}

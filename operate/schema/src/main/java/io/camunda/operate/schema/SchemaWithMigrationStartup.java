/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.DatastoreProperties;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.schema.migration.Migrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaWithMigrationStartup extends SchemaStartup {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaWithMigrationStartup.class);
  protected final Migrator migrator;
  protected final MigrationProperties migrationProperties;

  public SchemaWithMigrationStartup(
      final SchemaManager schemaManager,
      final IndexSchemaValidator schemaValidator,
      final DatastoreProperties datastoreProperties,
      final Migrator migrator,
      final MigrationProperties migrationProperties) {
    super(schemaManager, schemaValidator, datastoreProperties);
    this.migrator = migrator;
    this.migrationProperties = migrationProperties;
  }

  @Override
  public void migrateDataWhenNeeded() throws MigrationException {
    if (migrator != null && migrationProperties.isMigrationEnabled()) {
      LOGGER.info("SchemaStartup: migrate schema.");
      migrator.migrateData();
    }
  }
}

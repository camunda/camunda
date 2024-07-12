/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.DatastoreProperties;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.indices.IndexDescriptor;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaStartup {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaStartup.class);
  protected final SchemaManager schemaManager;
  protected final IndexSchemaValidator schemaValidator;
  protected final DatastoreProperties datastoreProperties;

  public SchemaStartup(
      final SchemaManager schemaManager,
      final IndexSchemaValidator schemaValidator,
      final DatastoreProperties datastoreProperties) {
    this.schemaManager = schemaManager;
    this.schemaValidator = schemaValidator;
    this.datastoreProperties = datastoreProperties;
  }

  public void initializeSchema() {
    try {
      LOGGER.info("SchemaStartup started.");
      LOGGER.info("SchemaStartup: validate index versions.");
      schemaValidator.validateIndexVersions();
      LOGGER.info("SchemaStartup: validate index mappings.");
      final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields =
          schemaValidator.validateIndexMappings();
      final boolean createSchema = datastoreProperties.isCreateSchema();
      if (createSchema && !schemaValidator.schemaExists()) {
        LOGGER.info("SchemaStartup: schema is empty or not complete. Indices will be created.");
        schemaManager.createSchema();
        LOGGER.info("SchemaStartup: update index mappings.");
      } else {
        LOGGER.info(
            "SchemaStartup: schema won't be created, it either already exist, or schema creation is disabled in configuration.");
      }
      if (createSchema) {
        schemaManager.checkAndUpdateIndices();
      }
      if (!newFields.isEmpty()) {
        if (createSchema) {
          schemaManager.updateSchema(newFields);
        } else {
          LOGGER.info(
              "SchemaStartup: schema won't be updated as schema creation is disabled in configuration.");
        }
      }
      migrateDataWhenNeeded();
      LOGGER.info("SchemaStartup finished.");
    } catch (final Exception ex) {
      LOGGER.error("Schema startup failed: " + ex.getMessage(), ex);
      throw new OperateRuntimeException(ex);
    }
  }

  public void migrateDataWhenNeeded() throws MigrationException {
    // no migration in the future
  }
}

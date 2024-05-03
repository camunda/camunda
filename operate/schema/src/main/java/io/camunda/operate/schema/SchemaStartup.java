/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.migration.Migrator;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaStartup")
@DependsOn("databaseInfo")
@Profile("!test")
public class SchemaStartup {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaStartup.class);

  @Autowired private SchemaManager schemaManager;

  @Autowired private IndexSchemaValidator schemaValidator;

  @Autowired private Migrator migrator;

  @Autowired private OperateProperties operateProperties;

  @Autowired private MigrationProperties migrationProperties;

  @PostConstruct
  public void initializeSchema() throws MigrationException {
    try {
      LOGGER.info("SchemaStartup started.");
      LOGGER.info("SchemaStartup: validate index versions.");
      schemaValidator.validateIndexVersions();
      LOGGER.info("SchemaStartup: validate index mappings.");
      final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields =
          schemaValidator.validateIndexMappings();
      final boolean createSchema =
          DatabaseInfo.isOpensearch()
              ? operateProperties.getOpensearch().isCreateSchema()
              : operateProperties.getElasticsearch().isCreateSchema();
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
      if (migrationProperties.isMigrationEnabled()) {
        LOGGER.info("SchemaStartup: migrate schema.");
        migrator.migrateData();
      }
      LOGGER.info("SchemaStartup finished.");
    } catch (final Exception ex) {
      LOGGER.error("Schema startup failed: " + ex.getMessage(), ex);
      throw ex;
    }
  }
}

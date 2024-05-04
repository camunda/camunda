/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema;

import io.camunda.tasklist.exceptions.MigrationException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.MigrationProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.schema.migration.Migrator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaStartup")
@Profile("!test")
public class SchemaStartup {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaStartup.class);

  @Autowired private SchemaManager schemaManager;

  @Autowired private IndexSchemaValidator schemaValidator;

  @Autowired private Migrator migrator;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private MigrationProperties migrationProperties;

  @PostConstruct
  public void initializeSchema() throws MigrationException {
    LOGGER.info("SchemaStartup started.");
    LOGGER.info("SchemaStartup: validate schema.");
    schemaValidator.validate();

    Boolean isCreateSchema = false;
    if (TasklistProperties.ELASTIC_SEARCH.equalsIgnoreCase(tasklistProperties.getDatabase())) {
      isCreateSchema = tasklistProperties.getElasticsearch().isCreateSchema();
    } else if (TasklistProperties.OPEN_SEARCH.equalsIgnoreCase(tasklistProperties.getDatabase())) {
      isCreateSchema = tasklistProperties.getOpenSearch().isCreateSchema();
    }

    if (isCreateSchema && !schemaValidator.schemaExists()) {
      LOGGER.info("SchemaStartup: schema is empty or not complete. Indices will be created.");
      schemaManager.createSchema();
    } else {
      LOGGER.info(
          "SchemaStartup: schema won't be created, it either already exist, or schema creation is disabled in configuration.");
    }
    if (migrationProperties.isMigrationEnabled()) {
      LOGGER.info("SchemaStartup: migrate schema.");
      try {
        migrator.migrate();
      } catch (Exception e) {
        LOGGER.error("An issue occurred during schema migration, details:", e);
        throw new TasklistRuntimeException("An issue occurred during schema migration", e);
      }
    }
    LOGGER.info("SchemaStartup: finished.");
  }
}

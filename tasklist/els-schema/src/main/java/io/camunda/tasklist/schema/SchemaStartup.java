/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.manager.SchemaManager;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("tasklistSchemaStartup")
@Profile("!test")
public class SchemaStartup {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaStartup.class);

  @Autowired private SchemaManager schemaManager;

  @Autowired private IndexSchemaValidator schemaValidator;

  @Autowired private TasklistProperties tasklistProperties;

  @PostConstruct
  public void initializeSchema() throws IOException {
    try {
      LOGGER.info("SchemaStartup started.");
      LOGGER.info("SchemaStartup: validate index versions.");
      schemaValidator.validateIndexVersions();

      final boolean createSchema =
          TasklistProperties.OPEN_SEARCH.equalsIgnoreCase(tasklistProperties.getDatabase())
              ? tasklistProperties.getOpenSearch().isCreateSchema()
              : tasklistProperties.getElasticsearch().isCreateSchema();
      if (createSchema
          && !(schemaValidator.schemaExists() && schemaValidator.isValidNumberOfReplicas())) {
        LOGGER.info("SchemaStartup: schema is empty or not complete. Indices will be created.");
        schemaManager.createSchema();
        LOGGER.info("SchemaStartup: update index mappings.");
      } else {
        LOGGER.info(
            "SchemaStartup: schema won't be created, it either already exist, or schema creation is disabled in configuration.");
      }

      LOGGER.info("SchemaStartup finished.");
    } catch (final Exception ex) {
      LOGGER.error("Schema startup failed: " + ex.getMessage(), ex);
      throw ex;
    }
  }
}

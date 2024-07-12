/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// @Component("schemaStartup")
// @Profile("test")
public class TestSchemaStartup /*extends SchemaStartup*/ {
  private static final Logger LOGGER = LoggerFactory.getLogger(TestSchemaStartup.class);

  /*public TestSchemaStartup(
      final SchemaManager schemaManager,
      final IndexSchemaValidator schemaValidator,
      final Migrator migrator,
      final DatastoreProperties datastoreProperties,
      final MigrationProperties migrationProperties) {
    super(schemaManager, schemaValidator, datastoreProperties, migrator, migrationProperties);
  }*/

  //  @Override
  public void initializeSchema() {
    LOGGER.info("TestSchemaStartup: no schema will be created, validated or migrated.");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.util;

import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.schema.SchemaStartup;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaStartup")
@Profile("test")
public class TestSchemaStartup extends SchemaStartup {
  private static final Logger LOGGER = LoggerFactory.getLogger(TestSchemaStartup.class);

  @PostConstruct
  @Override
  public void initializeSchema() throws MigrationException {
    LOGGER.info("TestSchemaStartup: no schema will be created, validated or migrated.");
  }
}

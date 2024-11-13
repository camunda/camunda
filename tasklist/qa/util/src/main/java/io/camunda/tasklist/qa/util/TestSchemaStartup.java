/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util;

import io.camunda.tasklist.exceptions.MigrationException;
import io.camunda.tasklist.schema.v86.SchemaStartup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("tasklistSchemaStartup")
@Profile("test")
public class TestSchemaStartup extends SchemaStartup {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestSchemaStartup.class);

  @Override
  public void initializeSchema() throws MigrationException {
    LOGGER.info("No schema validation, creation and migration will be executed.");
  }
}

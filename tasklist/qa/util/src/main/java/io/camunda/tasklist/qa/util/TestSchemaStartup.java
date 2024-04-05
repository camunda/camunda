/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.util;

import io.camunda.tasklist.exceptions.MigrationException;
import io.camunda.tasklist.schema.SchemaStartup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaStartup")
@Profile("test")
public class TestSchemaStartup extends SchemaStartup {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestSchemaStartup.class);

  @Override
  public void initializeSchema() throws MigrationException {
    LOGGER.info("No schema validation, creation and migration will be executed.");
  }
}

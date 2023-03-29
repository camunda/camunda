/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util;

import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.schema.SchemaStartup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component("schemaStartup")
@Profile("test")
public class TestSchemaStartup extends SchemaStartup {
  private static final Logger logger = LoggerFactory.getLogger(TestSchemaStartup.class);
  @PostConstruct
  @Override
  public void initializeSchema() throws MigrationException {
      logger.info("TestSchemaStartup: no schema will be created, validated or migrated.");
  }
}

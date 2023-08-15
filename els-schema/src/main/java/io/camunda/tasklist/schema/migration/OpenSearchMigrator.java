/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.schema.migration;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.MigrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * This is a stub, implementation will be done in the scope of:
 * `https://github.com/camunda/tasklist/issues/3274` ticket
 */
@Component
@Configuration
@Conditional(OpenSearchCondition.class)
public class OpenSearchMigrator implements Migrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchMigrator.class);

  @Override
  public void migrate() throws MigrationException {
    LOGGER.warn(
        "This is a stub, implementation will be done in the scope of: `https://github.com/camunda/tasklist/issues/3274` ticket");
  }
}

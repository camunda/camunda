/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.management;

import io.camunda.tasklist.schema.IndexSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("searchEngineCheck")
public class SearchEngineHealthIndicator implements HealthIndicator {

  private static Logger logger = LoggerFactory.getLogger(SearchEngineHealthIndicator.class);

  @Autowired private SearchEngineCheck searchEngineCheck;
  @Autowired private IndexSchemaValidator indexSchemaValidator;

  @Override
  public Health health() {
    logger.debug("Search engine check is called");
    if (searchEngineCheck.isHealthy() && indexSchemaValidator.schemaExists()) {
      return Health.up().build();
    } else {
      return Health.down().build();
    }
  }

  @Override
  public Health getHealth(final boolean includeDetails) {
    return health();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.management;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("indicesCheck")
public class IndicesHealthIndicator implements HealthIndicator {

  private static final Logger logger = LoggerFactory.getLogger(IndicesHealthIndicator.class);

  @Autowired
  private IndicesCheck indicesCheck;

  @Override
  public Health health() {
    logger.debug("Indices check is called");
    if (indicesCheck.isHealthy() && indicesCheck.indicesArePresent()) {
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

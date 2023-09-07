/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.conditionals;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public abstract class DataBaseCondition implements Condition {

  protected final ConfigurationService configurationService;

  protected DataBaseCondition(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    final String database = configurationService.getDatabaseType();
    return getDefaultIfEmpty() || getDatabase().equalsIgnoreCase(database);
  }

  public abstract boolean getDefaultIfEmpty();

  public abstract String getDatabase();
}

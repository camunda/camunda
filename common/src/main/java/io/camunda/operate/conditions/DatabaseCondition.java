/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.conditions;

import io.micrometer.common.util.StringUtils;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public abstract class DatabaseCondition implements Condition {

  public static final String DATABASE_PROPERTY = "camunda.operate.database";

  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    final String database = context.getEnvironment().getProperty(DATABASE_PROPERTY);
    return (StringUtils.isEmpty(database) && getDefaultIfEmpty())
        || getDatabase().equalsIgnoreCase(database);
  }

  public abstract boolean getDefaultIfEmpty();

  public abstract String getDatabase();
}

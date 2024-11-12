/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

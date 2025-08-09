/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.conditions;

import io.micrometer.common.util.StringUtils;
import java.util.Optional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public abstract class DatabaseCondition implements Condition {

  public static final String DATABASE_PROPERTY = "camunda.operate.database";
  public static final String UNIFIED_CONFIGURATION_DATABASE_PROPERTY =
      "camunda.data.secondary-storage.type";

  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    final Environment environment = context.getEnvironment();

    String dbType = environment.getProperty(UNIFIED_CONFIGURATION_DATABASE_PROPERTY);
    if (dbType == null) {
      dbType =
          Optional.ofNullable(environment.getProperty(DATABASE_PROPERTY)).orElse("elasticsearch");
    }

    return (StringUtils.isEmpty(dbType) && getDefaultIfEmpty())
        || getDatabase().equalsIgnoreCase(dbType);
  }

  public abstract boolean getDefaultIfEmpty();

  public abstract String getDatabase();
}

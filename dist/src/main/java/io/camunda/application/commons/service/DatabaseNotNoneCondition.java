/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that matches when the database type is NOT "none".
 * This prevents service beans from being created when database.type=none,
 * which would fail because search clients are not available.
 */
public class DatabaseNotNoneCondition implements Condition {

  private static final String NONE_DATABASE_TYPE = "none";

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    final String databaseType = context.getEnvironment().getProperty("camunda.database.type");
    
    // If database type is not set, default to enabled (not "none")
    if (databaseType == null || databaseType.trim().isEmpty()) {
      return true;
    }
    
    // Return false only if database type is explicitly set to "none"
    return !NONE_DATABASE_TYPE.equalsIgnoreCase(databaseType);
  }
}
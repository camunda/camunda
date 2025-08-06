/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;
import static io.camunda.spring.utils.DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Conditional annotation that activates beans only when secondary storage is disabled
 * (camunda.database.type=none).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(ConditionalOnSecondaryStorageDisabled.NoSecondaryStorageCondition.class)
public @interface ConditionalOnSecondaryStorageDisabled {

  class NoSecondaryStorageCondition implements Condition {

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final String dbType = context.getEnvironment().getProperty(PROPERTY_CAMUNDA_DATABASE_TYPE);
      return CAMUNDA_DATABASE_TYPE_NONE.equalsIgnoreCase(dbType);
    }
  }
}

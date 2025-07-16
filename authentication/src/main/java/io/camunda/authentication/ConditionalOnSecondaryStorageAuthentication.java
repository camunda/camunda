/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.application.commons.utils.DatabaseTypeUtils;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Conditional annotation that only loads authentication components when secondary storage is enabled.
 * This prevents authentication services that depend on secondary storage from being loaded in no-db mode.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(ConditionalOnSecondaryStorageAuthentication.SecondaryStorageEnabledCondition.class)
public @interface ConditionalOnSecondaryStorageAuthentication {

  class SecondaryStorageEnabledCondition implements Condition {

    private static final Logger LOG = LoggerFactory.getLogger(SecondaryStorageEnabledCondition.class);

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final Environment env = context.getEnvironment();
      if (!DatabaseTypeUtils.isSecondaryStorageEnabled(env)) {
        LOG.warn(
            "Secondary storage is disabled ({}=none). Authentication services that depend on secondary storage will not be loaded.",
            DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE);
        return false;
      }
      return true;
    }
  }
}
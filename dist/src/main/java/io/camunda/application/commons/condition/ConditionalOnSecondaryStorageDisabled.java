/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.condition;

import io.camunda.application.commons.condition.ConditionalOnSecondaryStorageDisabled.OnSecondaryStorageDisabled;
import io.camunda.application.commons.condition.ConditionalOnSecondaryStorageEnabled.OnSecondaryStorageEnabledCondition;
import io.camunda.application.commons.utils.DatabaseTypeUtils;
import io.camunda.search.connect.configuration.DatabaseType;
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
import org.springframework.core.type.AnnotatedTypeMetadata;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(OnSecondaryStorageDisabled.class)
public @interface ConditionalOnSecondaryStorageDisabled {

  class OnSecondaryStorageDisabled implements Condition {

    private static final Logger LOG = LoggerFactory.getLogger(OnSecondaryStorageDisabled.class);

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final var disabled = !new OnSecondaryStorageEnabledCondition().matches(context, metadata);
      if (disabled) {
        LOG.warn(
            "Secondary storage is disabled ({}={}). Some features such as webapps will not start unless a secondary storage is configured. See camunda.database.type config.",
            DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE,
            DatabaseType.NONE);
      }
      return disabled;
    }
  }
}

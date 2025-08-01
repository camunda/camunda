/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.condition;

import static io.camunda.application.commons.utils.DatabaseTypeUtils.UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE;

import io.camunda.application.commons.condition.ConditionalOnSecondaryStorageType.OnSecondaryStorageTypeCondition;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnSecondaryStorageTypeCondition.class)
public @interface ConditionalOnSecondaryStorageType {

  SecondaryStorageType[] value();

  class OnSecondaryStorageTypeCondition implements Condition {

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final var env = context.getEnvironment();
      var strType = env.getProperty(UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE);
      if (strType == null) {
        // TODO: Once we clear the legacy properties from the test envs we have, remove the case
        //  that uses  camunda.database.type
        strType =
            Optional.ofNullable(env.getProperty("camunda.database.type")).orElse("elasticsearch");
      }

      final var type = SecondaryStorageType.valueOf(strType.toLowerCase());

      final var attributes =
          metadata.getAnnotationAttributes(ConditionalOnSecondaryStorageType.class.getName());
      if (attributes == null) {
        return false;
      }

      final var value = attributes.get("value");
      if (value instanceof SecondaryStorageType[] acceptedTypes) {
        return Arrays.asList(acceptedTypes).contains(type);
      }

      return false;
    }
  }
}

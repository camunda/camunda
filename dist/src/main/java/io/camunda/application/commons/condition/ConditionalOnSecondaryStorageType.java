/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.condition;

import static io.camunda.spring.utils.DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE;

import io.camunda.application.commons.condition.ConditionalOnSecondaryStorageType.OnSecondaryStorageTypeCondition;
import io.camunda.search.connect.configuration.DatabaseConfig;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnSecondaryStorageTypeCondition.class)
public @interface ConditionalOnSecondaryStorageType {

  String[] value();

  class OnSecondaryStorageTypeCondition implements Condition {

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final var env = context.getEnvironment();
      final var configuredType = env.getProperty(PROPERTY_CAMUNDA_DATABASE_TYPE);

      final var type =
          Optional.ofNullable(configuredType)
              .filter(StringUtils::hasText)
              .orElse(DatabaseConfig.ELASTICSEARCH);

      if (metadata.isAnnotated(ConditionalOnSecondaryStorageType.class.getName())) {
        final var value =
            Objects.requireNonNull(
                    metadata.getAnnotationAttributes(
                        ConditionalOnSecondaryStorageType.class.getName()))
                .get("value");
        if (value instanceof final String[] acceptedTypes) {
          return Arrays.stream(acceptedTypes)
              .map(String::toLowerCase)
              .anyMatch(type.toLowerCase()::equals);
        }
      }
      return false;
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.condition;

import io.camunda.gatekeeper.spring.condition.ConditionalOnUnprotectedApi.ApiUnprotectedCondition;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(ApiUnprotectedCondition.class)
public @interface ConditionalOnUnprotectedApi {

  /** Property key for the unprotected API flag. */
  String UNPROTECTED_API_PROPERTY = "camunda.security.authentication.unprotected-api";

  /** Default value when no property is set. */
  boolean DEFAULT_UNPROTECTED_API = false;

  final class ApiUnprotectedCondition implements Condition {
    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final Environment env = context.getEnvironment();
      return Optional.ofNullable(env.getProperty(UNPROTECTED_API_PROPERTY, Boolean.class))
          .orElse(DEFAULT_UNPROTECTED_API);
    }
  }
}

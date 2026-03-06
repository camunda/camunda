/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter.condition;

import io.camunda.auth.starter.condition.ConditionalOnUnprotectedApi.ApiUnprotectedCondition;
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

  class ApiUnprotectedCondition implements Condition {
    private static final String PROPERTY = "camunda.auth.unprotected-api";

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final Environment env = context.getEnvironment();
      return Optional.ofNullable(env.getProperty(PROPERTY, Boolean.class)).orElse(false);
    }
  }
}

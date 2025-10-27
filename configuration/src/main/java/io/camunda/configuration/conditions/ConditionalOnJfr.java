/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.conditions;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.conditions.ConditionalOnJfr.OnJfrCondition;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnJfrCondition.class)
public @interface ConditionalOnJfr {

  class OnJfrCondition implements Condition {

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final var helper = new UnifiedConfigurationHelper(context.getEnvironment());
      final var camunda =
          Binder.get(context.getEnvironment()).bind("camunda", Camunda.class).orElse(new Camunda());
      return camunda.getMonitoring().isJfr();
    }
  }
}

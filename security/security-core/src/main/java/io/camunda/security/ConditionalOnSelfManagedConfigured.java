/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security;

import io.camunda.security.ConditionalOnSelfManagedConfigured.SelfManagedConfiguredCondition;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(SelfManagedConfiguredCondition.class)
public @interface ConditionalOnSelfManagedConfigured {

  final class SelfManagedConfiguredCondition implements Condition {

    private final List<String> saasProperties =
        Arrays.asList("camunda.security.saas.organizationId", "camunda.security.saas.clusterId");

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      // Self-Managed is configured when SaaS properties are NOT present or empty
      return saasProperties.stream()
          .map(key -> context.getEnvironment().getProperty(key))
          .allMatch(value -> value == null || value.isEmpty());
    }
  }
}

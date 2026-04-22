/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

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
 * Feature-flag stub for physical tenant isolation (see camunda/camunda#51001).
 *
 * <p>Beans annotated with this conditional are part of the per-physical-tenant scaffolding for the
 * ES/OS read path. They exist in the codebase but must not be instantiated until the rest of the
 * physical-tenant feature (config model, validation, per-tenant client construction, etc.) is in
 * place.
 *
 * <p>The condition matches when the property {@code camunda.physical-tenants.enabled} is set to
 * {@code true}. It defaults to {@code false} so production wiring is unchanged until the feature is
 * complete; tests that exercise the per-tenant beans can opt in by setting the property.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(ConditionalOnPhysicalTenantsEnabled.OnPhysicalTenantsEnabledCondition.class)
public @interface ConditionalOnPhysicalTenantsEnabled {

  String PROPERTY_NAME = "camunda.physical-tenants.enabled";

  class OnPhysicalTenantsEnabledCondition implements Condition {

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      return Boolean.parseBoolean(context.getEnvironment().getProperty(PROPERTY_NAME, "false"));
    }
  }
}

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
 * <p>For now this condition always evaluates to {@code false}. Once the feature flag wiring lands,
 * this class will be updated to read it from configuration (e.g. {@code
 * camunda.physical-tenants.enabled}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(ConditionalOnPhysicalTenantsEnabled.OnPhysicalTenantsEnabledCondition.class)
public @interface ConditionalOnPhysicalTenantsEnabled {

  class OnPhysicalTenantsEnabledCondition implements Condition {

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      // Physical tenant isolation is not yet feature-complete; keep all per-tenant beans disabled.
      return false;
    }
  }
}

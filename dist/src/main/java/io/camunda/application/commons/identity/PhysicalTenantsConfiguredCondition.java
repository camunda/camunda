/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import java.util.LinkedHashMap;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * {@code @Conditional} that matches when {@code camunda.physical-tenants.*} resolves to a non-empty
 * map. Used to gate PT-only configuration classes and controllers without relying on a dedicated
 * Spring profile — config presence is the single switch.
 *
 * <p>Uses {@link Binder} for the same reason {@code
 * PhysicalTenantSecurityChainRegistrar#readTenantIds} does: the property is a structured map of
 * tenant ids, not a flat value, so a placeholder-based {@code @ConditionalOnExpression} would
 * always resolve to the empty default.
 */
public final class PhysicalTenantsConfiguredCondition implements Condition {

  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    return !Binder.get(context.getEnvironment())
        .bind("camunda.physical-tenants", Bindable.mapOf(String.class, Object.class))
        .orElse(new LinkedHashMap<>())
        .isEmpty();
  }
}

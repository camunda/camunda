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
 * Inverse of {@link PhysicalTenantsConfiguredCondition}. Matches when no {@code
 * camunda.physical-tenants.*} entries are configured.
 */
public final class NoPhysicalTenantsConfiguredCondition implements Condition {

  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    return Binder.get(context.getEnvironment())
        .bind("camunda.physical-tenants", Bindable.mapOf(String.class, Object.class))
        .orElse(new LinkedHashMap<>())
        .isEmpty();
  }
}

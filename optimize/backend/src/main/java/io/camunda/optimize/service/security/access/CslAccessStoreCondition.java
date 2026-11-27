/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.access;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.OptimizeProfile;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when Optimize's own access store, not Management Identity, decides access: Self-Managed,
 * with CSL enabled and {@value #MANAGEMENT_IDENTITY_ENABLED} switched off.
 *
 * <p>The switch is internal and defaults to {@code true}, so existing installs keep Management
 * Identity until the migration exists. It is removed once the store becomes the default.
 */
public class CslAccessStoreCondition implements Condition {

  public static final String MANAGEMENT_IDENTITY_ENABLED =
      "optimize.security.management-identity.enabled";
  static final String CSL_ENABLED = "optimize.security.csl.enabled";

  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    final var environment = context.getEnvironment();
    return ConfigurationService.getOptimizeProfile(environment) == OptimizeProfile.CCSM
        && environment.getProperty(CSL_ENABLED, Boolean.class, true)
        && !environment.getProperty(MANAGEMENT_IDENTITY_ENABLED, Boolean.class, true);
  }
}

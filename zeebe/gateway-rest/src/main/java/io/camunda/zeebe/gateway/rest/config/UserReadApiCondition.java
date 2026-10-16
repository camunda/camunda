/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class UserReadApiCondition implements Condition {

  static final String AUTHENTICATION_METHOD_PROPERTY = "camunda.security.authentication.method";
  static final String SCIM_ENABLED_PROPERTY = "camunda.scim.enabled";

  static boolean isOidc(final Environment environment) {
    return "oidc".equalsIgnoreCase(environment.getProperty(AUTHENTICATION_METHOD_PROPERTY, ""));
  }

  static boolean isScimEnabled(final Environment environment) {
    return environment.getProperty(SCIM_ENABLED_PROPERTY, Boolean.class, false);
  }

  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    final var environment = context.getEnvironment();
    return !isOidc(environment) || isScimEnabled(environment);
  }
}

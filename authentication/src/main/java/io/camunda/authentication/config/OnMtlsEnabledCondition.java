/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Custom Spring condition that checks if mTLS authentication is enabled. Supports both relaxed
 * property binding and environment variable formats.
 */
public class OnMtlsEnabledCondition implements Condition {

  private static final Logger LOG = LoggerFactory.getLogger(OnMtlsEnabledCondition.class);

  @Override
  public boolean matches(final ConditionContext ctx, final AnnotatedTypeMetadata md) {
    final var env = ctx.getEnvironment();

    LOG.info("OnMtlsEnabledCondition: Checking mTLS enabled condition");

    // Check relaxed property binding first
    final String relaxedProperty = env.getProperty("camunda.security.authentication.mtls.enabled");
    LOG.info(
        "OnMtlsEnabledCondition: Relaxed property 'camunda.security.authentication.mtls.enabled' = {}",
        relaxedProperty);

    // Check raw environment variable
    final String envVariable = env.getProperty("CAMUNDA_SECURITY_AUTHENTICATION_MTLS_ENABLED");
    LOG.info(
        "OnMtlsEnabledCondition: Environment variable 'CAMUNDA_SECURITY_AUTHENTICATION_MTLS_ENABLED' = {}",
        envVariable);

    // Use the first non-null value
    final String value = relaxedProperty != null ? relaxedProperty : envVariable;
    LOG.info("OnMtlsEnabledCondition: Final value to evaluate = {}", value);

    final boolean result = value != null && "true".equalsIgnoreCase(value);

    LOG.info("OnMtlsEnabledCondition: Condition matches = {}", result);

    if (result) {
      LOG.info("OnMtlsEnabledCondition: mTLS authentication ENABLED - beans will be created");
    } else {
      LOG.info("OnMtlsEnabledCondition: mTLS authentication DISABLED - beans will NOT be created");
    }

    return result;
  }
}

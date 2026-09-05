/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.security.core.port.out.SecurityPathPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Shared {@link SecurityPathPort} for PT tests, built from production wiring so the {@code
 * webapp-enabled} gate under test is the real thing, not a copy. Reads the context {@link
 * Environment} — not a test's hand-rolled {@link MockEnvironment}, which is a separate consumer
 * ({@link PhysicalTenantScopeProvider}'s).
 */
@Configuration
class OcPathsConfig {

  @Bean
  SecurityPathPort securityPathPort(final Environment environment) {
    return new WebSecurityConfig().securityPathPort(environment);
  }
}

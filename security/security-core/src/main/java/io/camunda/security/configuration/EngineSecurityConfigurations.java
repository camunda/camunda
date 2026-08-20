/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import java.util.regex.Pattern;

/** Factory methods for commonly used {@link EngineSecurityConfig} presets. */
public class EngineSecurityConfigurations {

  /** Default compiled pattern for validating user/resource identifier strings. */
  public static final Pattern ID_VALIDATION_PATTERN = Pattern.compile("^[a-zA-Z0-9_~@.+-]+$");

  /** Default compiled pattern for validating group identifier strings. */
  public static final Pattern GROUP_ID_VALIDATION_PATTERN = Pattern.compile(".*", Pattern.DOTALL);

  /**
   * Creates an {@link EngineSecurityConfig} preset where neither authentication nor authorization
   * checks are enforced. The API is unprotected and multi-tenancy checks are disabled. Useful for
   * testing or environments where security is intentionally bypassed.
   *
   * @return an {@link EngineSecurityConfig} with authentication method {@code BASIC}, unprotected
   *     API, and both authorization and multi-tenancy checks disabled
   */
  public static EngineSecurityConfig unauthenticatedAndUnauthorized() {
    final var authentication = new AuthenticationConfiguration();
    authentication.setMethod(AuthenticationMethod.BASIC);
    authentication.setUnprotectedApi(true);
    return new EngineSecurityConfig(
        authentication,
        /* authorizationsEnabled= */ false,
        /* multiTenancyChecksEnabled= */ false,
        new InitializationConfiguration(),
        ID_VALIDATION_PATTERN,
        GROUP_ID_VALIDATION_PATTERN);
  }

  /**
   * Creates an {@link EngineSecurityConfig} preset representing the default security configuration.
   * Authorization checks are enabled, while multi-tenancy checks are disabled. Uses the default
   * {@link AuthenticationConfiguration} and validation patterns.
   *
   * @return an {@link EngineSecurityConfig} with authorizations enabled and multi-tenancy checks
   *     disabled
   */
  public static EngineSecurityConfig defaultConfig() {
    return new EngineSecurityConfig(
        new AuthenticationConfiguration(),
        /* authorizationsEnabled= */ true,
        /* multiTenancyChecksEnabled= */ false,
        new InitializationConfiguration(),
        ID_VALIDATION_PATTERN,
        GROUP_ID_VALIDATION_PATTERN);
  }
}

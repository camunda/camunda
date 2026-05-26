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

  public static EngineSecurityConfig unauthenticatedAndUnauthorized() {
    final var authentication = new AuthenticationConfiguration();
    authentication.setMethod(AuthenticationMethod.BASIC);
    authentication.setUnprotectedApi(true);
    return new EngineSecurityConfig(
        authentication,
        /* authorizationsEnabled= */ false,
        /* multiTenancyChecksEnabled= */ false,
        new InitializationConfiguration(),
        Pattern.compile("^[a-zA-Z0-9_~@.+-]+$"),
        Pattern.compile(".*", Pattern.DOTALL));
  }

  public static EngineSecurityConfig defaultConfig() {
    return new EngineSecurityConfig(
        new AuthenticationConfiguration(),
        /* authorizationsEnabled= */ true,
        /* multiTenancyChecksEnabled= */ false,
        new InitializationConfiguration(),
        Pattern.compile("^[a-zA-Z0-9_~@.+-]+$"),
        Pattern.compile(".*", Pattern.DOTALL));
  }
}

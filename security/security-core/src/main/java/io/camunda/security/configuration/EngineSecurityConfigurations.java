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
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.validation.IdentifierValidator;
import java.util.regex.Pattern;

/** Factory methods for commonly used {@link EngineSecurityConfig} presets. */
public class EngineSecurityConfigurations {

  public static EngineSecurityConfig unauthenticatedAndUnauthorized() {
    final var authentication = new AuthenticationConfiguration();
    authentication.setMethod(AuthenticationMethod.BASIC);
    authentication.setUnprotectedApi(true);
    final var authorizations = new AuthorizationsConfiguration();
    authorizations.setEnabled(false);
    return new EngineSecurityConfig(
        authentication,
        authorizations,
        new MultiTenancyConfiguration(),
        new InitializationConfiguration(),
        new IdentifierValidator(
            Pattern.compile("^[a-zA-Z0-9_~@.+-]+$"), Pattern.compile(".*", Pattern.DOTALL)));
  }

  public static EngineSecurityConfig defaultConfig() {
    return new EngineSecurityConfig(
        new AuthenticationConfiguration(),
        new AuthorizationsConfiguration(),
        new MultiTenancyConfiguration(),
        new InitializationConfiguration(),
        new IdentifierValidator(
            Pattern.compile("^[a-zA-Z0-9_~@.+-]+$"), Pattern.compile(".*", Pattern.DOTALL)));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.zeebe.util.VisibleForTesting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CamundaSecurityLibraryProperties.class)
public class CamundaSecurityConfiguration {

  @VisibleForTesting
  public static final String UNPROTECTED_API_ENV_VAR =
      "CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI";

  @VisibleForTesting
  public static final String AUTHORIZATION_CHECKS_ENV_VAR =
      "CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED";

  private final CamundaSecurityLibraryProperties properties;

  @Autowired
  public CamundaSecurityConfiguration(final CamundaSecurityLibraryProperties properties) {
    this.properties = properties;
  }

  @Bean
  public InitializationConfiguration initializationConfiguration() {
    return properties.getInitialization();
  }

  @Bean
  public MultiTenancyConfiguration multiTenancyConfiguration() {
    return properties.getMultiTenancy();
  }

  @Bean
  public IdentifierValidator identifierValidator() {
    return new IdentifierValidator(
        properties.getCompiledIdValidationPattern(),
        properties.getCompiledGroupIdValidationPattern());
  }
}

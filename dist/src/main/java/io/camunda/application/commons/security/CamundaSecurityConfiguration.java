/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.zeebe.util.VisibleForTesting;
import jakarta.annotation.PostConstruct;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(io.camunda.security.spring.CamundaSecurityConfiguration.class)
public class CamundaSecurityConfiguration {

  @VisibleForTesting
  public static final String UNPROTECTED_API_ENV_VAR =
      "CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI";

  @VisibleForTesting
  public static final String AUTHORIZATION_CHECKS_ENV_VAR =
      "CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED";

  private static final String MULTI_TENANCY_CHECKS_ENABLED_PROPERTY =
      "camunda.security.multiTenancy.checksEnabled";

  private static final String CONFIGURED_TENANTS_PROPERTY =
      "camunda.security.initialization.tenants";

  @VisibleForTesting
  public static final String TENANTS_API_DISABLED_WARNING =
      "Multi-tenancy checks are enabled (%s=true) while the tenants API is disabled (%s=false). Tenants cannot be created or modified through the API and must be provisioned via %s. Set %s to true to manage tenants over the API."
          .formatted(
              MULTI_TENANCY_CHECKS_ENABLED_PROPERTY,
              MultiTenancyConfiguration.API_ENABLED_PROPERTY,
              CONFIGURED_TENANTS_PROPERTY,
              MultiTenancyConfiguration.API_ENABLED_PROPERTY);

  private static final Logger LOG = LoggerFactory.getLogger(CamundaSecurityConfiguration.class);

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

  @PostConstruct
  public void validate() {
    final var multiTenancyEnabled = properties.getMultiTenancy().isChecksEnabled();
    final var apiUnprotected = properties.getAuthentication().isUnprotectedApi();

    if (multiTenancyEnabled && apiUnprotected) {
      throw new IllegalStateException(
          "Multi-tenancy is enabled (%s=%b), but the API is unprotected (%s=%b). Please enable API protection if you want to make use of multi-tenancy."
              .formatted(
                  MULTI_TENANCY_CHECKS_ENABLED_PROPERTY,
                  true,
                  "camunda.security.authentication.unprotected-api",
                  true));
    }

    if (multiTenancyEnabled && !properties.getMultiTenancy().isApiEnabled()) {
      LOG.warn(TENANTS_API_DISABLED_WARNING);
    }

    final var idRegex = properties.getIdValidationPattern();
    try {
      final var idPattern = properties.getCompiledIdValidationPattern();
      if (idPattern != null && idPattern.matcher(AuthorizationScope.WILDCARD_CHAR).matches()) {
        throw new IllegalStateException(
            "The configured identifier pattern (%s=%s) allows the asterisk ('*') which is a reserved character. Please use a different pattern."
                .formatted("camunda.security.id-validation-pattern", idRegex));
      }
    } catch (final PatternSyntaxException regEx) {
      throw new IllegalStateException(
          "The configured identifier pattern (%s=%s) is invalid. Please use a different pattern."
              .formatted("camunda.security.id-validation-pattern", idRegex));
    }
  }
}

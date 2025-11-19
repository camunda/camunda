/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.processing.identity.initialize.AuthorizationConfigurer;
import io.camunda.zeebe.engine.processing.identity.initialize.IdentityInitializationException;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.VisibleForTesting;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CamundaSecurityProperties.class)
public class CamundaSecurityConfiguration {

  @VisibleForTesting
  public static final String UNPROTECTED_API_ENV_VAR =
      "CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI";

  @VisibleForTesting
  public static final String AUTHORIZATION_CHECKS_ENV_VAR =
      "CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED";

  private final CamundaSecurityProperties camundaSecurityProperties;

  @Autowired
  public CamundaSecurityConfiguration(final CamundaSecurityProperties camundaSecurityProperties) {
    this.camundaSecurityProperties = camundaSecurityProperties;
  }

  @Bean
  public InitializationConfiguration initializationConfiguration(
      final SecurityConfiguration securityConfiguration) {
    return securityConfiguration.getInitialization();
  }

  @Bean
  public MultiTenancyConfiguration multiTenancyConfiguration(
      final SecurityConfiguration securityConfiguration) {
    return securityConfiguration.getMultiTenancy();
  }

  @PostConstruct
  public void validate() {
    final var multiTenancyEnabled = camundaSecurityProperties.getMultiTenancy().isChecksEnabled();
    final var apiUnprotected = camundaSecurityProperties.getAuthentication().getUnprotectedApi();

    if (multiTenancyEnabled && apiUnprotected) {
      throw new IllegalStateException(
          "Multi-tenancy is enabled (%s=%b), but the API is unprotected (%s=%b). Please enable API protection if you want to make use of multi-tenancy."
              .formatted(
                  "camunda.security.multiTenancy.checksEnabled",
                  true,
                  "camunda.security.authentication.unprotected-api",
                  true));
    }

    final var idRegex = camundaSecurityProperties.getIdValidationPattern();
    try {
      final var idPattern = camundaSecurityProperties.getCompiledIdValidationPattern();
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

    validateInitializationConfig();
  }

  // actually initializing the entities will be done in IdentitySetupInitializer.
  // Validation is done here, only to be able to stop the application on error.
  private void validateInitializationConfig() {
    final AuthorizationConfigurer authorizationConfigurer =
        new AuthorizationConfigurer(camundaSecurityProperties.getCompiledIdValidationPattern());

    final Either<List<String>, List<AuthorizationRecord>> configuredAuthorizations =
        authorizationConfigurer.configureEntities(
            camundaSecurityProperties.getInitialization().getAuthorizations());

    // TODO: after adding more entity types, change this, so it accounts for all violations all
    //   together.
    configuredAuthorizations.ifLeft(
        (violations) -> {
          throw new IdentityInitializationException(
              "Cannot initialize configured entities: \n- %s"
                  .formatted(StringUtils.join(violations, "\n- ")));
        });
  }

  @ConfigurationProperties("camunda.security")
  public static final class CamundaSecurityProperties extends SecurityConfiguration {}
}

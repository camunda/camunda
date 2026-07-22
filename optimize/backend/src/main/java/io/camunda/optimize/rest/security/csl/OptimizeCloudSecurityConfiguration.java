/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.rest.security.oauth.CustomClaimValidator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import io.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * SPIKE (ADR-0036): CCSaaS-specific security wiring for the CSL adoption. Active only under the
 * cloud profile with CSL enabled. Brings back the SaaS org/cluster validation the legacy {@code
 * CCSaaSSecurityConfigurerAdapter} performed, using CSL's documented host extension points:
 *
 * <ul>
 *   <li>{@link OptimizeCloudOidcUserService} as the {@code OidcUserService} bean CSL wires into the
 *       webapp login (organization membership + role gate).
 *   <li>A {@link TokenValidatorFactory} override that appends a cluster-id claim validator to the
 *       bearer/public-API JWT validation (CSL ships timestamp/issuer/audience; this overrides its
 *       {@code @ConditionalOnMissingBean} default to add the SaaS check).
 * </ul>
 */
@Configuration
@Conditional(CCSaaSCondition.class)
@ConditionalOnProperty(name = "optimize.security.csl.enabled", havingValue = "true")
public class OptimizeCloudSecurityConfiguration {

  static final String CLUSTER_ID_CLAIM = "https://camunda.com/clusterId";

  @Bean
  public OidcUserService oidcUserService(final ConfigurationService configurationService) {
    final CloudAuthConfiguration cloud = cloudConfig(configurationService);
    return new OptimizeCloudOidcUserService(
        cloud.getOrganizationId(), OptimizeCloudOidcUserService.ALLOWED_ORG_ROLES);
  }

  @Bean
  public TokenValidatorFactory tokenValidatorFactory(
      final OidcProviderConfigurationPort oidcProviderConfigurationPort,
      final ConfigurationService configurationService) {
    final CloudAuthConfiguration cloud = cloudConfig(configurationService);
    final List<OAuth2TokenValidator<Jwt>> extraValidators = new ArrayList<>();
    if (StringUtils.isNotBlank(cloud.getClusterId())) {
      extraValidators.add(new CustomClaimValidator(CLUSTER_ID_CLAIM, cloud.getClusterId()));
    }
    return new TokenValidatorFactory(
        oidcProviderConfigurationPort.getOidcAuthenticationConfigurations(),
        OidcConfiguration.DEFAULT_CLOCK_SKEW,
        extraValidators);
  }

  private static CloudAuthConfiguration cloudConfig(
      final ConfigurationService configurationService) {
    return configurationService.getAuthConfiguration().getCloudAuthConfiguration();
  }
}

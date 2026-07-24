/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

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
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

/**
 * CCSaaS-specific security wiring for the CSL adoption. Active only under the cloud profile with
 * CSL enabled. Brings back the SaaS org/cluster validation the legacy {@code
 * CCSaaSSecurityConfigurerAdapter} performed, using CSL's documented host extension points. See <a
 * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>.
 *
 * <p>Follows OC's pattern (its {@code OidcOverrideBeansConfiguration}): the SaaS checks are two
 * {@link OAuth2TokenValidator}s composed into CSL's {@link TokenValidatorFactory}, and the same
 * factory feeds the {@code idTokenDecoderFactory}. So a single place gates <em>both</em> the bearer
 * public-API access token and the interactive webapp login id_token — no bespoke {@code
 * OidcUserService}.
 *
 * <ul>
 *   <li>{@link OptimizeCloudOrganizationValidator} — organization membership + allowed role
 *       (lenient on absence, so M2M tokens without the claim are not organization-gated).
 *   <li>{@link OptimizeCloudClusterValidator} — cluster id binding (lenient on absence).
 * </ul>
 *
 * <p>The Auth0 {@code audience} authorize-request parameter (so the login token is accepted by the
 * Accounts API) and the clusterId-derived servlet context path are supplied by configuration, not
 * code: the config compatibility bridge maps the legacy Optimize keys to {@code
 * camunda.security.*}, and CSL's authorization-request resolver injects the {@code audience}
 * parameter natively.
 */
@Configuration
@Conditional(CCSaaSCondition.class)
@ConditionalOnProperty(name = "optimize.security.csl.enabled", havingValue = "true")
public class OptimizeCloudSecurityConfiguration {

  @Bean
  public TokenValidatorFactory tokenValidatorFactory(
      final OidcProviderConfigurationPort oidcProviderConfigurationPort,
      final ConfigurationService configurationService) {
    final CloudAuthConfiguration cloud = cloudConfig(configurationService);
    final List<OAuth2TokenValidator<Jwt>> extraValidators = new ArrayList<>();
    if (StringUtils.isNotBlank(cloud.getOrganizationId())) {
      extraValidators.add(
          new OptimizeCloudOrganizationValidator(
              cloud.getOrganizationId(), OptimizeCloudOrganizationValidator.ALLOWED_ORG_ROLES));
    }
    if (StringUtils.isNotBlank(cloud.getClusterId())) {
      extraValidators.add(new OptimizeCloudClusterValidator(cloud.getClusterId()));
    }
    return new TokenValidatorFactory(
        oidcProviderConfigurationPort.getOidcAuthenticationConfigurations(),
        OidcConfiguration.DEFAULT_CLOCK_SKEW,
        extraValidators);
  }

  /**
   * Applies the same {@link TokenValidatorFactory} (and therefore the SaaS org/cluster validators)
   * to the interactive login id_token. Overrides CSL's {@code @ConditionalOnMissingBean} default so
   * the webapp login is org/cluster-gated, matching OC.
   */
  @Bean
  public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(
      final TokenValidatorFactory tokenValidatorFactory) {
    final OidcIdTokenDecoderFactory decoderFactory = new OidcIdTokenDecoderFactory();
    decoderFactory.setJwtValidatorFactory(tokenValidatorFactory::createTokenValidator);
    return decoderFactory;
  }

  private static CloudAuthConfiguration cloudConfig(
      final ConfigurationService configurationService) {
    return configurationService.getAuthConfiguration().getCloudAuthConfiguration();
  }
}

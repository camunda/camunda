/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.spring.config.SecurityConfiguration;
import io.camunda.auth.spring.converter.OidcUserAuthenticationConverter;
import io.camunda.auth.spring.converter.TokenClaimsConverter;
import io.camunda.auth.spring.observability.CustomDefaultClientRequestObservationConvention;
import io.camunda.auth.spring.oidc.AssertionJwkProvider;
import io.camunda.auth.spring.oidc.ClientAwareOAuth2AuthorizationRequestResolver;
import io.camunda.auth.spring.oidc.ClientRegistrationFactory;
import io.camunda.auth.spring.oidc.JWSKeySelectorFactory;
import io.camunda.auth.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.auth.spring.oidc.OidcAuthenticationConfigurationRepository;
import io.camunda.auth.spring.oidc.OidcTokenEndpointCustomizer;
import io.camunda.auth.spring.oidc.TokenValidatorFactory;
import io.micrometer.common.KeyValues;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.web.client.RestClient;

/**
 * Auto-configuration for OIDC infrastructure beans. Gated on {@code camunda.auth.method=oidc}
 * (default). Provides client registrations, JWT decoders, token validators, and related OIDC
 * components.
 */
@AutoConfiguration(after = CamundaAuthAutoConfiguration.class)
@ConditionalOnProperty(name = "camunda.auth.method", havingValue = "oidc")
public class CamundaOidcAutoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaOidcAutoConfiguration.class);

  @Bean
  @ConfigurationProperties("camunda.security")
  public SecurityConfiguration authSecurityConfiguration() {
    return new SecurityConfiguration();
  }

  @Bean
  @ConditionalOnMissingBean
  public OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository(
      final SecurityConfiguration securityConfiguration) {
    return new OidcAuthenticationConfigurationRepository(securityConfiguration);
  }

  @Bean
  @ConditionalOnMissingBean
  public ClientRegistrationRepository clientRegistrationRepository(
      final OidcAuthenticationConfigurationRepository oidcConfigRepository) {
    final var configs = oidcConfigRepository.getOidcAuthenticationConfigurations();
    final List<ClientRegistration> registrations =
        configs.entrySet().stream()
            .map(e -> ClientRegistrationFactory.createClientRegistration(e.getKey(), e.getValue()))
            .toList();

    if (registrations.isEmpty()) {
      throw new IllegalStateException(
          "No OIDC client registrations configured. "
              + "Please configure camunda.security.authentication.oidc or "
              + "camunda.security.authentication.providers.oidc");
    }

    return new InMemoryClientRegistrationRepository(registrations);
  }

  @Bean
  @ConditionalOnMissingBean
  public TokenValidatorFactory tokenValidatorFactory(
      final OidcAuthenticationConfigurationRepository oidcConfigRepository) {
    return new TokenValidatorFactory(oidcConfigRepository);
  }

  @Bean
  @ConditionalOnMissingBean
  public JWSKeySelectorFactory jwsKeySelectorFactory() {
    return new JWSKeySelectorFactory();
  }

  @Bean
  @ConditionalOnMissingBean
  public OidcAccessTokenDecoderFactory oidcAccessTokenDecoderFactory(
      final JWSKeySelectorFactory jwsKeySelectorFactory,
      final TokenValidatorFactory tokenValidatorFactory) {
    return new OidcAccessTokenDecoderFactory(jwsKeySelectorFactory, tokenValidatorFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtDecoder jwtDecoder(
      final OidcAccessTokenDecoderFactory decoderFactory,
      final ClientRegistrationRepository clientRegistrationRepository,
      final OidcAuthenticationConfigurationRepository oidcConfigRepository) {
    return new SupplierJwtDecoder(
        () -> {
          if (clientRegistrationRepository instanceof InMemoryClientRegistrationRepository inMem) {
            final List<ClientRegistration> registrations = new java.util.ArrayList<>();
            inMem.iterator().forEachRemaining(registrations::add);

            final Map<String, List<String>> additionalJwkSetUrisByIssuer =
                buildAdditionalJwkSetUrisByIssuer(oidcConfigRepository, registrations);

            if (registrations.size() == 1) {
              final var reg = registrations.getFirst();
              final var issuerUri = reg.getProviderDetails().getIssuerUri();
              final var additionalUris = additionalJwkSetUrisByIssuer.get(issuerUri);
              return decoderFactory.createAccessTokenDecoder(reg, additionalUris);
            } else {
              return decoderFactory.createIssuerAwareAccessTokenDecoder(
                  registrations, additionalJwkSetUrisByIssuer);
            }
          }
          throw new IllegalStateException(
              "Cannot auto-configure JwtDecoder: ClientRegistrationRepository is not "
                  + "InMemoryClientRegistrationRepository");
        });
  }

  @Bean
  @ConditionalOnMissingBean
  public OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository() {
    return new HttpSessionOAuth2AuthorizedClientRepository();
  }

  @Bean
  @ConditionalOnMissingBean
  public AssertionJwkProvider assertionJwkProvider(
      final OidcAuthenticationConfigurationRepository oidcConfigRepository) {
    return new AssertionJwkProvider(oidcConfigRepository);
  }

  @Bean
  @ConditionalOnMissingBean
  public OidcTokenEndpointCustomizer oidcTokenEndpointCustomizer(
      final OidcAuthenticationConfigurationRepository oidcConfigRepository,
      final AssertionJwkProvider assertionJwkProvider) {
    final var observationConvention =
        new CustomDefaultClientRequestObservationConvention(
            "camunda.auth.oidc.token", KeyValues.of("component", "auth"));
    final var restClient =
        RestClient.builder().observationConvention(observationConvention).build();
    return new OidcTokenEndpointCustomizer(oidcConfigRepository, assertionJwkProvider, restClient);
  }

  @Bean
  @ConditionalOnMissingBean
  public OAuth2AuthorizedClientManager authorizedClientManager(
      final ClientRegistrationRepository clientRegistrationRepository) {
    final var clientService =
        new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    final var manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository, clientService);

    final OAuth2AuthorizedClientProvider clientProvider =
        OAuth2AuthorizedClientProviderBuilder.builder().refreshToken().clientCredentials().build();
    manager.setAuthorizedClientProvider(clientProvider);
    return manager;
  }

  @Bean
  @ConditionalOnMissingBean
  public OidcUserService oidcUserService() {
    return new OidcUserService();
  }

  @Bean
  @ConditionalOnMissingBean
  public OidcUserAuthenticationConverter oidcUserAuthenticationConverter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OidcAccessTokenDecoderFactory accessTokenDecoderFactory,
      final TokenClaimsConverter tokenClaimsConverter,
      final HttpServletRequest request,
      final OidcAuthenticationConfigurationRepository oidcConfigRepository) {
    final Map<String, List<String>> additionalJwkSetUrisByIssuer =
        oidcConfigRepository.getOidcAuthenticationConfigurations().entrySet().stream()
            .filter(e -> e.getValue().getAdditionalJwkSetUris() != null)
            .collect(
                Collectors.toMap(
                    e -> e.getValue().getIssuerUri(),
                    e -> e.getValue().getAdditionalJwkSetUris(),
                    (a, b) -> a));

    return new OidcUserAuthenticationConverter(
        authorizedClientRepository,
        accessTokenDecoderFactory,
        tokenClaimsConverter,
        request,
        additionalJwkSetUrisByIssuer);
  }

  @Bean
  @ConditionalOnMissingBean
  public OAuth2AuthorizationRequestResolver clientAwareOAuth2AuthorizationRequestResolver(
      final ClientRegistrationRepository clientRegistrationRepository,
      final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
    return new ClientAwareOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository, oidcProviderRepository);
  }

  private Map<String, List<String>> buildAdditionalJwkSetUrisByIssuer(
      final OidcAuthenticationConfigurationRepository oidcConfigRepository,
      final List<ClientRegistration> registrations) {
    return registrations.stream()
        .filter(r -> r.getProviderDetails().getIssuerUri() != null)
        .collect(
            Collectors.toMap(
                r -> r.getProviderDetails().getIssuerUri(),
                r -> {
                  final var config =
                      oidcConfigRepository
                          .getOidcAuthenticationConfigurations()
                          .get(r.getRegistrationId());
                  return config != null && config.getAdditionalJwkSetUris() != null
                      ? config.getAdditionalJwkSetUris()
                      : List.of();
                },
                (a, b) -> a));
  }
}

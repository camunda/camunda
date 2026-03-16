/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.autoconfigure;

import static java.util.stream.Collectors.toMap;

import io.camunda.gatekeeper.auth.OidcPrincipalLoader;
import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.config.OidcConfig;
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.gatekeeper.spi.CamundaAuthenticationConverter;
import io.camunda.gatekeeper.spi.MembershipResolver;
import io.camunda.gatekeeper.spi.OidcConfigurationProvider;
import io.camunda.gatekeeper.spring.condition.ConditionalOnAuthenticationMethod;
import io.camunda.gatekeeper.spring.config.GatekeeperProperties;
import io.camunda.gatekeeper.spring.converter.OidcTokenAuthenticationConverter;
import io.camunda.gatekeeper.spring.converter.OidcUserAuthenticationConverter;
import io.camunda.gatekeeper.spring.converter.TokenClaimsConverter;
import io.camunda.gatekeeper.spring.oidc.AssertionJwkProvider;
import io.camunda.gatekeeper.spring.oidc.ClientRegistrationFactory;
import io.camunda.gatekeeper.spring.oidc.JWSKeySelectorFactory;
import io.camunda.gatekeeper.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.gatekeeper.spring.oidc.OidcAuthenticationConfigurationRepository;
import io.camunda.gatekeeper.spring.oidc.OidcTokenEndpointCustomizer;
import io.camunda.gatekeeper.spring.oidc.TokenValidatorFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

/**
 * Auto-configuration for OIDC-based authentication. Only active when the authentication method is
 * set to OIDC.
 */
@AutoConfiguration(after = GatekeeperAuthAutoConfiguration.class)
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public final class GatekeeperOidcAutoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(GatekeeperOidcAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public OidcConfigurationProvider propertiesOidcConfigurationProvider(
      final GatekeeperProperties properties) {
    final var providersMap = properties.getAuthentication().getProviders().getOidc();
    if (providersMap == null || providersMap.isEmpty()) {
      return new OidcConfigurationProvider() {
        @Override
        public List<OidcConfig> getConfigurations() {
          return List.of();
        }

        @Override
        public java.util.Optional<OidcConfig> getConfiguration(final String registrationId) {
          return java.util.Optional.empty();
        }
      };
    }
    final List<OidcConfig> configs =
        providersMap.entrySet().stream()
            .map(
                entry -> {
                  final var oidcProps = entry.getValue();
                  final var registrationId =
                      oidcProps.getRegistrationId() != null
                          ? oidcProps.getRegistrationId()
                          : entry.getKey();
                  return oidcProps.toOidcConfig(registrationId);
                })
            .toList();
    return new OidcConfigurationProvider() {
      @Override
      public List<OidcConfig> getConfigurations() {
        return configs;
      }

      @Override
      public java.util.Optional<OidcConfig> getConfiguration(final String registrationId) {
        return configs.stream().filter(c -> registrationId.equals(c.registrationId())).findFirst();
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean
  public OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository(
      final AuthenticationConfig authenticationConfig,
      final Optional<OidcConfigurationProvider> oidcConfigurationProvider) {
    return new OidcAuthenticationConfigurationRepository(
        authenticationConfig, oidcConfigurationProvider.orElse(null));
  }

  @Bean
  @ConditionalOnMissingBean
  public TokenClaimsConverter tokenClaimsConverter(
      final AuthenticationConfig authenticationConfig,
      final MembershipResolver membershipResolver) {
    final var oidcConfig = authenticationConfig.oidc();
    final var oidcPrincipalLoader =
        new OidcPrincipalLoader(oidcConfig.usernameClaim(), oidcConfig.clientIdClaim());
    return new TokenClaimsConverter(
        oidcPrincipalLoader,
        oidcConfig.usernameClaim(),
        oidcConfig.clientIdClaim(),
        oidcConfig.preferUsernameClaim(),
        membershipResolver);
  }

  @Bean
  @ConditionalOnMissingBean(OidcTokenAuthenticationConverter.class)
  public CamundaAuthenticationConverter<Authentication> oidcTokenAuthenticationConverter(
      final TokenClaimsConverter tokenClaimsConverter) {
    return new OidcTokenAuthenticationConverter(tokenClaimsConverter);
  }

  @Bean
  @ConditionalOnMissingBean(OidcUserAuthenticationConverter.class)
  public CamundaAuthenticationConverter<Authentication> oidcUserAuthenticationConverter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OidcAccessTokenDecoderFactory oidcAccessTokenDecoderFactory,
      final TokenClaimsConverter tokenClaimsConverter,
      final HttpServletRequest request,
      final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
    return new OidcUserAuthenticationConverter(
        authorizedClientRepository,
        oidcAccessTokenDecoderFactory,
        tokenClaimsConverter,
        request,
        buildAdditionalJwkSetUrisByIssuer(oidcProviderRepository));
  }

  @Bean
  @ConditionalOnMissingBean
  public ClientRegistrationRepository clientRegistrationRepository(
      final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
    final var clientRegistrations =
        oidcProviderRepository.getOidcConfigurations().entrySet().stream()
            .map(e -> createClientRegistration(e.getKey(), e.getValue()))
            .toList();
    return new InMemoryClientRegistrationRepository(clientRegistrations);
  }

  @Bean
  @ConditionalOnMissingBean
  public JWSKeySelectorFactory jwsKeySelectorFactory() {
    return new JWSKeySelectorFactory();
  }

  @Bean
  @ConditionalOnMissingBean
  public TokenValidatorFactory tokenValidatorFactory(
      final AuthenticationConfig authenticationConfig,
      final OidcAuthenticationConfigurationRepository oidcConfigRepository) {
    return new TokenValidatorFactory(authenticationConfig.oidc(), oidcConfigRepository);
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
      final OidcAccessTokenDecoderFactory oidcAccessTokenDecoderFactory,
      final ClientRegistrationRepository clientRegistrationRepository,
      final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
    final var clientRegistrations = extractClientRegistrations(clientRegistrationRepository);
    final var additionalJwkSetUrisByIssuer =
        buildAdditionalJwkSetUrisByIssuer(oidcProviderRepository);

    if (clientRegistrations.size() == 1) {
      final var clientRegistration = clientRegistrations.getFirst();
      final var additionalUris =
          additionalJwkSetUrisByIssuer.get(clientRegistration.getProviderDetails().getIssuerUri());
      LOG.info(
          "Create Access Token JWT Decoder for OIDC Provider: {}",
          clientRegistration.getRegistrationId());
      return new SupplierJwtDecoder(
          () ->
              oidcAccessTokenDecoderFactory.createAccessTokenDecoder(
                  clientRegistration, additionalUris));
    } else {
      LOG.info(
          "Create Issuer Aware JWT Decoder for multiple OIDC Providers: [{}]",
          clientRegistrations.stream()
              .map(ClientRegistration::getRegistrationId)
              .collect(Collectors.joining(", ")));
      return new SupplierJwtDecoder(
          () ->
              oidcAccessTokenDecoderFactory.createIssuerAwareAccessTokenDecoder(
                  clientRegistrations, additionalJwkSetUrisByIssuer));
    }
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
  public OAuth2AuthorizedClientManager authorizedClientManager(
      final ClientRegistrationRepository clientRegistrationRepository,
      final OAuth2AuthorizedClientRepository authorizedClientRepository) {
    final var manager =
        new DefaultOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientRepository);
    final var provider =
        OAuth2AuthorizedClientProviderBuilder.builder()
            .authorizationCode()
            .refreshToken()
            .clientCredentials()
            .build();
    manager.setAuthorizedClientProvider(provider);
    return manager;
  }

  @Bean
  @ConditionalOnMissingBean
  public OidcTokenEndpointCustomizer oidcTokenEndpointCustomizer(
      final OidcAuthenticationConfigurationRepository oidcConfigRepository,
      final AssertionJwkProvider assertionJwkProvider) {
    return new OidcTokenEndpointCustomizer(oidcConfigRepository, assertionJwkProvider);
  }

  private static ClientRegistration createClientRegistration(
      final String registrationId, final OidcConfig config) {
    try {
      return ClientRegistrationFactory.createClientRegistration(registrationId, config);
    } catch (final Exception e) {
      throw new IllegalStateException(
          "Unable to connect to the Identity Provider endpoint '"
              + config.issuerUri()
              + "'. Double check that it is configured correctly, and if the problem persists, "
              + "contact your external Identity provider.",
          e);
    }
  }

  private static List<ClientRegistration> extractClientRegistrations(
      final ClientRegistrationRepository clientRegistrationRepository) {
    if (!(clientRegistrationRepository instanceof final Iterable<?> iterableRepository)) {
      throw new IllegalStateException(
          "Unable to extract OAuth 2.0 client registrations as clientRegistrationRepository %s is not iterable"
              .formatted(clientRegistrationRepository.getClass()));
    }

    return StreamSupport.stream(iterableRepository.spliterator(), false)
        .filter(ClientRegistration.class::isInstance)
        .map(ClientRegistration.class::cast)
        .toList();
  }

  private static Map<String, List<String>> buildAdditionalJwkSetUrisByIssuer(
      final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
    return oidcProviderRepository.getOidcConfigurations().values().stream()
        .filter(
            config ->
                config.issuerUri() != null
                    && config.additionalJwkSetUris() != null
                    && !config.additionalJwkSetUris().isEmpty())
        .collect(
            toMap(
                OidcConfig::issuerUri,
                config -> List.copyOf(config.additionalJwkSetUris()),
                (a, b) -> {
                  if (!a.equals(b)) {
                    throw new IllegalStateException(
                        "Multiple OIDC providers share the same issuer URI with different"
                            + " additional JWKS URIs. Ensure each issuer has a consistent"
                            + " configuration.");
                  }
                  return a;
                }));
  }
}

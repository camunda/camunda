/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import static com.nimbusds.jose.JOSEObjectType.JWT;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Factory for creating {@link JwtDecoder} instances tailored for decoding access tokens issued by
 * OpenID Connect (OIDC) Identity Providers. Supports both single-issuer and multi-issuer setups.
 */
public class OidcAccessTokenDecoderFactory {

  static final JOSEObjectType AT_JWT = new JOSEObjectType("at+jwt");
  private static final Logger LOG = LoggerFactory.getLogger(OidcAccessTokenDecoderFactory.class);
  private static final String ERROR_MISSING_ISSUER =
      "The following OIDC Providers are missing 'issuerUri': %s";
  private static final String ERROR_MISSING_JWK =
      "OIDC Provider '%s' is missing a valid 'jwk-set-uri'. Issuer URI: %s";
  private final JWSKeySelectorFactory jwsKeySelectorFactory;
  private final TokenValidatorFactory tokenValidatorFactory;

  public OidcAccessTokenDecoderFactory(
      final JWSKeySelectorFactory jwsKeySelectorFactory,
      final TokenValidatorFactory tokenValidatorFactory) {
    this.jwsKeySelectorFactory = jwsKeySelectorFactory;
    this.tokenValidatorFactory = tokenValidatorFactory;
  }

  public JwtDecoder createIssuerAwareAccessTokenDecoder(
      final List<ClientRegistration> clientRegistrations) {
    return createIssuerAwareAccessTokenDecoder(clientRegistrations, Collections.emptyMap());
  }

  public JwtDecoder createIssuerAwareAccessTokenDecoder(
      final List<ClientRegistration> clientRegistrations,
      final Map<String, List<String>> additionalJwkSetUrisByIssuer) {
    LOG.debug(
        "Creating an Issuer Aware JwtDecoder for multiple OIDC Providers: {}",
        clientRegistrations.size());
    validateClientRegistrationsHaveIssuer(clientRegistrations);
    final var jwtProcessor =
        createIssuerAwareJwtProcessor(clientRegistrations, additionalJwkSetUrisByIssuer);
    final var jwtValidator = createIssuerAwareJwtValidator(clientRegistrations);
    return createNimbusJwtDecoder(jwtProcessor, jwtValidator);
  }

  protected void validateClientRegistrationsHaveIssuer(
      final List<ClientRegistration> clientRegistrations) {
    final var invalidProviders =
        clientRegistrations.stream()
            .filter(
                r -> {
                  final var issuerUri = r.getProviderDetails().getIssuerUri();
                  return issuerUri == null || issuerUri.isBlank();
                })
            .map(ClientRegistration::getRegistrationId)
            .toList();

    if (!invalidProviders.isEmpty()) {
      throw new IllegalArgumentException(
          ERROR_MISSING_ISSUER.formatted(String.join(", ", invalidProviders)));
    }
  }

  public JwtDecoder createAccessTokenDecoder(final ClientRegistration clientRegistration) {
    return createAccessTokenDecoder(clientRegistration, null);
  }

  public JwtDecoder createAccessTokenDecoder(
      final ClientRegistration clientRegistration, final List<String> additionalJwkSetUris) {
    LOG.debug("Creating JwtDecoder for OIDC Provider {}", clientRegistration.getRegistrationId());
    LOG.debug("Additional JWK Set URIs: {}", additionalJwkSetUris);
    final var jwtProcessor = createJwtProcessor(clientRegistration, additionalJwkSetUris);
    final var jwtValidator = createJwtValidator(clientRegistration);
    return createNimbusJwtDecoder(jwtProcessor, jwtValidator);
  }

  protected String getJWKSetUri(final ClientRegistration clientRegistration) {
    final var providerDetails = clientRegistration.getProviderDetails();
    final var jwkSetUri = providerDetails.getJwkSetUri();
    if (jwkSetUri == null || jwkSetUri.isBlank()) {
      throw new IllegalArgumentException(
          ERROR_MISSING_JWK.formatted(
              clientRegistration.getRegistrationId(), providerDetails.getIssuerUri()));
    }
    return jwkSetUri;
  }

  protected NimbusJwtDecoder createNimbusJwtDecoder(
      final JWTProcessor<SecurityContext> jwtProcessor,
      final OAuth2TokenValidator<Jwt> tokenValidator) {
    final var decoder = new NimbusJwtDecoder(jwtProcessor);
    final JwtTypeValidator jwtTypeValidator =
        new JwtTypeValidator(List.of(JOSEObjectType.JWT.getType(), AT_JWT.getType()));
    jwtTypeValidator.setAllowEmpty(true);
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(jwtTypeValidator, tokenValidator));
    return decoder;
  }

  protected ConfigurableJWTProcessor<SecurityContext> createIssuerAwareJwtProcessor(
      final List<ClientRegistration> clientRegistrations,
      final Map<String, List<String>> additionalJwkSetUrisByIssuer) {
    final var jwsKeySelector =
        new IssuerAwareJWSKeySelector(
            clientRegistrations, jwsKeySelectorFactory, additionalJwkSetUrisByIssuer);
    return createAndCustomizeJwtProcessor(
        processor -> processor.setJWTClaimsSetAwareJWSKeySelector(jwsKeySelector));
  }

  protected ConfigurableJWTProcessor<SecurityContext> createJwtProcessor(
      final ClientRegistration clientRegistration, final List<String> additionalJwkSetUris) {
    final var jwkSetUri = getJWKSetUri(clientRegistration);
    final var jwsKeySelector =
        jwsKeySelectorFactory.createJWSKeySelector(jwkSetUri, additionalJwkSetUris);
    return createAndCustomizeJwtProcessor(processor -> processor.setJWSKeySelector(jwsKeySelector));
  }

  protected ConfigurableJWTProcessor<SecurityContext> createAndCustomizeJwtProcessor(
      final Consumer<ConfigurableJWTProcessor<SecurityContext>> customizer) {
    final var jwtProcessor = new DefaultJWTProcessor<>();
    final var jwsTypeVerifier = createJOSEObjectTypeVerifier();
    jwtProcessor.setJWSTypeVerifier(jwsTypeVerifier);
    customizer.accept(jwtProcessor);
    return jwtProcessor;
  }

  protected OAuth2TokenValidator<Jwt> createIssuerAwareJwtValidator(
      final List<ClientRegistration> clientRegistrations) {
    return new IssuerAwareTokenValidator(clientRegistrations, tokenValidatorFactory);
  }

  protected OAuth2TokenValidator<Jwt> createJwtValidator(
      final ClientRegistration clientRegistration) {
    return tokenValidatorFactory.createTokenValidator(clientRegistration);
  }

  protected JOSEObjectTypeVerifier<SecurityContext> createJOSEObjectTypeVerifier() {
    return new DefaultJOSEObjectTypeVerifier<>(JWT, AT_JWT, null);
  }
}

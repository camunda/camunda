/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static com.nimbusds.jose.JOSEObjectType.JWT;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import java.util.List;
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
 * OpenID Connect (OIDC) Identity Providers.
 *
 * <p>This factory supports both single-issuer and multi-issuer (issuer-aware) setups, and enforces
 * proper configuration of issuer URIs and JWK Set URIs.
 */
public class OidcAccessTokenDecoderFactory {

  // We explicitly support the "at+jwt" JWT 'typ' header defined in
  // https://datatracker.ietf.org/doc/html/rfc9068#name-header
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

  /**
   * Creates a {@link JwtDecoder} that supports multiple OIDC Providers by resolving issuer-specific
   * keys and validation logic at runtime.
   *
   * @param clientRegistrations the list of client registrations to support
   * @return a {@link JwtDecoder} capable of handling multiple issuers
   * @throws IllegalArgumentException if any registration is missing an issuer URI
   */
  public JwtDecoder createIssuerAwareAccessTokenDecoder(
      final List<ClientRegistration> clientRegistrations) {
    LOG.debug(
        "Creating an Issuer Aware JwtDecoder for multiple OIDC Providers: {}",
        clientRegistrations.size());
    validateClientRegistrationsHaveIssuer(clientRegistrations);
    final var jwtProcessor = createIssuerAwareJwtProcessor(clientRegistrations);
    final var jwtValidator = createIssuerAwareJwtValidator(clientRegistrations);
    return createNimbusJwtDecoder(jwtProcessor, jwtValidator);
  }

  /**
   * Validates that all provided {@link ClientRegistration} entries have a configured issuer URI.
   *
   * @param clientRegistrations the list of client registrations to validate
   * @throws IllegalArgumentException if any registration is missing a valid issuer URI
   */
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

  /**
   * Creates a {@link JwtDecoder} for a single OIDC Identity Provider.
   *
   * @param clientRegistration the client registration to use
   * @return a {@link JwtDecoder} for that client
   * @throws IllegalArgumentException if the registration is missing a JWK Set URI
   */
  public JwtDecoder createAccessTokenDecoder(final ClientRegistration clientRegistration) {
    LOG.debug("Creating JwtDecoder for OIDC Provider {}", clientRegistration.getRegistrationId());
    final var jwtProcessor = createJwtProcessor(clientRegistration);
    final var jwtValidator = createJwtValidator(clientRegistration);
    return createNimbusJwtDecoder(jwtProcessor, jwtValidator);
  }

  /**
   * Extracts the JWK Set URI from the given client registration.
   *
   * @param clientRegistration the client registration
   * @return the JWK Set URI
   * @throws IllegalArgumentException if the URI is missing or blank
   */
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

  /**
   * Creates a {@link NimbusJwtDecoder} using the given processor and validator.
   *
   * @param jwtProcessor the JWT processor
   * @param tokenValidator the token validator
   * @return a configured {@link NimbusJwtDecoder}
   */
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

  /**
   * Creates a {@link ConfigurableJWTProcessor} that is aware of multiple issuers.
   *
   * @param clientRegistrations the list of client registrations
   * @return a configured JWT processor
   */
  protected ConfigurableJWTProcessor<SecurityContext> createIssuerAwareJwtProcessor(
      final List<ClientRegistration> clientRegistrations) {
    final var jwsKeySelector =
        new IssuerAwareJWSKeySelector(clientRegistrations, jwsKeySelectorFactory);
    return createAndCustomizeJwtProcessor(
        processor -> processor.setJWTClaimsSetAwareJWSKeySelector(jwsKeySelector));
  }

  /**
   * Creates a {@link ConfigurableJWTProcessor} for a single issuer using its JWK Set URI.
   *
   * @param clientRegistration the client registration
   * @return a configured JWT processor
   */
  protected ConfigurableJWTProcessor<SecurityContext> createJwtProcessor(
      final ClientRegistration clientRegistration) {
    final var jwkSetUri = getJWKSetUri(clientRegistration);
    final var jwsKeySelector = jwsKeySelectorFactory.createJWSKeySelector(jwkSetUri);
    return createAndCustomizeJwtProcessor(processor -> processor.setJWSKeySelector(jwsKeySelector));
  }

  /**
   * Creates and customizes a {@link ConfigurableJWTProcessor} with a standard JOSE header type
   * verifier.
   *
   * @param customizer a lambda for applying processor-specific customization
   * @return a configured JWT processor
   */
  protected ConfigurableJWTProcessor<SecurityContext> createAndCustomizeJwtProcessor(
      final Consumer<ConfigurableJWTProcessor<SecurityContext>> customizer) {
    final var jwtProcessor = new DefaultJWTProcessor<>();
    final var jwsTypeVerifier = createJOSEObjectTypeVerifier();
    jwtProcessor.setJWSTypeVerifier(jwsTypeVerifier);
    customizer.accept(jwtProcessor);
    return jwtProcessor;
  }

  /**
   * Creates a {@link Jwt} validator that supports multiple issuers.
   *
   * @param clientRegistrations the list of client registrations
   * @return a token validator aware of multiple issuers
   */
  protected OAuth2TokenValidator<Jwt> createIssuerAwareJwtValidator(
      final List<ClientRegistration> clientRegistrations) {
    return new IssuerAwareTokenValidator(clientRegistrations, tokenValidatorFactory);
  }

  /**
   * Creates a token validator for a single OIDC Identity Provider.
   *
   * @param clientRegistration the client registration
   * @return a token validator for the issuer
   */
  protected OAuth2TokenValidator<Jwt> createJwtValidator(
      final ClientRegistration clientRegistration) {
    return tokenValidatorFactory.createTokenValidator(clientRegistration);
  }

  /**
   * Creates a {@link JOSEObjectTypeVerifier} that accepts standard JWT types.
   *
   * @return a JOSE object type verifier
   */
  protected JOSEObjectTypeVerifier<SecurityContext> createJOSEObjectTypeVerifier() {
    return new DefaultJOSEObjectTypeVerifier<>(JWT, AT_JWT, null);
  }
}

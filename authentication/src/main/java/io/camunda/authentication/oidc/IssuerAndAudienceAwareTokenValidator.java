/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oidc;

import io.camunda.authentication.config.OidcAuthenticationConfigurationRepository;
import io.camunda.authentication.config.TokenValidatorFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Replaces CSL's {@code IssuerAwareTokenValidator} (which matches purely by {@code iss} and serves
 * the FIRST registration that matches). When multiple {@link ClientRegistration}s share the same
 * issuer URI — e.g. two clients on the same Keycloak realm emitting distinct {@code aud} claims —
 * this validator disambiguates by intersecting the token's {@code aud} claim with each
 * registration's declared {@code audiences}.
 *
 * <p>Per-issuer behaviour:
 *
 * <ul>
 *   <li>No registration matches {@code iss} → {@code invalid_token}.
 *   <li>Exactly one registration matches {@code iss} → use that registration's validator (preserves
 *       CSL's single-registration semantics).
 *   <li>Multiple registrations match {@code iss} → require the token to carry an {@code aud} claim
 *       that intersects exactly one registration's declared audiences. Empty/missing {@code aud},
 *       or no intersecting registration → {@code invalid_token}.
 * </ul>
 *
 * <p>Validators are cached by registration id (not by issuer URI as CSL does) so two same-iss
 * registrations get distinct cached validators.
 */
public class IssuerAndAudienceAwareTokenValidator implements OAuth2TokenValidator<Jwt> {

  private static final Logger LOG =
      LoggerFactory.getLogger(IssuerAndAudienceAwareTokenValidator.class);
  private static final String CLAIM_ISSUER = "iss";

  private final List<ClientRegistration> clientRegistrations;
  private final TokenValidatorFactory tokenValidatorFactory;
  private final OidcAuthenticationConfigurationRepository oidcConfigRepository;
  private final Map<String, OAuth2TokenValidator<Jwt>> validators;

  public IssuerAndAudienceAwareTokenValidator(
      final List<ClientRegistration> clientRegistrations,
      final TokenValidatorFactory tokenValidatorFactory,
      final OidcAuthenticationConfigurationRepository oidcConfigRepository) {
    this.clientRegistrations = clientRegistrations;
    this.tokenValidatorFactory = tokenValidatorFactory;
    this.oidcConfigRepository = oidcConfigRepository;
    validators = new ConcurrentHashMap<>();
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    final String issuer = token.getClaimAsString(CLAIM_ISSUER);
    if (issuer == null || issuer.isBlank()) {
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN, "Token is missing or has a blank 'iss' claim", null));
    }

    final List<ClientRegistration> issMatches =
        clientRegistrations.stream()
            .filter(r -> issuer.equals(r.getProviderDetails().getIssuerUri()))
            .toList();
    if (issMatches.isEmpty()) {
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN,
              "Token issuer '" + issuer + "' is not trusted",
              null));
    }

    final ClientRegistration match;
    if (issMatches.size() == 1) {
      match = issMatches.getFirst();
    } else {
      final List<String> tokenAud = token.getAudience();
      if (tokenAud == null || tokenAud.isEmpty()) {
        LOG.debug(
            "Multiple registrations share iss '{}' but token has no 'aud' claim to disambiguate",
            issuer);
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "Issuer '"
                    + issuer
                    + "' is shared across registrations; token requires an 'aud' claim to"
                    + " disambiguate",
                null));
      }
      final ClientRegistration audMatch =
          issMatches.stream()
              .filter(r -> audiencesOf(r).stream().anyMatch(tokenAud::contains))
              .findFirst()
              .orElse(null);
      if (audMatch == null) {
        LOG.debug(
            "No registration with iss '{}' declares an audience that intersects token aud {}",
            issuer,
            tokenAud);
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "No registration with iss '"
                    + issuer
                    + "' declares an audience that intersects token aud "
                    + tokenAud,
                null));
      }
      match = audMatch;
    }

    return validators
        .computeIfAbsent(
            match.getRegistrationId(), k -> tokenValidatorFactory.createTokenValidator(match))
        .validate(token);
  }

  private Set<String> audiencesOf(final ClientRegistration registration) {
    final var config =
        oidcConfigRepository
            .getOidcAuthenticationConfigurations()
            .get(registration.getRegistrationId());
    if (config == null || config.getAudiences() == null) {
      return Set.of();
    }
    return config.getAudiences();
  }
}

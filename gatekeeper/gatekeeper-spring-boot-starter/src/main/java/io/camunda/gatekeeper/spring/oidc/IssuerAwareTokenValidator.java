/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.oidc;

import java.util.List;
import java.util.Map;
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
 * A {@link OAuth2TokenValidator} implementation that validates a {@link Jwt} based on the token's
 * issuer claim ({@code iss}). This validator ensures that the token was issued by a trusted issuer
 * registered via the list of {@link ClientRegistration}s.
 *
 * <p>If a trusted client registration is found, a corresponding validator is obtained (or created)
 * via a {@link TokenValidatorFactory}, and used to validate the token.
 *
 * <p>This allows support for multiple issuers in multi-tenant OIDC setups.
 */
public final class IssuerAwareTokenValidator implements OAuth2TokenValidator<Jwt> {

  private static final Logger LOG = LoggerFactory.getLogger(IssuerAwareTokenValidator.class);
  private static final String CLAIM_ISSUER = "iss";
  private static final String OAUTH2_ERROR_DESCRIPTION = "Token issuer '%s' is not trusted";

  private final List<ClientRegistration> clientRegistrations;
  private final TokenValidatorFactory tokenValidatorFactory;
  private final Map<String, OAuth2TokenValidator<Jwt>> validators;

  public IssuerAwareTokenValidator(
      final List<ClientRegistration> clientRegistrations,
      final TokenValidatorFactory tokenValidatorFactory) {
    this.clientRegistrations = clientRegistrations;
    this.tokenValidatorFactory = tokenValidatorFactory;
    validators = new ConcurrentHashMap<>();
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    final var issuer = token.getClaimAsString(CLAIM_ISSUER);
    final var registration = getClientRegistrationByIssuer(issuer);

    if (registration == null) {
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN, OAUTH2_ERROR_DESCRIPTION.formatted(issuer), null));
    }

    return getOrCreateTokenValidator(registration).validate(token);
  }

  private OAuth2TokenValidator<Jwt> getOrCreateTokenValidator(
      final ClientRegistration clientRegistration) {
    final var issuer = clientRegistration.getProviderDetails().getIssuerUri();
    return validators.computeIfAbsent(
        issuer, k -> tokenValidatorFactory.createTokenValidator(clientRegistration));
  }

  private ClientRegistration getClientRegistrationByIssuer(final String issuer) {
    return clientRegistrations.stream()
        .filter(c -> issuer.equals(c.getProviderDetails().getIssuerUri()))
        .findFirst()
        .orElseGet(
            () -> {
              LOG.debug("No matching client registration found for issuer uri {}", issuer);
              return null;
            });
  }
}

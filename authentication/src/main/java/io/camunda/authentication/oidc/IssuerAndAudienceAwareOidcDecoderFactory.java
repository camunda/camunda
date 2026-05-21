/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oidc;

import io.camunda.authentication.config.JWSKeySelectorFactory;
import io.camunda.authentication.config.OidcAccessTokenDecoderFactory;
import io.camunda.authentication.config.TokenValidatorFactory;
import java.util.List;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Subclass of {@link OidcAccessTokenDecoderFactory} that swaps in {@link
 * IssuerAndAudienceAwareTokenValidator} for the issuer-aware validation step, so multiple {@link
 * ClientRegistration}s sharing an {@code iss} are disambiguated by their declared audiences.
 *
 * <p>The audience source is each registration's {@code providerConfigurationMetadata} — see {@link
 * IssuerAndAudienceAwareTokenValidator} and {@link MetadataAwareTokenValidatorFactory} for the
 * rationale. Producers seed audiences via {@link
 * io.camunda.authentication.pt.PerTenantClientRegistrations#AUDIENCES_METADATA_KEY}.
 */
public class IssuerAndAudienceAwareOidcDecoderFactory extends OidcAccessTokenDecoderFactory {

  private final TokenValidatorFactory tokenValidatorFactory;

  public IssuerAndAudienceAwareOidcDecoderFactory(
      final JWSKeySelectorFactory jwsKeySelectorFactory,
      final TokenValidatorFactory tokenValidatorFactory) {
    super(jwsKeySelectorFactory, tokenValidatorFactory);
    this.tokenValidatorFactory = tokenValidatorFactory;
  }

  @Override
  protected OAuth2TokenValidator<Jwt> createIssuerAwareJwtValidator(
      final List<ClientRegistration> clientRegistrations) {
    return new IssuerAndAudienceAwareTokenValidator(clientRegistrations, tokenValidatorFactory);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.spring.converter.AdditionalJwkSetUrisByRegistrationId;
import io.camunda.security.spring.converter.OidcUserAuthenticationConverter;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import io.camunda.spring.utils.PhysicalTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.JwtDecoder;

final class ProviderAwareOidcUserAuthenticationConverter extends OidcUserAuthenticationConverter {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProviderAwareOidcUserAuthenticationConverter.class);

  private final OidcAccessTokenDecoderFactory accessTokenDecoderFactory;
  private final LazyTokenClaimsConverter defaultTokenClaimsConverter;
  private final Map<String, LazyTokenClaimsConverter> tokenClaimsConvertersByRegistrationId;
  private final Map<String, List<String>> identityClaimsByRegistrationId;
  private final Map<String, PhysicalTenantOidcSettings> settingsByPhysicalTenant;
  private final Map<String, Map<String, JwtDecoder>> tenantJwtDecoders = new ConcurrentHashMap<>();

  ProviderAwareOidcUserAuthenticationConverter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OidcAccessTokenDecoderFactory accessTokenDecoderFactory,
      final LazyTokenClaimsConverter defaultTokenClaimsConverter,
      final HttpServletRequest request,
      final AdditionalJwkSetUrisByRegistrationId additionalJwkSetUrisByRegistrationId,
      final Map<String, Boolean> preferIdTokenClaimsByRegistrationId,
      final Map<String, LazyTokenClaimsConverter> tokenClaimsConvertersByRegistrationId,
      final Map<String, List<String>> identityClaimsByRegistrationId,
      final Map<String, PhysicalTenantOidcSettings> settingsByPhysicalTenant) {
    super(
        authorizedClientRepository,
        accessTokenDecoderFactory,
        defaultTokenClaimsConverter,
        request,
        additionalJwkSetUrisByRegistrationId,
        preferIdTokenClaimsByRegistrationId);
    this.accessTokenDecoderFactory = accessTokenDecoderFactory;
    this.defaultTokenClaimsConverter = defaultTokenClaimsConverter;
    this.tokenClaimsConvertersByRegistrationId = Map.copyOf(tokenClaimsConvertersByRegistrationId);
    this.identityClaimsByRegistrationId = Map.copyOf(identityClaimsByRegistrationId);
    this.settingsByPhysicalTenant = Map.copyOf(settingsByPhysicalTenant);
  }

  /**
   * The super implementation reads the root settings and caches decoders by registration id alone,
   * so a tenant sharing a registration id with the root, or with another tenant, would get the
   * other's issuer, key sets and claim preference. Requests of the default tenant and requests
   * outside any tenant keep the super behavior.
   */
  @Override
  protected Map<String, Object> getClaims(final OAuth2AuthenticationToken authenticationToken) {
    final var settings = currentTenantSettings();
    if (settings == null) {
      return super.getClaims(authenticationToken);
    }
    final var registrationId = authenticationToken.getAuthorizedClientRegistrationId();
    if (Boolean.TRUE.equals(settings.preferIdTokenClaimsByRegistrationId().get(registrationId))) {
      return getIdTokenClaims(authenticationToken);
    }
    return Optional.ofNullable(getAccessTokenClaims(authenticationToken))
        .orElseGet(
            () -> {
              LOG.warn("Falling back to ID Token claims");
              return getIdTokenClaims(authenticationToken);
            });
  }

  @Override
  protected JwtDecoder getJwtDecoder(final ClientRegistration clientRegistration) {
    final var settings = currentTenantSettings();
    if (settings == null) {
      return super.getJwtDecoder(clientRegistration);
    }
    final var registrationId = clientRegistration.getRegistrationId();
    return tenantJwtDecoders
        .computeIfAbsent(PhysicalTenantContext.currentOrNull(), k -> new ConcurrentHashMap<>())
        .computeIfAbsent(
            registrationId,
            k ->
                accessTokenDecoderFactory.createAccessTokenDecoder(
                    clientRegistration,
                    settings.additionalJwkSetUris().get(registrationId),
                    settings.validatorFactory()));
  }

  private PhysicalTenantOidcSettings currentTenantSettings() {
    final var physicalTenantId = PhysicalTenantContext.currentOrNull();
    if (physicalTenantId == null || DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
      return null;
    }
    final var settings = settingsByPhysicalTenant.get(physicalTenantId);
    if (settings == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN,
              "No OIDC configuration for physical tenant '" + physicalTenantId + "'",
              null));
    }
    return settings;
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    final var oauth2Authentication = (OAuth2AuthenticationToken) authentication;
    final var registrationId = oauth2Authentication.getAuthorizedClientRegistrationId();
    final var settings = currentTenantSettings();
    final var tokenClaimsConverter = tokenClaimsConverter(settings, registrationId);
    final var identityClaims =
        settings == null
            ? identityClaimsByRegistrationId
            : settings.identityClaimsByRegistrationId();
    try {
      final var claims = new HashMap<>(getClaims(oauth2Authentication));
      identityClaims
          .getOrDefault(registrationId, List.of())
          .forEach(claim -> normalizeUriClaim(claims, claim));
      return tokenClaimsConverter.convert(claims);
    } catch (final IllegalArgumentException e) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, e.getMessage(), null), e);
    }
  }

  private LazyTokenClaimsConverter tokenClaimsConverter(
      final PhysicalTenantOidcSettings settings, final String registrationId) {
    if (settings == null) {
      return tokenClaimsConvertersByRegistrationId.getOrDefault(
          registrationId, defaultTokenClaimsConverter);
    }
    final var converter = settings.tokenClaimsConvertersByRegistrationId().get(registrationId);
    if (converter == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN,
              "No OIDC registration '" + registrationId + "' for the physical tenant",
              null));
    }
    return converter;
  }

  private static void normalizeUriClaim(final Map<String, Object> claims, final String claimName) {
    final var value = claims.get(claimName);
    if (value instanceof URI || value instanceof URL) {
      claims.put(claimName, value.toString());
    }
  }

  /**
   * OIDC settings of one non-default physical tenant, resolved from the tenant's own providers: the
   * access-token validators, and per registration id the additional JWKS URIs, claim preference and
   * claim mapping.
   */
  record PhysicalTenantOidcSettings(
      TokenValidatorFactory validatorFactory,
      AdditionalJwkSetUrisByRegistrationId additionalJwkSetUris,
      Map<String, Boolean> preferIdTokenClaimsByRegistrationId,
      Map<String, LazyTokenClaimsConverter> tokenClaimsConvertersByRegistrationId,
      Map<String, List<String>> identityClaimsByRegistrationId) {}
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import io.camunda.authentication.config.OidcAuthenticationConfigurationRepository;
import io.camunda.security.configuration.PhysicalTenantConfiguration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Additive {@link Converter} that composes Spring's default {@link JwtAuthenticationConverter}
 * (preserving {@code SCOPE_*} extraction) and adds {@code PT_<id>} authorities for each {@code
 * pt:*} value present in the configured claim (default {@code groups}).
 *
 * <p>If no {@code pt:*} value is present the default authorities are returned untouched —
 * pre-existing tokens are unaffected. When a {@code pt:*} value <em>is</em> present, the converter
 * enforces the PT allow-list: the issuer of the token must correspond to a registration id that
 * appears in the claimed PT's {@code idps} list, otherwise authentication is denied via {@link
 * OAuth2AuthenticationException}. Empty {@code idps} on a PT means passthrough (no filtering),
 * matching the configuration-binding semantics.
 */
public class PhysicalTenantJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  static final String DEFAULT_CLAIM_NAME = "groups";
  static final String DEFAULT_CLAIM_VALUE_PREFIX = "pt:";
  static final String AUTHORITY_PREFIX = "PT_";

  private final Converter<Jwt, ? extends AbstractAuthenticationToken> delegate;
  private final OidcAuthenticationConfigurationRepository providers;
  private final List<PhysicalTenantConfiguration> tenants;
  private final String claimName;
  private final String claimValuePrefix;

  public PhysicalTenantJwtAuthenticationConverter(
      final OidcAuthenticationConfigurationRepository providers,
      final List<PhysicalTenantConfiguration> tenants) {
    this(
        new JwtAuthenticationConverter(),
        providers,
        tenants,
        DEFAULT_CLAIM_NAME,
        DEFAULT_CLAIM_VALUE_PREFIX);
  }

  PhysicalTenantJwtAuthenticationConverter(
      final Converter<Jwt, ? extends AbstractAuthenticationToken> delegate,
      final OidcAuthenticationConfigurationRepository providers,
      final List<PhysicalTenantConfiguration> tenants,
      final String claimName,
      final String claimValuePrefix) {
    this.delegate = delegate;
    this.providers = providers;
    this.tenants = tenants;
    this.claimName = claimName;
    this.claimValuePrefix = claimValuePrefix;
  }

  @Override
  public AbstractAuthenticationToken convert(final Jwt jwt) {
    final AbstractAuthenticationToken defaultAuth = delegate.convert(jwt);
    final List<String> ptIds = extractPtIds(jwt);
    if (ptIds.isEmpty()) {
      return defaultAuth;
    }

    final String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
    final String registrationId = resolveRegistrationId(issuer);
    if (registrationId == null) {
      throw denied("Token issuer '" + issuer + "' does not match any configured OIDC registration");
    }

    final Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    if (defaultAuth != null && defaultAuth.getAuthorities() != null) {
      authorities.addAll(defaultAuth.getAuthorities());
    }

    for (final String ptId : ptIds) {
      final PhysicalTenantConfiguration tenant = findTenant(ptId);
      if (tenant == null) {
        throw denied("Token claims PT '" + ptId + "' which is not configured");
      }
      final List<String> allowList = tenant.getIdps();
      if (allowList != null && !allowList.isEmpty() && !allowList.contains(registrationId)) {
        throw denied(
            "OIDC registration '"
                + registrationId
                + "' is not in the idps allow-list of PT '"
                + ptId
                + "'");
      }
      authorities.add(new SimpleGrantedAuthority(AUTHORITY_PREFIX + ptId));
    }

    final String principal = defaultAuth == null ? jwt.getSubject() : defaultAuth.getName();
    return new JwtAuthenticationToken(jwt, authorities, principal);
  }

  private List<String> extractPtIds(final Jwt jwt) {
    final Object raw = jwt.getClaim(claimName);
    if (raw == null) {
      return List.of();
    }
    final List<String> result = new ArrayList<>();
    if (raw instanceof Collection<?> collection) {
      for (final Object value : collection) {
        addIfPt(result, value);
      }
    } else {
      addIfPt(result, raw);
    }
    return result;
  }

  private void addIfPt(final List<String> sink, final Object raw) {
    if (raw instanceof String value && value.startsWith(claimValuePrefix)) {
      final String id = value.substring(claimValuePrefix.length());
      if (!id.isBlank()) {
        sink.add(id);
      }
    }
  }

  private String resolveRegistrationId(final String issuer) {
    if (issuer == null) {
      return null;
    }
    for (final Map.Entry<String, ?> entry :
        providers.getOidcAuthenticationConfigurations().entrySet()) {
      final var issuerUri =
          providers.getOidcAuthenticationConfigurationById(entry.getKey()).getIssuerUri();
      if (issuer.equals(issuerUri)) {
        return entry.getKey();
      }
    }
    return null;
  }

  private PhysicalTenantConfiguration findTenant(final String id) {
    for (final PhysicalTenantConfiguration tenant : tenants) {
      if (id.equals(tenant.getId())) {
        return tenant;
      }
    }
    return null;
  }

  private static OAuth2AuthenticationException denied(final String description) {
    return new OAuth2AuthenticationException(
        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oidc;

import io.camunda.authentication.config.AudienceValidator;
import io.camunda.authentication.config.ClusterValidator;
import io.camunda.authentication.config.OidcAuthenticationConfigurationRepository;
import io.camunda.authentication.config.OrganizationValidator;
import io.camunda.authentication.config.TokenValidatorFactory;
import io.camunda.authentication.pt.PerTenantClientRegistrations;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * Drop-in replacement for the host's {@link TokenValidatorFactory} that reads each registration's
 * expected audiences from {@link ClientRegistration.ProviderDetails#getConfigurationMetadata()}
 * rather than from a static {@code registrationId -> OidcConfiguration} map keyed off the root
 * {@link SecurityConfiguration}.
 *
 * <p>Why this is necessary: under physical-tenant overrides multiple {@link ClientRegistration}s
 * can share the same registration id (e.g. each PT's view of a shared {@code tenanta} IdP) but
 * declare different {@code audiences}. The host's stock factory looks up audiences by registration
 * id from a map built from ROOT config — PT-side audience overrides would be silently dropped
 * because the lookup key collides on the registration id and the map only ever holds ROOT audiences
 * for that id.
 *
 * <p>By stashing audiences on the registration itself (via {@code providerConfigurationMetadata} —
 * see {@link PerTenantClientRegistrations#AUDIENCES_METADATA_KEY} and {@code
 * OidcOverrideBeansConfiguration#enhanceWithAudiencesMetadata} for root-side registrations) the
 * factory composes the audience validator from the same source of truth used by {@link
 * IssuerAndAudienceAwareTokenValidator} when picking which registration's validator to fire for an
 * inbound token. The two paths read the same field; PT overrides flow consistently through both.
 *
 * <p>Composition mirrors the host's stock {@link TokenValidatorFactory#createTokenValidator}:
 * timestamp validator (always), audience validator (when the registration carries audiences), SaaS
 * organization/cluster validators (when {@code SecurityConfiguration.getSaas().isConfigured()}
 * returns true). The super constructor is still invoked so super-side fields exist for any caller
 * that holds a {@code TokenValidatorFactory} reference, but its {@code createTokenValidator} is
 * fully overridden — the static providers map super maintains is not consulted.
 */
public class MetadataAwareTokenValidatorFactory extends TokenValidatorFactory {

  private final SecurityConfiguration securityConfiguration;

  public MetadataAwareTokenValidatorFactory(
      final SecurityConfiguration securityConfiguration,
      final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository) {
    super(securityConfiguration, oidcAuthenticationConfigurationRepository);
    this.securityConfiguration = securityConfiguration;
  }

  @Override
  public OAuth2TokenValidator<Jwt> createTokenValidator(
      final ClientRegistration clientRegistration) {
    final var validators = new LinkedList<OAuth2TokenValidator<Jwt>>();

    validators.add(
        new JwtTimestampValidator(
            securityConfiguration.getAuthentication().getOidc().getClockSkew()));

    final Set<String> audiences = readAudiencesFromMetadata(clientRegistration);
    if (!audiences.isEmpty()) {
      validators.add(new AudienceValidator(audiences));
    }

    if (securityConfiguration.getSaas().isConfigured()) {
      validators.add(
          new OrganizationValidator(securityConfiguration.getSaas().getOrganizationId()));
      validators.add(new ClusterValidator(securityConfiguration.getSaas().getClusterId()));
    }

    return new DelegatingOAuth2TokenValidator<>(validators);
  }

  /**
   * Extracts the audiences list stashed under {@link
   * PerTenantClientRegistrations#AUDIENCES_METADATA_KEY} on the registration's provider details.
   * Returns an empty set when the registration carries no audiences (no audience validator will be
   * composed, mirroring the host's stock behaviour for a provider with {@code audiences}
   * null/empty).
   */
  private static Set<String> readAudiencesFromMetadata(final ClientRegistration registration) {
    final Object value =
        registration
            .getProviderDetails()
            .getConfigurationMetadata()
            .get(PerTenantClientRegistrations.AUDIENCES_METADATA_KEY);
    if (!(value instanceof final List<?> list) || list.isEmpty()) {
      return Set.of();
    }
    // The producer always writes List<String>; we narrow at the call site rather than assuming
    // generics — fail closed (skip non-String entries) so a misconfigured custom producer can't
    // poison the validator with non-string audiences.
    final Set<String> audiences = new LinkedHashSet<>();
    for (final Object element : list) {
      if (element instanceof final String s && !s.isBlank()) {
        audiences.add(s);
      }
    }
    return audiences;
  }
}

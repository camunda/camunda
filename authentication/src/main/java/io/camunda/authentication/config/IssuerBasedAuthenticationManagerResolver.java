/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

public class IssuerBasedAuthenticationManagerResolver
    implements AuthenticationManagerResolver<String> {

  private final Map<String, OidcAuthenticationConfiguration> providers;
  private final Predicate<String> trustedIssuer;
  private final JwtDecoderFactory jwtDecoderFactory;
  private final Map<String, AuthenticationManager> authenticationManagers = new ConcurrentHashMap();

  public IssuerBasedAuthenticationManagerResolver(
      final Map<String, OidcAuthenticationConfiguration> providers,
      final JwtDecoderFactory jwtDecoderFactory,
      final Predicate<String> trustedIssuer) {
    this.providers = providers;
    this.jwtDecoderFactory = jwtDecoderFactory;
    this.trustedIssuer = trustedIssuer;
  }

  @Override
  public AuthenticationManager resolve(final String issuer) {
    if (trustedIssuer.test(issuer)) {
      return authenticationManagers.computeIfAbsent(
          issuer,
          (k) -> {
            final var provider =
                providers.entrySet().stream()
                    .filter(e -> e.getValue().getIssuerUri().equals(issuer))
                    .findAny()
                    .get();
            final var jwtDecoder = jwtDecoderFactory.createDecoder(provider.getKey());
            final JwtAuthenticationProvider authenticationProvider =
                new JwtAuthenticationProvider(jwtDecoder);
            return authenticationProvider::authenticate;
          });
    } else {
      return null;
    }
  }
}

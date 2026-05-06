/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import java.time.Duration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Decorator around {@link CamundaAuthenticationConverter} that caches the computed {@link
 * CamundaAuthentication} across requests. This is particularly important for stateless REST API
 * requests where no HTTP session exists, which causes the per-session holder to be bypassed and
 * the full authentication conversion (including DB lookups) to repeat on every request.
 *
 * <p>Anonymous authentications are never cached and always delegate to the wrapped converter.
 * Cache entries expire after the configured TTL.
 */
public class CachingCamundaAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private final CamundaAuthenticationConverter<Authentication> delegate;
  private final Cache<String, CamundaAuthentication> cache;

  public CachingCamundaAuthenticationConverter(
      final CamundaAuthenticationConverter<Authentication> delegate,
      final Duration ttl,
      final long maxSize) {
    this.delegate = delegate;
    cache = Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(ttl).build();
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return delegate.supports(authentication);
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    final String key = cacheKey(authentication);
    if (key == null) {
      return delegate.convert(authentication);
    }
    // Caffeine.get() is atomic: concurrent misses on the same key call the loader only once.
    // Null loader results are not cached, so failed conversions are retried on the next request.
    return cache.get(key, ignored -> delegate.convert(authentication));
  }

  private static String cacheKey(final Authentication authentication) {
    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
      return null;
    }
    if (authentication instanceof final JwtAuthenticationToken jwt) {
      return "jwt:" + jwt.getToken().getTokenValue();
    }
    if (authentication instanceof final OAuth2AuthenticationToken oauth2) {
      return "oauth2:" + oauth2.getAuthorizedClientRegistrationId() + ":" + oauth2.getName();
    }
    return "basic:" + authentication.getName();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationCache;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class DefaultCamundaAuthenticationProvider implements CamundaAuthenticationProvider {

  private final CamundaAuthenticationCache cache;
  private final CamundaAuthenticationConverter<Authentication> converter;

  public DefaultCamundaAuthenticationProvider(
      final CamundaAuthenticationCache cache,
      final CamundaAuthenticationConverter<Authentication> converter) {
    this.cache = cache;
    this.converter = converter;
  }

  @Override
  public CamundaAuthentication getCamundaAuthentication() {
    final var springBasedAuthentication = SecurityContextHolder.getContext().getAuthentication();
    return Optional.ofNullable(getFromCacheIfPresent(springBasedAuthentication))
        .orElseGet(() -> convertAndCache(springBasedAuthentication));
  }

  private CamundaAuthentication getFromCacheIfPresent(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .map(Authentication::getPrincipal)
        .map(cache::get)
        .orElse(null);
  }

  private CamundaAuthentication convertAndCache(final Authentication authentication) {
    final var result = converter.convert(authentication);
    if (result != null && !result.isAnonymous()) {
      Optional.ofNullable(authentication)
          .map(Authentication::getPrincipal)
          .ifPresent(p -> cache.put(p, result));
    }
    return result;
  }
}

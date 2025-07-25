/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.cache;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationCache;
import java.util.List;
import java.util.Optional;

public class CamundaAuthenticationDelegatingCache implements CamundaAuthenticationCache {

  private final List<CamundaAuthenticationCache> caches;

  public CamundaAuthenticationDelegatingCache(final List<CamundaAuthenticationCache> caches) {
    this.caches = caches;
  }

  @Override
  public boolean supports(final Object principal) {
    return true;
  }

  @Override
  public void put(final Object principal, final CamundaAuthentication authentication) {
    Optional.ofNullable(getCache(principal)).ifPresent(c -> c.put(principal, authentication));
  }

  @Override
  public CamundaAuthentication get(final Object principal) {
    return Optional.ofNullable(getCache(principal)).map(c -> c.get(principal)).orElse(null);
  }

  @Override
  public void remove(final Object principal) {
    Optional.ofNullable(getCache(principal)).ifPresent(c -> c.remove(principal));
  }

  protected CamundaAuthenticationCache getCache(final Object principal) {
    return caches.stream().filter(cache -> cache.supports(principal)).findFirst().orElse(null);
  }
}

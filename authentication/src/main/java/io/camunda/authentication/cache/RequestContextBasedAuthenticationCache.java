/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.cache;

import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationCache;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * A {@link CamundaAuthenticationCache} that caches a {@link CamundaAuthentication} using the {@link
 * RequestContextHolder} as an underlying cache, meaning the {@link CamundaAuthentication} is stored
 * as a request attribute. The cache ensures that the same {@link CamundaAuthentication} is returned
 * while processing a request. However, Spring bounds the request attributes to the current thread.
 * By default, the request attributes are not inheritable for child threads. When spawning new
 * threads, it cannot guarantee to return the same instance of a {@link CamundaAuthentication} child
 * threads.
 */
public class RequestContextBasedAuthenticationCache implements CamundaAuthenticationCache {

  static final String CAMUNDA_AUTHENTICATION_CACHE_KEY =
      "io.camunda.security.request:CamundaAuthentication";

  private final HttpServletRequest request;

  public RequestContextBasedAuthenticationCache(final HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public boolean supports(final Object principal) {
    return request.getSession(false) == null;
  }

  @Override
  public void put(final Object principal, final CamundaAuthentication authentication) {
    RequestContextHolder.currentRequestAttributes()
        .setAttribute(CAMUNDA_AUTHENTICATION_CACHE_KEY, authentication, SCOPE_REQUEST);
  }

  @Override
  public CamundaAuthentication get(final Object principal) {
    return (CamundaAuthentication)
        RequestContextHolder.currentRequestAttributes()
            .getAttribute(CAMUNDA_AUTHENTICATION_CACHE_KEY, SCOPE_REQUEST);
  }

  @Override
  public void remove(final Object principal) {
    RequestContextHolder.currentRequestAttributes()
        .removeAttribute(CAMUNDA_AUTHENTICATION_CACHE_KEY, SCOPE_REQUEST);
  }
}

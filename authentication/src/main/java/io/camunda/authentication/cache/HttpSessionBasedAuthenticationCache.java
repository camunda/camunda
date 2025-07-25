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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;

/**
 * A {@link CamundaAuthenticationCache} that caches a {@link CamundaAuthentication} using the {@link
 * HttpSession} as an underlying cache. If no {@link HttpSession} exists, any given {@link
 * CamundaAuthentication} won't be cached.
 */
public class HttpSessionBasedAuthenticationCache implements CamundaAuthenticationCache {

  static final String CAMUNDA_AUTHENTICATION_CACHE_KEY =
      "io.camunda.security.session:CamundaAuthentication";

  private final HttpServletRequest request;

  public HttpSessionBasedAuthenticationCache(final HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public boolean supports(final Object principal) {
    return request.getSession(false) != null;
  }

  @Override
  public void put(final Object principal, final CamundaAuthentication authentication) {
    Optional.of(getHttpSession())
        .ifPresent(session -> setCamundaAuthenticationInSession(session, authentication));
  }

  @Override
  public CamundaAuthentication get(final Object principal) {
    return Optional.of(getHttpSession())
        .map(this::getCamundaAuthenticationFromSessionIfExists)
        .orElse(null);
  }

  @Override
  public void remove(final Object principal) {
    Optional.of(getHttpSession()).ifPresent(this::removeCamundaAuthenticationInSession);
  }

  protected HttpSession getHttpSession() {
    return request.getSession();
  }

  protected CamundaAuthentication getCamundaAuthenticationFromSessionIfExists(
      final HttpSession session) {
    return (CamundaAuthentication) session.getAttribute(CAMUNDA_AUTHENTICATION_CACHE_KEY);
  }

  protected void setCamundaAuthenticationInSession(
      final HttpSession session, final CamundaAuthentication camundaAuthentication) {
    session.setAttribute(CAMUNDA_AUTHENTICATION_CACHE_KEY, camundaAuthentication);
  }

  protected void removeCamundaAuthenticationInSession(final HttpSession session) {
    session.removeAttribute(CAMUNDA_AUTHENTICATION_CACHE_KEY);
  }
}

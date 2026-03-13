/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.holder;

import static java.time.temporal.ChronoUnit.MILLIS;

import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.spi.CamundaAuthenticationHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Associates a {@link CamundaAuthentication} to an existing {@link HttpSession}. As long as the
 * {@link HttpSession} stays active, the same {@link CamundaAuthentication} is returned.
 */
public final class HttpSessionBasedAuthenticationHolder implements CamundaAuthenticationHolder {

  public static final String CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY =
      "io.camunda.gatekeeper.session:CamundaAuthentication";
  public static final String LAST_REFRESH_ATTR = "AUTH_LAST_REFRESH";

  private final Duration authenticationRefreshInterval;
  private final HttpServletRequest request;

  public HttpSessionBasedAuthenticationHolder(
      final HttpServletRequest request, final AuthenticationConfig authenticationConfig) {
    this.request = request;
    authenticationRefreshInterval = authenticationConfig.authenticationRefreshInterval();
  }

  @Override
  public boolean supports() {
    return request.getSession(false) != null;
  }

  @Override
  public void set(final CamundaAuthentication authentication) {
    Optional.ofNullable(getHttpSession())
        .ifPresent(session -> setCamundaAuthenticationInSession(session, authentication));
  }

  @Override
  public CamundaAuthentication get() {
    return Optional.ofNullable(getHttpSession())
        .map(this::getCamundaAuthenticationFromSessionIfExists)
        .orElse(null);
  }

  private HttpSession getHttpSession() {
    return request.getSession(false);
  }

  private CamundaAuthentication getCamundaAuthenticationFromSessionIfExists(
      final HttpSession session) {
    final Instant now = Instant.now();
    final Instant lastRefresh = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
    if (lastRefresh != null && isRefreshRequired(lastRefresh, now)) {
      lockAndRefresh(session, now);
    }
    return (CamundaAuthentication) session.getAttribute(CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY);
  }

  private void lockAndRefresh(final HttpSession session, final Instant now) {
    synchronized (session.getAttribute(LAST_REFRESH_ATTR + "_LOCK")) {
      final Instant lastRefresh = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
      if (isRefreshRequired(lastRefresh, now)) {
        removeCamundaAuthenticationInSession(session);
        session.setAttribute(LAST_REFRESH_ATTR, now);
      }
    }
  }

  private void setCamundaAuthenticationInSession(
      final HttpSession session, final CamundaAuthentication camundaAuthentication) {
    session.setAttribute(CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY, camundaAuthentication);
    final Instant now = Instant.now();
    final Instant lastRefresh = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
    if (lastRefresh == null) {
      initializeRefreshAttributes(session, now);
    }
  }

  public void removeCamundaAuthenticationInSession(final HttpSession session) {
    session.removeAttribute(CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY);
  }

  private static void initializeRefreshAttributes(final HttpSession session, final Instant now) {
    session.setAttribute(LAST_REFRESH_ATTR, now);
    session.setAttribute(LAST_REFRESH_ATTR + "_LOCK", session.getId() + "LOCK");
  }

  private boolean isRefreshRequired(final Instant lastRefresh, final Instant now) {
    return MILLIS.between(lastRefresh, now) >= authenticationRefreshInterval.toMillis();
  }
}

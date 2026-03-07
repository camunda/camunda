/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.holder;

import static java.time.temporal.ChronoUnit.MILLIS;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.CamundaAuthenticationHolder;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Associates a {@link CamundaAuthentication} to an existing {@link HttpSession}. As long as the
 * {@link HttpSession} stays active, the same {@link CamundaAuthentication} is returned.
 */
public class HttpSessionBasedAuthenticationHolder implements CamundaAuthenticationHolder {

  public static final String CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY =
      "io.camunda.security.session:CamundaAuthentication";
  public static final String LAST_REFRESH_ATTR = "AUTH_LAST_REFRESH";

  /**
   * Per-session lock objects keyed by session ID. Using a ConcurrentMap instead of a session
   * attribute avoids two problems: (1) session.getAttribute() can return null after session
   * deserialization, causing NPE in synchronized blocks; (2) deserialized String attributes may
   * yield different object references across calls, breaking synchronized semantics.
   */
  private static final ConcurrentMap<String, Object> SESSION_LOCKS = new ConcurrentHashMap<>();

  private final Duration authenticationRefreshInterval;

  public HttpSessionBasedAuthenticationHolder(final Duration authenticationRefreshInterval) {
    this.authenticationRefreshInterval = authenticationRefreshInterval;
  }

  @Override
  public boolean supports() {
    return getHttpSession() != null;
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

  protected HttpSession getHttpSession() {
    final ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return null;
    }
    return attributes.getRequest().getSession(false);
  }

  protected CamundaAuthentication getCamundaAuthenticationFromSessionIfExists(
      final HttpSession session) {
    final Instant now = Instant.now();
    final Instant lastRefresh = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
    if (lastRefresh != null && isRefreshRequired(lastRefresh, now)) {
      lockAndRefresh(session, now);
    }
    return (CamundaAuthentication) session.getAttribute(CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY);
  }

  private void lockAndRefresh(final HttpSession session, final Instant now) {
    final Object lock = SESSION_LOCKS.computeIfAbsent(session.getId(), id -> new Object());
    synchronized (lock) {
      final Instant lastRefresh = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
      if (isRefreshRequired(lastRefresh, now)) {
        removeCamundaAuthenticationInSession(session);
        session.setAttribute(LAST_REFRESH_ATTR, now);
      }
    }
  }

  protected void setCamundaAuthenticationInSession(
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
    SESSION_LOCKS.remove(session.getId());
  }

  private static void initializeRefreshAttributes(final HttpSession session, final Instant now) {
    session.setAttribute(LAST_REFRESH_ATTR, now);
  }

  private boolean isRefreshRequired(final Instant lastRefresh, final Instant now) {
    return MILLIS.between(lastRefresh, now) >= authenticationRefreshInterval.toMillis();
  }
}

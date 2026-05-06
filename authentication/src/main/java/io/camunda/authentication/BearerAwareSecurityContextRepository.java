/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Routes {@link SecurityContextRepository} calls between a session-aware delegate and a no-op
 * {@link NullSecurityContextRepository} based on the request's authentication shape.
 *
 * <p>The API chain on this branch serves two flows:
 *
 * <ul>
 *   <li>Headless bearer-token clients ({@code Authorization: Bearer …}). Stateless — no session
 *       should be consulted, no security context ever loaded from a session.
 *   <li>Webapp users who logged in via the webapp chain and obtained a session cookie. Their {@link
 *       SecurityContext} lives in the session and must be loaded to keep them authenticated when
 *       they hit API endpoints.
 * </ul>
 *
 * <p>{@link SessionCreationPolicy} is a coarse switch — {@code STATELESS} kills session lookup
 * entirely (breaking webapp-user-on-API) and {@code NEVER} consults the session repository on every
 * request even for bearer requests that have no session cookie. The wall-clock cost on a
 * Spring-Session-backed deployment is real: {@link
 * org.springframework.security.web.context.HttpSessionSecurityContextRepository#loadDeferredContext}
 * unconditionally calls {@code request.getSession(false)}, which on a Spring-Session-wrapped
 * request triggers cookie parsing and (when a session cookie is present) a backend session lookup.
 *
 * <p>This class gates the delegate by:
 *
 * <ol>
 *   <li>If the request carries a {@code Authorization: Bearer …} header — bearer-token client. Use
 *       the no-op repository. Bearer authentication is performed downstream by {@code
 *       BearerTokenAuthenticationFilter}; no session involvement is appropriate.
 *   <li>Else if the request carries a session cookie of the configured name — webapp user. Use the
 *       session-aware delegate.
 *   <li>Else (no bearer, no session cookie) — anonymous request. Use the no-op repository.
 * </ol>
 *
 * <p>Bearer-wins ordering is intentional. Some clients accidentally carry stale session cookies,
 * and we don't want a stale cookie to opt them into session lookup when the {@code Authorization}
 * header has already declared their authentication intent.
 *
 * <p>Functional behaviour for cached webapp users is unchanged: a request with a valid session
 * cookie still goes through the standard {@code HttpSessionSecurityContextRepository} path.
 */
public class BearerAwareSecurityContextRepository implements SecurityContextRepository {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final SecurityContextRepository sessionAware;
  private final SecurityContextRepository nullRepository;
  private final String sessionCookieName;

  public BearerAwareSecurityContextRepository(
      final SecurityContextRepository sessionAware, final String sessionCookieName) {
    this.sessionAware = Objects.requireNonNull(sessionAware, "sessionAware");
    this.sessionCookieName = Objects.requireNonNull(sessionCookieName, "sessionCookieName");
    nullRepository = new NullSecurityContextRepository();
  }

  @Override
  public SecurityContext loadContext(final HttpRequestResponseHolder requestResponseHolder) {
    return repoFor(requestResponseHolder.getRequest()).loadContext(requestResponseHolder);
  }

  @Override
  public DeferredSecurityContext loadDeferredContext(final HttpServletRequest request) {
    return repoFor(request).loadDeferredContext(request);
  }

  @Override
  public void saveContext(
      final SecurityContext context,
      final HttpServletRequest request,
      final HttpServletResponse response) {
    repoFor(request).saveContext(context, request, response);
  }

  @Override
  public boolean containsContext(final HttpServletRequest request) {
    return repoFor(request).containsContext(request);
  }

  private SecurityContextRepository repoFor(final HttpServletRequest request) {
    if (hasBearerToken(request)) {
      return nullRepository;
    }
    if (hasSessionCookie(request)) {
      return sessionAware;
    }
    return nullRepository;
  }

  private boolean hasBearerToken(final HttpServletRequest request) {
    final String header = request.getHeader(AUTHORIZATION_HEADER);
    return header != null
        && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
  }

  private boolean hasSessionCookie(final HttpServletRequest request) {
    final Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return false;
    }
    for (final Cookie cookie : cookies) {
      if (sessionCookieName.equals(cookie.getName())) {
        return true;
      }
    }
    return false;
  }
}

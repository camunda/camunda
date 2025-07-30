/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.holder;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;

/**
 * Associates a {@link CamundaAuthentication} to an existing {@link HttpSession}. As long as the
 * {@link HttpSession} stays active, the same {@link CamundaAuthentication} is returned.
 */
public class HttpSessionBasedAuthenticationHolder implements CamundaAuthenticationHolder {

  public static final String CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY =
      "io.camunda.security.session:CamundaAuthentication";

  private final HttpServletRequest request;

  public HttpSessionBasedAuthenticationHolder(final HttpServletRequest request) {
    this.request = request;
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

  protected HttpSession getHttpSession() {
    return request.getSession(false);
  }

  protected CamundaAuthentication getCamundaAuthenticationFromSessionIfExists(
      final HttpSession session) {
    return (CamundaAuthentication) session.getAttribute(CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY);
  }

  protected void setCamundaAuthenticationInSession(
      final HttpSession session, final CamundaAuthentication camundaAuthentication) {
    session.setAttribute(CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY, camundaAuthentication);
  }

  protected void removeCamundaAuthenticationInSession(final HttpSession session) {
    session.removeAttribute(CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY);
  }
}

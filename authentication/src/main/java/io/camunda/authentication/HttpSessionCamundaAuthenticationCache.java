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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;

public class HttpSessionCamundaAuthenticationCache implements CamundaAuthenticationCache {

  private static final String CAMUNDA_AUTHENTICATION_CONTEXT =
      "io.camunda.security.authentication:CamundaAuthentication";

  private final HttpServletRequest request;

  public HttpSessionCamundaAuthenticationCache(final HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public CamundaAuthentication getFromCache() {
    return Optional.ofNullable(getHttpSessionIfExists())
        .map(this::getCamundaAuthenticationFromSessionIfExists)
        .orElse(null);
  }

  @Override
  public void addOrUpdateInCache(final CamundaAuthentication camundaAuthentication) {
    Optional.ofNullable(getHttpSessionIfExists())
        .ifPresent(session -> setCamundaAuthenticationInSession(session, camundaAuthentication));
  }

  protected HttpSession getHttpSessionIfExists() {
    // don't create a session if it does not exist;
    return request.getSession(false);
  }

  protected CamundaAuthentication getCamundaAuthenticationFromSessionIfExists(
      final HttpSession session) {
    return (CamundaAuthentication) session.getAttribute(CAMUNDA_AUTHENTICATION_CONTEXT);
  }

  protected void setCamundaAuthenticationInSession(
      final HttpSession session, final CamundaAuthentication camundaAuthentication) {
    session.setAttribute(CAMUNDA_AUTHENTICATION_CONTEXT, camundaAuthentication);
  }
}

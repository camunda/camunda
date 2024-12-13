/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.csrf;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * This CSRF token repository delegates to the provided repository and also sets a CSRF token
 * response header when a token is saved.
 */
public class ResponseHeaderCsrfTokenRepository implements CsrfTokenRepository {

  private final CsrfTokenRepository repository;
  private final String headerName;

  public ResponseHeaderCsrfTokenRepository(
      final CsrfTokenRepository repository, final String headerName) {
    this.repository = repository;
    this.headerName = headerName;
  }

  @Override
  public CsrfToken generateToken(final HttpServletRequest request) {
    return repository.generateToken(request);
  }

  @Override
  public void saveToken(
      final CsrfToken token, final HttpServletRequest request, final HttpServletResponse response) {
    repository.saveToken(token, request, response);
    response.setHeader(headerName, token != null ? token.getToken() : "");
  }

  @Override
  public CsrfToken loadToken(final HttpServletRequest request) {
    return repository.loadToken(request);
  }
}

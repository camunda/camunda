/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.holder;

import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Associates a given {@link CamundaAuthentication} with the executing thread by using the {@link
 * RequestContextHolder}, meaning the {@link CamundaAuthentication} is stored as a request
 * attribute. {@link RequestContextBasedAuthenticationHolder This} ensures that the same {@link
 * CamundaAuthentication} is returned while processing a request. However, Spring bounds the request
 * attributes to the current thread. By default, the request attributes are not inheritable for
 * child threads. When spawning new threads, it cannot guarantee to return the same instance of a
 * {@link CamundaAuthentication} child threads.
 *
 * <p>{@link org.springframework.web.filter.RequestContextFilter} removes the {@link
 * org.springframework.web.context.request.RequestAttributes} accordingly
 */
public class RequestContextBasedAuthenticationHolder implements CamundaAuthenticationHolder {

  static final String CAMUNDA_AUTHENTICATION_REQUEST_HOLDER_KEY =
      "io.camunda.security.request:CamundaAuthentication";

  private final HttpServletRequest request;

  public RequestContextBasedAuthenticationHolder(final HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public boolean supports() {
    return request.getSession(false) == null;
  }

  @Override
  public void set(final CamundaAuthentication authentication) {
    RequestContextHolder.currentRequestAttributes()
        .setAttribute(CAMUNDA_AUTHENTICATION_REQUEST_HOLDER_KEY, authentication, SCOPE_REQUEST);
  }

  @Override
  public CamundaAuthentication get() {
    return (CamundaAuthentication)
        RequestContextHolder.currentRequestAttributes()
            .getAttribute(CAMUNDA_AUTHENTICATION_REQUEST_HOLDER_KEY, SCOPE_REQUEST);
  }
}

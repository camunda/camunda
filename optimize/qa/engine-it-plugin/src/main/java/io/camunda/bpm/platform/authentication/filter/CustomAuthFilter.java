/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.platform.authentication.filter;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.camunda.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;

public class CustomAuthFilter extends ProcessEngineAuthenticationFilter {
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;

    String customToken = req.getHeader("Custom-Token");
    if (!"SomeCustomToken".equals(customToken)) {
      resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    } else {
      super.doFilter(request, response, chain);
    }
  }
}

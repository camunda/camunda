/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CustomFilter2 extends HttpFilter {

  @Override
  protected void doFilter(
      final HttpServletRequest servletRequest,
      final HttpServletResponse servletResponse,
      final FilterChain filterChain)
      throws ServletException, IOException {

    // make this fail for a specific test scenario: REST call
    // "DELETE /.../user-tasks/987654321/assignee"
    if (servletRequest.getRequestURI().contains("/user-tasks/987654321/assignee")
        && "DELETE".equals(servletRequest.getMethod())) {

      throw new RuntimeException("I'm FILTERING!!!!");
    }
    filterChain.doFilter(servletRequest, servletResponse);
  }
}

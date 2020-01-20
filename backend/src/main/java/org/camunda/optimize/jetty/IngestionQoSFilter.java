/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.eclipse.jetty.servlets.QoSFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * This quality of service filter applies to event ingestion, limiting the max number of requests that can be served at
 * a time.
 */
@RequiredArgsConstructor
public class IngestionQoSFilter extends QoSFilter {

  private final Callable<Integer> maxRequestCountProvider;

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    if (getMaxRequests() != getMaxRequestsFromProvider()) {
      setMaxRequests(getMaxRequestsFromProvider());
    }
    super.doFilter(request, response, chain);
  }

  @SneakyThrows
  public int getMaxRequestsFromProvider() {
    return maxRequestCountProvider.call();
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util.rest;

import java.net.URI;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * Factory class which sets up the HttpComponents context to be the same on every request with the
 * RestTemplate.
 */
public class StatefulHttpComponentsClientHttpRequestFactory
    extends HttpComponentsClientHttpRequestFactory {

  private final HttpContext httpContext;

  public StatefulHttpComponentsClientHttpRequestFactory(
      HttpClient httpClient, HttpContext httpContext) {
    super(httpClient);
    this.httpContext = httpContext;
  }

  @Override
  protected HttpContext createHttpContext(HttpMethod httpMethod, URI uri) {
    return this.httpContext;
  }
}

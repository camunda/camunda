/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util.rest;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.springframework.context.annotation.Scope;
import org.springframework.http.*;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Stateful Rest template that you can use to remember cookies, once you log in with it will
 * remember the JSESSIONID and sent it on subsequent requests.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class StatefulRestTemplate extends RestTemplate {
  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";

  private final String host;
  private final Integer port;
  private final HttpClient httpClient;
  private final CookieStore cookieStore;
  private final HttpContext httpContext;
  private final StatefulHttpComponentsClientHttpRequestFactory
      statefulHttpComponentsClientHttpRequestFactory;
  private final String contextPath;

  public StatefulRestTemplate(final String host, final Integer port, final String contextPath) {
    super();
    this.host = host;
    this.port = port;
    this.contextPath = contextPath;
    httpClient = HttpClientBuilder.create().build();
    cookieStore = new BasicCookieStore();
    httpContext = new BasicHttpContext();
    httpContext.setAttribute(HttpClientContext.COOKIE_STORE, getCookieStore());
    statefulHttpComponentsClientHttpRequestFactory =
        new StatefulHttpComponentsClientHttpRequestFactory(httpClient, httpContext);
    super.setRequestFactory(statefulHttpComponentsClientHttpRequestFactory);

    // We set the interceptors here so they capture the non-overridden methods
    final var interceptors = new ArrayList<>(super.getInterceptors());
    interceptors.add(new BasicAuthenticationInterceptor(USERNAME, PASSWORD));
    super.setInterceptors(interceptors);
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }

  public CookieStore getCookieStore() {
    return cookieStore;
  }

  public HttpContext getHttpContext() {
    return httpContext;
  }

  public StatefulHttpComponentsClientHttpRequestFactory getStatefulHttpClientRequestFactory() {
    return statefulHttpComponentsClientHttpRequestFactory;
  }

  public URI getURL(final String urlPart) {
    try {
      final String path;
      if (contextPath.endsWith("/") && urlPart.startsWith("/")) {
        final var subUrlPart = urlPart.substring(1);
        path = contextPath + subUrlPart;
      } else {
        path = contextPath + urlPart;
      }

      return new URL(String.format("http://%s:%s%s", host, port, path)).toURI();
    } catch (final URISyntaxException | MalformedURLException e) {
      throw new RuntimeException("Error occurred while constructing URL", e);
    }
  }

  public URI getURL(final String urlPart, final String urlParams) {
    if (StringUtils.isEmpty(urlParams)) {
      return getURL(urlPart);
    }
    try {
      return new URL(String.format("%s?%s", getURL(urlPart), urlParams)).toURI();
    } catch (final URISyntaxException | MalformedURLException e) {
      throw new RuntimeException("Error occurred while constructing URL", e);
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.performance.util;

import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.springframework.web.client.RestTemplate;

/**
 * Stateful Rest template that you can use to remember cookies, once you log in
 * with it will remember the JSESSIONID and sent it on subsequent requests.
 */
public class StatefulRestTemplate extends RestTemplate {

  private final HttpClient httpClient;
  private final CookieStore cookieStore;
  private final HttpContext httpContext;
  private final StatefulHttpComponentsClientHttpRequestFactory statefulHttpComponentsClientHttpRequestFactory;

  public StatefulRestTemplate() {
    super();
    httpClient = HttpClientBuilder.create().build();
    cookieStore = new BasicCookieStore();
    httpContext = new BasicHttpContext();
    httpContext.setAttribute(HttpClientContext.COOKIE_STORE, getCookieStore());
    statefulHttpComponentsClientHttpRequestFactory = new StatefulHttpComponentsClientHttpRequestFactory(httpClient, httpContext);
    super.setRequestFactory(statefulHttpComponentsClientHttpRequestFactory);
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
}

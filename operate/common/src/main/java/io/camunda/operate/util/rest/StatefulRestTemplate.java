/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.rest;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.RetryOperation;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.elasticsearch.ElasticsearchException;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Stateful Rest template that you can use to remember cookies, once you log in with it will
 * remember the JSESSIONID and sent it on subsequent requests.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class StatefulRestTemplate extends RestTemplate {

  private static final String LOGIN_URL_PATTERN = "/api/login?username=%s&password=%s";
  private static final String CSRF_TOKEN_HEADER_NAME = "X-CSRF-TOKEN";

  private static final String USERNAME_DEFAULT = "demo";
  private static final String PASSWORD_DEFAULT = "demo";

  private final String host;
  private final Integer port;
  private final HttpClient httpClient;
  private final CookieStore cookieStore;
  private final HttpContext httpContext;
  private final StatefulHttpComponentsClientHttpRequestFactory
      statefulHttpComponentsClientHttpRequestFactory;
  private String csrfToken;
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

  @Override
  public <T> ResponseEntity<T> postForEntity(
      final URI url, final Object request, final Class<T> responseType) throws RestClientException {
    final RequestEntity<Object> requestEntity =
        RequestEntity.method(HttpMethod.POST, url)
            .headers(getCsrfHeader())
            .contentType(MediaType.APPLICATION_JSON)
            .body(request);
    final ResponseEntity<T> tResponseEntity = exchange(requestEntity, responseType);
    saveCSRFTokenWhenAvailable(tResponseEntity);
    return tResponseEntity;
  }

  @Override
  public <T> ResponseEntity<T> exchange(
      final RequestEntity<?> requestEntity, final Class<T> responseType)
      throws RestClientException {
    final ResponseEntity<T> responseEntity = super.exchange(requestEntity, responseType);
    saveCSRFTokenWhenAvailable(responseEntity);
    return responseEntity;
  }

  @Override
  public <T> RequestCallback httpEntityCallback(final Object requestBody, final Type responseType) {
    final HttpEntity httpEntity;
    if (requestBody != null) {
      final HttpHeaders headers = new HttpHeaders();
      headers.add(CSRF_TOKEN_HEADER_NAME, csrfToken);
      if (requestBody instanceof HttpEntity<?>) {
        httpEntity = new HttpEntity(((HttpEntity<?>) requestBody).getBody(), headers);
      } else {
        httpEntity = new HttpEntity(requestBody, headers);
      }
    } else {
      httpEntity = null;
    }
    return super.httpEntityCallback(httpEntity, responseType);
  }

  public StatefulHttpComponentsClientHttpRequestFactory getStatefulHttpClientRequestFactory() {
    return statefulHttpComponentsClientHttpRequestFactory;
  }

  public void loginWhenNeeded() {
    loginWhenNeeded(USERNAME_DEFAULT, PASSWORD_DEFAULT);
  }

  public void loginWhenNeeded(final String username, final String password) {
    // log in only once
    if (getCookieStore().getCookies().isEmpty()) {
      final ResponseEntity<Object> response = tryLoginAs(username, password);
      if (!response.getStatusCode().equals(HttpStatus.NO_CONTENT)) {
        throw new OperateRuntimeException(
            String.format(
                "Unable to login user %s to %s:%s. Response: %s", username, host, port, response));
      }
      saveCSRFTokenWhenAvailable(response);
    }
  }

  private ResponseEntity<Object> tryLoginAs(final String username, final String password) {
    try {
      return RetryOperation.<ResponseEntity<Object>>newBuilder()
          .retryConsumer(
              () ->
                  postForEntity(
                      getURL(String.format(LOGIN_URL_PATTERN, username, password)),
                      null,
                      Object.class))
          // retry for 5 minutes
          .noOfRetry(50)
          .delayInterval(6, TimeUnit.SECONDS)
          .retryOn(IOException.class, RestClientException.class, ElasticsearchException.class)
          .message("StatefulRestTemplate#tryLoginAs")
          .build()
          .retry();
    } catch (final Exception e) {
      throw new OperateRuntimeException("Unable to connect to Operate ", e);
    }
  }

  private ResponseEntity<?> saveCSRFTokenWhenAvailable(final ResponseEntity<?> response) {
    final List<String> csrfHeaders = response.getHeaders().get(CSRF_TOKEN_HEADER_NAME);
    if (csrfHeaders != null && !csrfHeaders.isEmpty()) {
      csrfToken = csrfHeaders.get(0);
    }
    return response;
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

  public Consumer<HttpHeaders> getCsrfHeader() {
    return (header) -> header.add(CSRF_TOKEN_HEADER_NAME, csrfToken);
  }
}

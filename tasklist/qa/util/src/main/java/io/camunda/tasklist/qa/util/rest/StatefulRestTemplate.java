/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.util.rest;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.function.Consumer;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.springframework.context.annotation.Scope;
import org.springframework.http.*;
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

  private static final String LOGIN_URL_PATTERN = "/api/login?username=%s&password=%s";
  private static final String CSRF_TOKEN_HEADER_NAME = "TASKLIST-X-CSRF-TOKEN";

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
  private String contextPath;

  public StatefulRestTemplate(String host, Integer port, String contextPath) {
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

  public StatefulHttpComponentsClientHttpRequestFactory getStatefulHttpClientRequestFactory() {
    return statefulHttpComponentsClientHttpRequestFactory;
  }

  public void loginWhenNeeded() {
    loginWhenNeeded(USERNAME_DEFAULT, PASSWORD_DEFAULT);
  }

  public void loginWhenNeeded(String username, String password) {
    // log in only once
    if (getCookieStore().getCookies().isEmpty()) {
      final ResponseEntity<Object> response = tryLoginAs(username, password);
      if (!response.getStatusCode().equals(HttpStatus.NO_CONTENT)) {
        throw new TasklistRuntimeException(
            String.format(
                "Unable to login user %s to %s:%s. Response: %s", username, host, port, response));
      }
      //      saveCSRFTokenWhenAvailable(response);
    }
  }

  private ResponseEntity<Object> tryLoginAs(final String username, final String password) {
    try {
      return postForEntity(
          getURL(String.format(LOGIN_URL_PATTERN, username, password)), null, Object.class);
    } catch (Exception e) {
      throw new TasklistRuntimeException("Unable to connect to Operate ", e);
    }
  }

  public URI getURL(String urlPart) {
    try {
      final String path;
      if (contextPath.endsWith("/") && urlPart.startsWith("/")) {
        final var subUrlPart = urlPart.substring(1);
        path = contextPath + subUrlPart;
      } else {
        path = contextPath + urlPart;
      }

      return new URL(String.format("http://%s:%s%s", host, port, path)).toURI();
    } catch (URISyntaxException | MalformedURLException e) {
      throw new RuntimeException("Error occurred while constructing URL", e);
    }
  }

  public URI getURL(String urlPart, String urlParams) {
    if (StringUtils.isEmpty(urlParams)) {
      return getURL(urlPart);
    }
    try {
      return new URL(String.format("%s?%s", getURL(urlPart), urlParams)).toURI();
    } catch (URISyntaxException | MalformedURLException e) {
      throw new RuntimeException("Error occurred while constructing URL", e);
    }
  }

  public Consumer<HttpHeaders> getCsrfHeader() {
    return (header) -> header.add(CSRF_TOKEN_HEADER_NAME, csrfToken);
  }
}

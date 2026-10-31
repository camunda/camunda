/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.function.Consumer;

public class TestWebappClient {

  private final URI endpoint;

  public TestWebappClient(final URI endpoint) {
    this.endpoint = endpoint;
  }

  public TestLoggedInWebappClient logIn(String username, String password) {

    final var cookieManager = new CookieManager();
    final var httpClient = HttpClient.newBuilder().cookieHandler(cookieManager).build();
    final var loginCsrfToken = requestLoginCsrfToken(httpClient);
    final var loginRequest = buildLoginRequest(username, password, loginCsrfToken);

    final var loginResponse = sendRequestAndThrowExceptionOnFailure(httpClient, loginRequest);

    // The login response only re-issues a token when it rotated one; otherwise the token fetched
    // for the login POST stays valid for the session this client hands back.
    final var csrfToken =
        loginResponse.headers().firstValue(WebSecurityConfig.X_CSRF_TOKEN).orElse(loginCsrfToken);

    return new TestLoggedInWebappClient(httpClient, cookieManager, csrfToken);
  }

  /**
   * Fetches the CSRF token the login endpoint issues on a GET, which the login POST has to echo
   * back: the login path enforces CSRF unconditionally, so a POST without a token is rejected even
   * before a session exists. Returns {@code null} when the deployment issues no token (CSRF
   * disabled), leaving the login POST unchanged for that shape.
   */
  private String requestLoginCsrfToken(final HttpClient httpClient) {
    final var tokenRequest = HttpRequest.newBuilder().uri(endpoint.resolve("login")).GET().build();
    return sendRequest(httpClient, tokenRequest)
        .map(response -> response.headers().firstValue(WebSecurityConfig.X_CSRF_TOKEN).orElse(null))
        .getOrElse(null);
  }

  private HttpRequest buildLoginRequest(
      final String username, final String password, final String csrfToken) {
    final var request =
        HttpRequest.newBuilder()
            .uri(endpoint.resolve("login"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "username=" + username + "&password=" + password));
    if (csrfToken != null) {
      request.header(WebSecurityConfig.X_CSRF_TOKEN, csrfToken);
    }
    return request.build();
  }

  private static Either<Exception, HttpResponse<String>> sendRequest(
      final HttpClient httpClient, final HttpRequest request) {
    try {
      return Either.right(httpClient.send(request, BodyHandlers.ofString()));
    } catch (final IOException | InterruptedException e) {
      return Either.left(e);
    }
  }

  private static HttpResponse<String> sendRequestAndThrowExceptionOnFailure(
      final HttpClient httpClient, final HttpRequest request) {
    final Either<Exception, HttpResponse<String>> result = sendRequest(httpClient, request);

    if (result.isLeft()) {
      throw new RuntimeException(result.getLeft());
    } else {
      return result.get();
    }
  }

  public class TestLoggedInWebappClient implements AutoCloseable {

    private final HttpClient httpClient;
    private final CookieManager cookieManager;
    private final String csrfToken;

    public TestLoggedInWebappClient(
        HttpClient httpClient, CookieManager cookieManager, String csrfToken) {
      this.httpClient = httpClient;
      this.cookieManager = cookieManager;
      this.csrfToken = csrfToken;
    }

    public String getCsrfToken() {
      return csrfToken;
    }

    public HttpCookie getSessionCookie() {
      return findCookie(WebSecurityConfig.SESSION_COOKIE);
    }

    public HttpCookie getCsrfCookie() {
      return findCookie(WebSecurityConfig.X_CSRF_TOKEN);
    }

    public URI getRootEndpoint() {
      return endpoint;
    }

    public List<HttpCookie> getCookies() {
      return cookieManager.getCookieStore().getCookies();
    }

    private HttpCookie findCookie(final String cookieName) {
      final var cookies = getCookies();

      return cookies.stream()
          .filter(c -> cookieName.equals(c.getName()))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Cookie not found: " + cookieName));
    }

    public Either<Exception, HttpResponse<String>> send(
        String path, Consumer<HttpRequest.Builder> requestModifier) {
      final Builder requestBuilder = HttpRequest.newBuilder().uri(endpoint.resolve(path));
      requestModifier.accept(requestBuilder);

      final HttpRequest request = requestBuilder.build();

      return sendRequest(httpClient, request);
    }

    public Either<Exception, HttpResponse<String>> send(String path) {
      return send(path, builder -> {});
    }

    @Override
    public void close() {
      httpClient.close();
    }
  }
}

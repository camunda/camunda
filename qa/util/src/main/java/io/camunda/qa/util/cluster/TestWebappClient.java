/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import io.camunda.security.spring.security.CamundaSecurityFilterChainConstants;
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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

public class TestWebappClient {

  // Generous upper bound, not the expected wait. On ES/OS the default user only becomes
  // searchable once it is exported and the index refreshes (gated behind schema init and the
  // exporter's backoff), which can lag startup by several seconds under CI load. The retry is
  // condition-based and returns as soon as login is accepted, so this only bites when something
  // is genuinely wrong.
  private static final Duration LOGIN_TIMEOUT = Duration.ofSeconds(60);

  private final URI endpoint;

  public TestWebappClient(final URI endpoint) {
    this.endpoint = endpoint;
  }

  public TestLoggedInWebappClient logIn(final String username, final String password) {

    final var cookieManager = new CookieManager();
    final var httpClient = HttpClient.newBuilder().cookieHandler(cookieManager).build();
    final var loginRequest = buildLoginRequest(username, password);
    final var lastResponse = new AtomicReference<HttpResponse<String>>();

    // Retry until the login is accepted (see LOGIN_TIMEOUT): until the user is searchable the
    // username lookup misses and login is rejected, and handing back an unauthenticated client here
    // would only surface later as a confusing 401 on the first authenticated request.
    try {
      Awaitility.await("login of user '%s'".formatted(username))
          .atMost(LOGIN_TIMEOUT)
          .pollDelay(Duration.ZERO)
          .pollInterval(Duration.ofMillis(100))
          .until(
              () -> {
                lastResponse.set(sendRequestAndThrowExceptionOnFailure(httpClient, loginRequest));
                return isSuccessful(lastResponse.get());
              });
    } catch (final ConditionTimeoutException e) {
      final var response = lastResponse.get();
      throw new IllegalStateException(
          "Login of user '%s' did not succeed within %s; last response status was %s"
              .formatted(username, LOGIN_TIMEOUT, response == null ? "n/a" : response.statusCode()),
          e);
    }

    final var csrfToken =
        lastResponse
            .get()
            .headers()
            .firstValue(CamundaSecurityFilterChainConstants.X_CSRF_TOKEN)
            .orElse(null);

    return new TestLoggedInWebappClient(httpClient, cookieManager, csrfToken);
  }

  private HttpRequest buildLoginRequest(final String username, final String password) {
    return HttpRequest.newBuilder()
        .uri(endpoint.resolve("login"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString("username=" + username + "&password=" + password))
        .build();
  }

  private static boolean isSuccessful(final HttpResponse<?> response) {
    final int status = response.statusCode();
    return status >= 200 && status < 300;
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
      return findCookie(CamundaSecurityFilterChainConstants.SESSION_COOKIE);
    }

    public HttpCookie getCsrfCookie() {
      return findCookie(CamundaSecurityFilterChainConstants.X_CSRF_TOKEN);
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

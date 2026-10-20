/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Runs the browser authorization code flow against a Keycloak login form, with the real login
 * callback, and reads the username of the session from {@code /v2/authentication/me}.
 */
final class OidcBrowserLogin {

  private static final Pattern LOGIN_FORM =
      Pattern.compile("<form[^>]*kc-form-login[^>]*action=\"([^\"]+)\"");

  private OidcBrowserLogin() {}

  /**
   * @param restAddress the REST address of the application
   * @param pathPrefix the path of the physical tenant, or an empty string for the root
   * @param registrationId the OIDC registration to log in with
   */
  static String loginAndReadUsername(
      final URI restAddress,
      final String pathPrefix,
      final String registrationId,
      final String username,
      final String password)
      throws Exception {
    try (final var app = new Browser();
        final var keycloak = new Browser()) {
      return loginAndReadUsername(
          app, keycloak, restAddress, pathPrefix, registrationId, username, password);
    }
  }

  private static String loginAndReadUsername(
      final Browser app,
      final Browser keycloak,
      final URI restAddress,
      final String pathPrefix,
      final String registrationId,
      final String username,
      final String password)
      throws Exception {

    // the app answers the login start with a redirect to the IdP and remembers the request
    final var authorizationRedirect =
        app.send(
            get(restAddress, pathPrefix + "/oauth2/authorization/" + registrationId),
            BodyHandlers.discarding());
    assertThat(authorizationRedirect.statusCode()).isEqualTo(302);
    final var authorizationUrl = location(authorizationRedirect);

    // the user signs in on the IdP's login form
    final var loginPage =
        keycloak.send(
            HttpRequest.newBuilder(URI.create(authorizationUrl)).GET(), BodyHandlers.ofString());
    final var matcher = LOGIN_FORM.matcher(loginPage.body());
    assertThat(matcher.find()).as("Keycloak login form in %s", loginPage.body()).isTrue();
    final var signIn =
        keycloak.send(
            HttpRequest.newBuilder(URI.create(matcher.group(1).replace("&amp;", "&")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "username=" + username + "&password=" + password)),
            BodyHandlers.ofString());
    assertThat(signIn.statusCode()).isEqualTo(302);
    final var callbackUrl = location(signIn);

    // the browser returns to the app's callback with the session of the login start
    final var callback =
        app.send(HttpRequest.newBuilder(URI.create(callbackUrl)).GET(), BodyHandlers.discarding());
    assertThat(callback.statusCode()).isEqualTo(302);

    final var me =
        app.send(
            HttpRequest.newBuilder(restAddress.resolve(pathPrefix + "/v2/authentication/me"))
                .header("Accept", "application/json")
                .GET(),
            BodyHandlers.ofString());
    assertThat(me.statusCode()).as("GET me on '%s': %s", pathPrefix, me.body()).isEqualTo(200);
    return new ObjectMapper().readTree(me.body()).get("username").asText();
  }

  /** Whether the application already answers the login start of {@code registrationId}. */
  static boolean isLoginServed(
      final URI restAddress, final String pathPrefix, final String registrationId)
      throws Exception {
    try (final var browser = new Browser()) {
      return browser
              .send(
                  get(restAddress, pathPrefix + "/oauth2/authorization/" + registrationId),
                  BodyHandlers.discarding())
              .statusCode()
          == 302;
    }
  }

  private static HttpRequest.Builder get(final URI restAddress, final String path) {
    return HttpRequest.newBuilder(restAddress.resolve(path)).header("Accept", "text/html").GET();
  }

  private static String location(final HttpResponse<?> response) {
    return response.headers().firstValue("location").orElseThrow();
  }

  /**
   * A browser stand-in that replays the cookies a server set, by name only. The JDK cookie manager
   * does not send Keycloak's login cookies back over plain HTTP.
   */
  private static final class Browser implements AutoCloseable {
    private final HttpClient client =
        HttpClient.newBuilder().followRedirects(Redirect.NEVER).build();
    private final Map<String, String> cookies = new LinkedHashMap<>();

    <T> HttpResponse<T> send(final HttpRequest.Builder request, final BodyHandler<T> handler)
        throws Exception {
      if (!cookies.isEmpty()) {
        request.header(
            "Cookie",
            cookies.entrySet().stream()
                .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                .collect(Collectors.joining("; ")));
      }
      final var response = client.send(request.build(), handler);
      response.headers().allValues("set-cookie").forEach(this::store);
      return response;
    }

    @Override
    public void close() {
      client.close();
    }

    private void store(final String setCookie) {
      final var attributes = setCookie.split(";");
      final var nameAndValue = attributes[0].split("=", 2);
      final var name = nameAndValue[0].trim();
      final var expired =
          nameAndValue.length < 2
              || nameAndValue[1].isBlank()
              || Arrays.stream(attributes)
                  .skip(1)
                  .anyMatch(attribute -> attribute.trim().equalsIgnoreCase("Max-Age=0"));
      if (expired) {
        cookies.remove(name);
      } else {
        cookies.put(name, nameAndValue[1].trim());
      }
    }
  }
}

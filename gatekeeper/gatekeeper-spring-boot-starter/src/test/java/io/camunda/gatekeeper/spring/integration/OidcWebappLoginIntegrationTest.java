/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gatekeeper.spring.integration.app.TestComponentApplication;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * OIDC webapp login integration test that performs the full OAuth2 authorization code redirect flow
 * against a Keycloak Testcontainer. Uses Java {@link HttpClient} with manual redirect following and
 * cookie-based session tracking.
 */
@SpringBootTest(
    classes = TestComponentApplication.class,
    webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("oidc")
final class OidcWebappLoginIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern FORM_ACTION_PATTERN =
      Pattern.compile("<form[^>]*action=\"([^\"]+)\"");

  @LocalServerPort private int port;

  private HttpClient httpClient;

  @DynamicPropertySource
  static void configureOidc(final DynamicPropertyRegistry registry) {
    KeycloakTestSupport.configureOidc(registry);
  }

  @BeforeEach
  void setUp() {
    httpClient =
        HttpClient.newBuilder()
            .cookieHandler(new PermissiveCookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @AfterEach
  void tearDown() throws Exception {
    httpClient.close();
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  @Nested
  @DisplayName("OAuth2 webapp login flow")
  class WebappLoginTests {

    @Test
    @DisplayName("accessing protected webapp path redirects to OAuth2 authorization")
    void protectedWebappPathShouldRedirectToAuthorization() throws Exception {
      final var response = httpGet(baseUrl() + "/app/test/identity");

      assertThat(response.statusCode()).isEqualTo(302);
      assertThat(response.headers().firstValue("Location"))
          .isPresent()
          .get()
          .asString()
          .contains("/oauth2/authorization/");
    }

    @Test
    @DisplayName("full login flow results in authenticated session")
    void fullLoginFlowShouldEstablishAuthenticatedSession() throws Exception {
      // Step 1: Access protected webapp path → redirect to /oauth2/authorization/{registrationId}
      final var step1 = httpGet(baseUrl() + "/app/test/identity");
      assertThat(step1.statusCode()).isEqualTo(302);
      final var authorizationUrl = step1.headers().firstValue("Location").orElseThrow();

      // Step 2: Follow to Spring's authorization endpoint → redirect to Keycloak
      final var step2 = httpGet(resolveUrl(authorizationUrl));
      assertThat(step2.statusCode()).isEqualTo(302);
      final var keycloakAuthUrl = step2.headers().firstValue("Location").orElseThrow();

      // Step 3: GET Keycloak's authorization page → login form HTML
      final var step3 = httpGet(keycloakAuthUrl);
      assertThat(step3.statusCode()).isEqualTo(200);
      final var loginFormAction = extractFormAction(step3.body());

      // Step 4: POST credentials to Keycloak → redirect back to app with auth code
      final var step4 = httpPostForm(loginFormAction, "username=demo&password=demo");
      assertThat(step4.statusCode()).isEqualTo(302);
      final var callbackUrl = step4.headers().firstValue("Location").orElseThrow();
      assertThat(callbackUrl).contains("/sso-callback").contains("code=");

      // Step 5: Follow callback → Spring exchanges code for tokens, redirects
      final var step5 = httpGet(callbackUrl);
      assertThat(step5.statusCode()).isEqualTo(302);
      final var finalRedirect = step5.headers().firstValue("Location").orElseThrow();

      // Step 6: Follow final redirect → authenticated response
      final var step6 = httpGet(resolveUrl(finalRedirect));
      assertThat(step6.statusCode()).isEqualTo(200);

      // Verify the response contains the authenticated user's identity
      @SuppressWarnings("unchecked")
      final Map<String, Object> identity = MAPPER.readValue(step6.body(), Map.class);
      assertThat(identity.get("username")).isEqualTo("demo");
      assertThat(identity.get("anonymous")).isEqualTo(false);
    }
  }

  private HttpResponse<String> httpGet(final String url) throws Exception {
    final var request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> httpPostForm(final String url, final String formBody)
      throws Exception {
    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private String resolveUrl(final String url) {
    if (url.startsWith("http")) {
      return url;
    }
    return baseUrl() + url;
  }

  private static String extractFormAction(final String html) {
    final var matcher = FORM_ACTION_PATTERN.matcher(html);
    if (!matcher.find()) {
      throw new IllegalStateException("Could not find form action URL in Keycloak login page HTML");
    }
    return matcher.group(1).replace("&amp;", "&");
  }

  /**
   * A CookieManager that sends all stored cookies on every request, bypassing domain matching. On
   * macOS, Java's default CookieManager stores localhost cookies with domain "localhost.local",
   * which then don't match requests to "localhost" — causing Keycloak to reject the login POST with
   * "Cookie not found".
   */
  private static final class PermissiveCookieManager extends CookieManager {

    PermissiveCookieManager() {
      super(null, CookiePolicy.ACCEPT_ALL);
    }

    @Override
    public Map<String, List<String>> get(
        final URI uri, final Map<String, List<String>> requestHeaders) throws IOException {
      final var result = new HashMap<>(super.get(uri, requestHeaders));
      final List<HttpCookie> allCookies = getCookieStore().getCookies();
      if (!allCookies.isEmpty()) {
        final var cookieHeader =
            allCookies.stream()
                .map(c -> c.getName() + "=" + c.getValue())
                .collect(Collectors.joining("; "));
        result.put("Cookie", List.of(cookieHeader));
      }
      return result;
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.it;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

/** Shared infrastructure for Keycloak integration tests. */
final class KeycloakTestSupport {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private KeycloakTestSupport() {}

  /** Creates a Keycloak testcontainer with optimized startup settings. */
  static KeycloakContainer createKeycloak() {
    return new KeycloakContainer()
        .withStartupTimeout(Duration.ofMinutes(5))
        .withEnv("JAVA_TOOL_OPTIONS", "-Xlog:disable -XX:TieredStopAtLevel=1");
  }

  /**
   * Creates a Keycloak realm with the given clients using the admin API.
   *
   * @param keycloak the running Keycloak container
   * @param realmName the realm name to create
   * @param clients the client representations to register in the realm
   */
  static void createRealm(
      final KeycloakContainer keycloak,
      final String realmName,
      final List<ClientRepresentation> clients) {
    final RealmRepresentation realm = new RealmRepresentation();
    realm.setRealm(realmName);
    realm.setEnabled(true);
    realm.setClients(clients);

    try (Keycloak admin = keycloak.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }

  /**
   * Creates a confidential client representation with service account enabled.
   *
   * @param clientId the client ID
   * @param clientSecret the client secret
   * @return the configured client representation
   */
  static ClientRepresentation createConfidentialClient(
      final String clientId, final String clientSecret) {
    final var client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setSecret(clientSecret);
    client.setEnabled(true);
    client.setServiceAccountsEnabled(true);
    client.setPublicClient(false);
    client.setDirectAccessGrantsEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    return client;
  }

  /**
   * Acquires an access token from Keycloak via the client_credentials grant.
   *
   * @param tokenEndpoint the full token endpoint URL
   * @param clientId the client ID
   * @param clientSecret the client secret
   * @return the access token string
   */
  static String acquireToken(
      final String tokenEndpoint, final String clientId, final String clientSecret) {
    final String body =
        "grant_type=client_credentials&client_id="
            + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&client_secret="
            + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(30))
            .build();

    try {
      final HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "Token acquisition failed with status %d: %s"
                .formatted(response.statusCode(), response.body()));
      }
      final Map<String, Object> tokenResponse =
          OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {});
      return (String) tokenResponse.get("access_token");
    } catch (final IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to acquire token from Keycloak", e);
    }
  }

  /** Builds the token endpoint URL for a given realm. */
  static String tokenEndpoint(final KeycloakContainer keycloak, final String realmName) {
    return keycloak.getAuthServerUrl() + "/realms/" + realmName + "/protocol/openid-connect/token";
  }

  /** Builds the issuer URI for a given realm. */
  static String issuerUri(final KeycloakContainer keycloak, final String realmName) {
    return keycloak.getAuthServerUrl() + "/realms/" + realmName;
  }

  /** Builds the authorization endpoint URL for a given realm. */
  static String authorizationUri(final KeycloakContainer keycloak, final String realmName) {
    return keycloak.getAuthServerUrl() + "/realms/" + realmName + "/protocol/openid-connect/auth";
  }

  /** Builds the JWK Set URI for a given realm. */
  static String jwkSetUri(final KeycloakContainer keycloak, final String realmName) {
    return keycloak.getAuthServerUrl() + "/realms/" + realmName + "/protocol/openid-connect/certs";
  }

  /**
   * Acquires a full token response from Keycloak via the client_credentials grant.
   *
   * @return the full token response map (access_token, refresh_token, etc.)
   */
  static Map<String, Object> acquireTokenResponse(
      final String tokenEndpoint, final String clientId, final String clientSecret) {
    final String body =
        "grant_type=client_credentials&client_id="
            + encode(clientId)
            + "&client_secret="
            + encode(clientSecret);
    return postForTokenResponse(tokenEndpoint, body);
  }

  /**
   * Acquires a token via the resource owner password credentials (ROPC) grant.
   *
   * @return the full token response map
   */
  static Map<String, Object> acquireTokenWithPassword(
      final String tokenEndpoint,
      final String clientId,
      final String clientSecret,
      final String username,
      final String password) {
    final String body =
        "grant_type=password&client_id="
            + encode(clientId)
            + "&client_secret="
            + encode(clientSecret)
            + "&username="
            + encode(username)
            + "&password="
            + encode(password);
    return postForTokenResponse(tokenEndpoint, body);
  }

  /**
   * Refreshes an access token using a refresh_token grant.
   *
   * @return the full token response map
   */
  static Map<String, Object> refreshToken(
      final String tokenEndpoint,
      final String clientId,
      final String clientSecret,
      final String refreshToken) {
    final String body =
        "grant_type=refresh_token&client_id="
            + encode(clientId)
            + "&client_secret="
            + encode(clientSecret)
            + "&refresh_token="
            + encode(refreshToken);
    return postForTokenResponse(tokenEndpoint, body);
  }

  /**
   * Performs an RFC 8693 token exchange.
   *
   * @return the full token response map
   */
  static Map<String, Object> exchangeToken(
      final String tokenEndpoint,
      final String clientId,
      final String clientSecret,
      final String subjectToken,
      final String audience) {
    final StringBuilder body = new StringBuilder();
    body.append("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange")
        .append("&client_id=")
        .append(encode(clientId))
        .append("&client_secret=")
        .append(encode(clientSecret))
        .append("&subject_token=")
        .append(encode(subjectToken))
        .append("&subject_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Aaccess_token");
    if (audience != null) {
      body.append("&audience=").append(encode(audience));
    }
    return postForTokenResponse(tokenEndpoint, body.toString());
  }

  /** PKCE challenge pair. */
  record PkceChallenge(String verifier, String challenge) {}

  /** Generates a PKCE code_verifier and code_challenge (S256). */
  static PkceChallenge generatePkceChallenge() {
    final byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    final String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    try {
      final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      final byte[] digest = sha256.digest(verifier.getBytes(StandardCharsets.US_ASCII));
      final String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
      return new PkceChallenge(verifier, challenge);
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * Performs a complete authorization code flow programmatically: GET authorization endpoint → POST
   * login form → capture authorization code → exchange for tokens with PKCE.
   *
   * @return the full token response map
   */
  static Map<String, Object> performAuthorizationCodeFlow(
      final KeycloakContainer keycloak,
      final String realm,
      final String clientId,
      final String clientSecret,
      final String redirectUri,
      final String username,
      final String password,
      final PkceChallenge pkce) {
    // Use a plain HttpClient without CookieManager - handle cookies manually
    // to work around Java HttpClient cookie handling issues with Keycloak
    final HttpClient httpClient =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Step 1: GET authorization endpoint
    final String authUrl =
        authorizationUri(keycloak, realm)
            + "?response_type=code"
            + "&client_id="
            + encode(clientId)
            + "&redirect_uri="
            + encode(redirectUri)
            + "&scope=openid"
            + "&code_challenge="
            + encode(pkce.challenge())
            + "&code_challenge_method=S256";

    try {
      HttpResponse<String> authResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(authUrl))
                  .GET()
                  .timeout(Duration.ofSeconds(30))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      // Collect all cookies from the response
      final String cookies = collectCookies(authResponse);

      // Follow redirects manually while accumulating cookies
      String allCookies = cookies;
      while (authResponse.statusCode() >= 300 && authResponse.statusCode() < 400) {
        final String loc =
            authResponse
                .headers()
                .firstValue("Location")
                .orElseThrow(() -> new IllegalStateException("Redirect without Location"));
        authResponse =
            httpClient.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(loc))
                    .header("Cookie", allCookies)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        allCookies = mergeCookies(allCookies, collectCookies(authResponse));
      }

      // Step 2: Extract the login form action URL and hidden fields
      final String loginFormAction = extractFormAction(authResponse.body());
      final Map<String, String> hiddenFields = extractHiddenFields(authResponse.body());

      // Step 3: POST login credentials with cookies from the login page
      final StringBuilder loginBody = new StringBuilder();
      loginBody.append("username=").append(encode(username));
      loginBody.append("&password=").append(encode(password));
      for (final var entry : hiddenFields.entrySet()) {
        loginBody
            .append("&")
            .append(encode(entry.getKey()))
            .append("=")
            .append(encode(entry.getValue()));
      }
      final HttpResponse<String> loginResponse =
          httpClient.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(loginFormAction))
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .header("Cookie", allCookies)
                  .POST(HttpRequest.BodyPublishers.ofString(loginBody.toString()))
                  .timeout(Duration.ofSeconds(30))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      // Step 4: Extract authorization code from redirect Location header
      // If status is not a redirect, the login form submission failed
      if (loginResponse.statusCode() >= 400) {
        // May need to follow through another page (e.g., consent screen)
        // Try extracting a form action from the response body if it's another page
        if (loginResponse.body() != null && loginResponse.body().contains("action=\"")) {
          // Follow any additional forms (consent, MFA, etc.)
          final String nextAction = extractFormAction(loginResponse.body());
          final Map<String, String> nextHidden = extractHiddenFields(loginResponse.body());
          final StringBuilder nextBody = new StringBuilder();
          for (final var entry : nextHidden.entrySet()) {
            if (!nextBody.isEmpty()) {
              nextBody.append("&");
            }
            nextBody.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
          }
          final var nextResponse =
              httpClient.send(
                  HttpRequest.newBuilder()
                      .uri(URI.create(nextAction))
                      .header("Content-Type", "application/x-www-form-urlencoded")
                      .POST(HttpRequest.BodyPublishers.ofString(nextBody.toString()))
                      .timeout(Duration.ofSeconds(30))
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
          if (nextResponse.headers().firstValue("Location").isPresent()) {
            final var loc = nextResponse.headers().firstValue("Location").get();
            final URI locUri = URI.create(loc);
            final String codeFromNext = extractQueryParam(locUri.getQuery(), "code");
            if (codeFromNext != null) {
              return exchangeCodeForTokens(
                  keycloak, realm, clientId, clientSecret, redirectUri, codeFromNext, pkce);
            }
          }
        }
        // Build detailed error message with form analysis
        final String respBody = loginResponse.body() != null ? loginResponse.body() : "";
        final String errorMsg =
            respBody.contains("kc-form-login")
                ? "Login form found but submission returned 400. "
                    + "Form action: "
                    + (respBody.contains("action=\"") ? extractFormAction(respBody) : "NOT FOUND")
                    + ", Hidden fields: "
                    + extractHiddenFields(respBody)
                    + ", Error messages: "
                    + extractKeycloakErrors(respBody)
                : "No login form found in response (status=" + loginResponse.statusCode() + ")";
        throw new IllegalStateException(errorMsg);
      }
      final String location =
          loginResponse
              .headers()
              .firstValue("Location")
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "No Location header in login response (status="
                              + loginResponse.statusCode()
                              + ")"));
      final URI locationUri = URI.create(location);
      final String code = extractQueryParam(locationUri.getQuery(), "code");
      if (code == null) {
        throw new IllegalStateException("No code parameter in redirect: " + location);
      }

      // Step 5: Exchange code for tokens with PKCE verifier
      return exchangeCodeForTokens(
          keycloak, realm, clientId, clientSecret, redirectUri, code, pkce);
    } catch (final IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Authorization code flow failed", e);
    }
  }

  private static Map<String, Object> exchangeCodeForTokens(
      final KeycloakContainer keycloak,
      final String realm,
      final String clientId,
      final String clientSecret,
      final String redirectUri,
      final String code,
      final PkceChallenge pkce) {
    final String tokenBody =
        "grant_type=authorization_code"
            + "&client_id="
            + encode(clientId)
            + "&client_secret="
            + encode(clientSecret)
            + "&code="
            + encode(code)
            + "&redirect_uri="
            + encode(redirectUri)
            + "&code_verifier="
            + encode(pkce.verifier());
    return postForTokenResponse(tokenEndpoint(keycloak, realm), tokenBody);
  }

  /**
   * Creates a user in the given Keycloak realm via the admin API.
   *
   * @param keycloak the Keycloak container
   * @param realm the realm name
   * @param username the username
   * @param password the password
   */
  static void createUser(
      final KeycloakContainer keycloak,
      final String realm,
      final String username,
      final String password) {
    try (Keycloak admin = keycloak.getKeycloakAdminClient()) {
      final var user = new UserRepresentation();
      user.setUsername(username);
      user.setEmail(username + "@test.local");
      user.setFirstName(username);
      user.setLastName("Test");
      user.setEnabled(true);
      user.setEmailVerified(true);
      user.setRequiredActions(List.of());

      final var credential = new CredentialRepresentation();
      credential.setType(CredentialRepresentation.PASSWORD);
      credential.setValue(password);
      credential.setTemporary(false);

      user.setCredentials(List.of(credential));
      admin.realm(realm).users().create(user);
    }
  }

  /**
   * Creates a confidential client configured for authorization_code grant with a redirect URI.
   *
   * @param clientId the client ID
   * @param clientSecret the client secret
   * @param redirectUri the allowed redirect URI
   * @return the client representation
   */
  static ClientRepresentation createAuthorizationCodeClient(
      final String clientId, final String clientSecret, final String redirectUri) {
    final var client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setSecret(clientSecret);
    client.setEnabled(true);
    client.setPublicClient(false);
    client.setServiceAccountsEnabled(false);
    client.setStandardFlowEnabled(true);
    client.setDirectAccessGrantsEnabled(true);
    client.setRedirectUris(List.of(redirectUri));
    client.setClientAuthenticatorType("client-secret");
    return client;
  }

  /**
   * Creates a confidential client configured for token exchange.
   *
   * @param clientId the client ID
   * @param clientSecret the client secret
   * @return the client representation
   */
  static ClientRepresentation createTokenExchangeClient(
      final String clientId, final String clientSecret) {
    final var client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setSecret(clientSecret);
    client.setEnabled(true);
    client.setPublicClient(false);
    client.setServiceAccountsEnabled(true);
    client.setDirectAccessGrantsEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    // Enable standard token exchange (RFC 8693) on this client
    client.setAttributes(
        Map.of("oidc.ciba.grant.enabled", "false", "standard.token.exchange.enabled", "true"));
    return client;
  }

  // -- Private helpers --

  private static String encode(final String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static Map<String, Object> postForTokenResponse(
      final String tokenEndpoint, final String body) {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(30))
            .build();

    try {
      final HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "Token request failed with status %d: %s"
                .formatted(response.statusCode(), response.body()));
      }
      return OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {});
    } catch (final IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to perform token request", e);
    }
  }

  /**
   * Sends a POST token request and returns the HTTP status code and body, without throwing on
   * non-200 responses. Used for negative test cases.
   */
  static TokenResponse postTokenRequest(final String tokenEndpoint, final String body) {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenEndpoint))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(30))
            .build();

    try {
      final HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      return new TokenResponse(response.statusCode(), response.body());
    } catch (final IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to perform token request", e);
    }
  }

  record TokenResponse(int statusCode, String body) {}

  private static String extractFormAction(final String html) {
    final Pattern pattern = Pattern.compile("action=\"([^\"]+)\"");
    final Matcher matcher = pattern.matcher(html);
    if (!matcher.find()) {
      throw new IllegalStateException("No form action found in login page HTML");
    }
    return matcher.group(1).replace("&amp;", "&");
  }

  private static Map<String, String> extractHiddenFields(final String html) {
    final Map<String, String> fields = new HashMap<>();
    final Pattern pattern =
        Pattern.compile("<input[^>]+type=[\"']hidden[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    final Matcher matcher = pattern.matcher(html);
    while (matcher.find()) {
      final String input = matcher.group();
      final String name = extractAttribute(input, "name");
      final String value = extractAttribute(input, "value");
      if (name != null) {
        fields.put(name, value != null ? value : "");
      }
    }
    return fields;
  }

  private static String collectCookies(final HttpResponse<?> response) {
    return response.headers().allValues("Set-Cookie").stream()
        .map(cookie -> cookie.split(";")[0])
        .reduce((a, b) -> a + "; " + b)
        .orElse("");
  }

  private static String mergeCookies(final String existing, final String additional) {
    if (additional.isEmpty()) {
      return existing;
    }
    if (existing.isEmpty()) {
      return additional;
    }
    return existing + "; " + additional;
  }

  private static String extractKeycloakErrors(final String html) {
    final StringBuilder errors = new StringBuilder();
    // Look for Keycloak error spans: <span id="input-error-..." or class="...error..."
    final Pattern errorPattern =
        Pattern.compile(
            "class=\"[^\"]*(?:alert|error|pf-m-danger)[^\"]*\"[^>]*>([^<]+)",
            Pattern.CASE_INSENSITIVE);
    final Matcher matcher = errorPattern.matcher(html);
    while (matcher.find()) {
      final String text = matcher.group(1).trim();
      if (!text.isEmpty()) {
        if (!errors.isEmpty()) {
          errors.append("; ");
        }
        errors.append(text);
      }
    }
    return errors.isEmpty() ? "none" : errors.toString();
  }

  private static String extractAttribute(final String tag, final String attrName) {
    final Pattern pattern =
        Pattern.compile(attrName + "=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    final Matcher matcher = pattern.matcher(tag);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static String extractQueryParam(final String query, final String paramName) {
    if (query == null) {
      return null;
    }
    for (final String param : query.split("&")) {
      final String[] keyValue = param.split("=", 2);
      if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
        return keyValue[1];
      }
    }
    return null;
  }
}

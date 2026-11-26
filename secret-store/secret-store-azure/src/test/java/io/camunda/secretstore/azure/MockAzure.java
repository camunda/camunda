/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.ValueMatcher;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Microsoft Entra ID, IMDS and Key Vault, mocked by one WireMock server: HTTPS for Entra ID and the
 * vaults, plain HTTP for IMDS, as in Azure. Clients reach it through {@link #httpClient()}, which
 * keeps Azure's host names in every request; the stubs tell the services apart by them.
 */
final class MockAzure implements AutoCloseable {

  static final String VAULT_RESOURCE = "https://vault.azure.net";

  private static final String ORIGINAL_HOST = RoutedHttpClient.ORIGINAL_HOST;
  private static final String IMDS_TOKEN_PATH = "/metadata/identity/oauth2/token";
  private static final String RESPONSE_TEMPLATE = "response-template";

  private final WireMockServer server;
  private final TestCertificate certificate;

  private MockAzure(final WireMockServer server, final TestCertificate certificate) {
    this.server = server;
    this.certificate = certificate;
  }

  static MockAzure start(final Path directory) throws Exception {
    final var certificate = TestCertificate.generate(directory);
    final var server =
        new WireMockServer(
            options()
                .port(0)
                .httpsPort(0)
                .keystorePath(certificate.keystore().toString())
                .keystoreType("PKCS12")
                .keystorePassword(certificate.password())
                .keyManagerPassword(certificate.password()));
    server.start();
    return new MockAzure(server, certificate);
  }

  /** A client that sends every call to Azure to this mock, trusting only its certificate. */
  RoutedHttpClient httpClient() {
    return new RoutedHttpClient(certificate.certificate(), server.httpsPort(), server.port());
  }

  /** Entra ID issues {@code token} to {@code clientId} of {@code tenantId}. */
  void entraIssues(
      final String tenantId, final String clientId, final String token, final long expiresIn) {
    server.stubFor(
        post(urlPathEqualTo("/" + tenantId + "/oauth2/v2.0/token"))
            .withHeader(ORIGINAL_HOST, equalTo(RoutedHttpClient.AUTHORITY_HOST))
            .withRequestBody(containing("client_id=" + clientId))
            .willReturn(
                okJson(
                        """
                    {"token_type":"Bearer","expires_in":%1$d,"ext_expires_in":%1$d,"access_token":"%2$s"}
                    """
                            .formatted(expiresIn, token))
                    .withTransformers(RESPONSE_TEMPLATE)));
  }

  /**
   * IMDS issues {@code token} to the user-assigned identity {@code clientId}, or with {@code null}
   * to the system-assigned identity.
   */
  void imdsIssues(final @Nullable String clientId, final String token, final long expiresIn) {
    stubImds(clientId, token, expiresIn);
  }

  /**
   * Entra ID issues {@code clientId} of {@code tenantId} a new token on every request, valid for
   * {@code lifetimeSeconds}: {@code <tokenPrefix>.<expiry in epoch millis>}, which {@link
   * #vaultHoldsForUnexpiredTokens} checks.
   */
  void entraIssuesShortLivedTokens(
      final String tenantId,
      final String clientId,
      final String tokenPrefix,
      final long lifetimeSeconds) {
    entraIssues(tenantId, clientId, shortLivedToken(tokenPrefix, lifetimeSeconds), lifetimeSeconds);
  }

  /** As {@link #entraIssuesShortLivedTokens}, from IMDS to the user-assigned {@code clientId}. */
  void imdsIssuesShortLivedTokens(
      final String clientId, final String tokenPrefix, final long lifetimeSeconds) {
    stubImds(clientId, shortLivedToken(tokenPrefix, lifetimeSeconds), lifetimeSeconds);
  }

  private void stubImds(final @Nullable String clientId, final String token, final long expiresIn) {
    server.stubFor(
        get(urlPathEqualTo(IMDS_TOKEN_PATH))
            .withHeader(ORIGINAL_HOST, equalTo(RoutedHttpClient.IMDS_HOST))
            .withHeader("Metadata", equalTo("true"))
            .withQueryParam("client_id", clientId == null ? absent() : equalTo(clientId))
            .willReturn(
                okJson(
                        // expires_on is rendered per response, so a token is never issued expired
                        """
                        {"access_token":"%1$s","expires_in":"%2$d",
                         "expires_on":"{{now offset='%2$d seconds' format='unix'}}",
                         "resource":"%3$s","token_type":"Bearer"}
                        """
                            .formatted(token, expiresIn, VAULT_RESOURCE))
                    .withTransformers(RESPONSE_TEMPLATE)));
  }

  private static String shortLivedToken(final String tokenPrefix, final long lifetimeSeconds) {
    return tokenPrefix + ".{{now offset='" + lifetimeSeconds + " seconds' format='epoch'}}";
  }

  /** IMDS answers as on a machine without a managed identity. */
  void imdsHasNoIdentity() {
    server.stubFor(
        get(urlPathEqualTo(IMDS_TOKEN_PATH))
            .withHeader(ORIGINAL_HOST, equalTo(RoutedHttpClient.IMDS_HOST))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"error":"invalid_request","error_description":"Identity not found"}
                        """)));
  }

  /**
   * The vault {@code vaultHost} challenges unauthenticated calls for a token of {@code tenantId},
   * accepts {@code token}, holds {@code secretName} with {@code value} and lists no secrets.
   */
  void vaultHolds(
      final String vaultHost,
      final String tenantId,
      final String token,
      final String secretName,
      final String value) {
    vaultChallenges(vaultHost, tenantId);
    server.stubFor(
        get(urlPathMatching("/secrets/" + Pattern.quote(secretName) + "/?"))
            .withHeader(ORIGINAL_HOST, equalTo(vaultHost))
            .withHeader("Authorization", equalTo("Bearer " + token))
            .willReturn(
                okJson(
                    """
                    {"value":"%s","id":"https://%s/secrets/%s/1","attributes":{"enabled":true}}
                    """
                        .formatted(value, vaultHost, secretName))));
    server.stubFor(
        get(urlPathEqualTo("/secrets"))
            .withHeader(ORIGINAL_HOST, equalTo(vaultHost))
            .withHeader("Authorization", equalTo("Bearer " + token))
            .willReturn(
                okJson(
                    """
                {"value":[],"nextLink":null}
                """)));
  }

  /**
   * Like {@link #vaultHolds}, but accepts only unexpired tokens issued by {@link
   * #entraIssuesShortLivedTokens} or {@link #imdsIssuesShortLivedTokens}. An expired token gets 401
   * with a new challenge, as from Key Vault.
   */
  void vaultHoldsForUnexpiredTokens(
      final String vaultHost,
      final String tenantId,
      final String tokenPrefix,
      final String secretName,
      final String value) {
    vaultChallenges(vaultHost, tenantId);
    final var bearerPrefix = "Bearer " + tokenPrefix + ".";
    final ValueMatcher<Request> unexpired =
        request -> {
          final var authorization = request.getHeader("Authorization");
          final var expiresAt = Long.parseLong(authorization.substring(bearerPrefix.length()));
          return MatchResult.of(System.currentTimeMillis() < expiresAt);
        };
    server.stubFor(
        get(urlPathMatching("/secrets/" + Pattern.quote(secretName) + "/?"))
            .atPriority(1)
            .withHeader(ORIGINAL_HOST, equalTo(vaultHost))
            .withHeader("Authorization", matching(Pattern.quote(bearerPrefix) + "\\d+"))
            .andMatching(unexpired)
            .willReturn(
                okJson(
                    """
                    {"value":"%s","id":"https://%s/secrets/%s/1","attributes":{"enabled":true}}
                    """
                        .formatted(value, vaultHost, secretName))));
    server.stubFor(
        get(urlPathEqualTo("/secrets"))
            .atPriority(1)
            .withHeader(ORIGINAL_HOST, equalTo(vaultHost))
            .withHeader("Authorization", matching(Pattern.quote(bearerPrefix) + "\\d+"))
            .andMatching(unexpired)
            .willReturn(
                okJson(
                    """
                {"value":[],"nextLink":null}
                """)));
    server.stubFor(
        get(anyUrl())
            .atPriority(5)
            .withHeader(ORIGINAL_HOST, equalTo(vaultHost))
            .withHeader("Authorization", containing(bearerPrefix))
            .willReturn(
                challenge(tenantId)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"error":{"code":"Unauthorized","message":"AKV10022: Invalid audience or expired token."}}
                        """)));
  }

  /** The bearer tokens the vault {@code vaultHost} answered with 200, in order. */
  List<String> acceptedTokens(final String vaultHost) {
    return server.getAllServeEvents().reversed().stream()
        .filter(event -> vaultHost.equals(event.getRequest().getHeader(ORIGINAL_HOST)))
        .filter(event -> event.getResponse().getStatus() == 200)
        .map(event -> event.getRequest().getHeader("Authorization").substring("Bearer ".length()))
        .toList();
  }

  /** The bearer tokens the vault {@code vaultHost} answered with 401, in order. */
  List<String> rejectedTokens(final String vaultHost) {
    return server.getAllServeEvents().reversed().stream()
        .filter(event -> vaultHost.equals(event.getRequest().getHeader(ORIGINAL_HOST)))
        .filter(event -> event.getResponse().getStatus() == 401)
        .filter(event -> event.getRequest().containsHeader("Authorization"))
        .map(event -> event.getRequest().getHeader("Authorization").substring("Bearer ".length()))
        .toList();
  }

  /**
   * The vault {@code vaultHost} challenges unauthenticated calls for a token of {@code tenantId}
   * and refuses {@code token} with 403, as for an identity without a role on the vault.
   */
  void vaultDenies(final String vaultHost, final String tenantId, final String token) {
    vaultChallenges(vaultHost, tenantId);
    server.stubFor(
        get(anyUrl())
            .withHeader(ORIGINAL_HOST, equalTo(vaultHost))
            .withHeader("Authorization", equalTo("Bearer " + token))
            .willReturn(
                aResponse()
                    .withStatus(403)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"error":{"code":"Forbidden","message":"Caller is not authorized to perform action on resource.","innererror":{"code":"ForbiddenByRbac"}}}
                        """)));
  }

  private void vaultChallenges(final String vaultHost, final String tenantId) {
    server.stubFor(
        get(anyUrl())
            .withHeader(ORIGINAL_HOST, equalTo(vaultHost))
            .withHeader("Authorization", absent())
            .willReturn(challenge(tenantId)));
  }

  private static ResponseDefinitionBuilder challenge(final String tenantId) {
    return aResponse()
        .withStatus(401)
        .withHeader(
            "WWW-Authenticate",
            "Bearer authorization=\"https://%s/%s\", resource=\"%s\""
                .formatted(RoutedHttpClient.AUTHORITY_HOST, tenantId, VAULT_RESOURCE));
  }

  /** Every request that reached the mock for {@code host}, in order. */
  List<LoggedRequest> requestsTo(final String host) {
    return server.findAll(anyRequestedFor(anyUrl()).withHeader(ORIGINAL_HOST, equalTo(host)));
  }

  /** Every token request sent to Entra ID or IMDS, in order. */
  List<LoggedRequest> tokenRequests() {
    return server.getAllServeEvents().reversed().stream()
        .map(event -> event.getRequest())
        .filter(
            request ->
                request.getUrl().contains("/oauth2/v2.0/token")
                    || request.getUrl().startsWith(IMDS_TOKEN_PATH))
        .toList();
  }

  /** Forgets every stub and recorded request, for the next test. */
  void reset() {
    server.resetAll();
  }

  @Override
  public void close() {
    server.stop();
  }
}

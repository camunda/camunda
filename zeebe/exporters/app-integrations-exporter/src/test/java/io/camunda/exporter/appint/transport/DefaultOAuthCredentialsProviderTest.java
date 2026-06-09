/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.transport;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.exporter.appint.metrics.AppIntegrationsExporterMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DefaultOAuthCredentialsProviderTest {

  @RegisterExtension
  public static WireMockExtension wireMock =
      WireMockExtension.extensionOptions().options(wireMockConfig().dynamicPort()).build();

  private static final String TOKEN_PATH = "/oauth/token";

  private DefaultOAuthCredentialsProvider provider;
  private SimpleMeterRegistry registry;

  @AfterEach
  void tearDown() {
    if (provider != null) {
      provider.close();
      provider = null;
    }
  }

  @Test
  void shouldFetchTokenInBackgroundOnStart() throws IOException {
    // given
    wireMock.stubFor(post(TOKEN_PATH).willReturn(tokenResponse("tok-A", 3600)));

    // when
    provider = newProvider();

    // then — the background thread fetches the token without any applyCredentials call
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(tokenRequestCount()).isGreaterThanOrEqualTo(1));

    // and a subsequent applyCredentials attaches the warm token without another fetch
    final int fetchesBefore = tokenRequestCount();
    assertThat(applyAndCaptureAuthorization()).isEqualTo("Bearer tok-A");
    assertThat(tokenRequestCount()).isEqualTo(fetchesBefore);
  }

  @Test
  void shouldProactivelyRotateBeforeTheSixtySecondMargin() throws IOException {
    // given — first token expires just past the 60s refresh margin so rotation happens quickly
    wireMock.stubFor(
        post(TOKEN_PATH)
            .inScenario("rotation")
            .whenScenarioStateIs(STARTED)
            .willReturn(tokenResponse("tok-1", 61))
            .willSetStateTo("rotated"));
    wireMock.stubFor(
        post(TOKEN_PATH)
            .inScenario("rotation")
            .whenScenarioStateIs("rotated")
            .willReturn(tokenResponse("tok-2", 3600)));

    // when
    provider = newProvider();

    // then — a second token request happens before expiry and the cached bearer changes
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(tokenRequestCount()).isGreaterThanOrEqualTo(2));
    assertThat(applyAndCaptureAuthorization()).isEqualTo("Bearer tok-2");
  }

  @Test
  void shouldRefetchAfterInvalidate() throws IOException {
    // given
    wireMock.stubFor(
        post(TOKEN_PATH)
            .inScenario("invalidate")
            .whenScenarioStateIs(STARTED)
            .willReturn(tokenResponse("tok-1", 3600))
            .willSetStateTo("second"));
    wireMock.stubFor(
        post(TOKEN_PATH)
            .inScenario("invalidate")
            .whenScenarioStateIs("second")
            .willReturn(tokenResponse("tok-2", 3600)));
    provider = newProvider();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(tokenRequestCount()).isGreaterThanOrEqualTo(1));

    // when
    provider.invalidate();

    // then — a fresh token is fetched and applied
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(tokenRequestCount()).isGreaterThanOrEqualTo(2));
    assertThat(applyAndCaptureAuthorization()).isEqualTo("Bearer tok-2");
  }

  @Test
  void shouldStopRefresherThreadOnClose() {
    // given — a short-lived token that would keep rotating roughly every second
    wireMock.stubFor(post(TOKEN_PATH).willReturn(tokenResponse("tok-A", 61)));
    provider = newProvider();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(tokenRequestCount()).isGreaterThanOrEqualTo(1));

    // when
    provider.close();
    provider = null; // already closed
    final int countAtClose = tokenRequestCount();

    // then — no further token requests arrive after close
    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(4))
        .untilAsserted(() -> assertThat(tokenRequestCount()).isEqualTo(countAtClose));
  }

  @Test
  void shouldFallBackToSynchronousFetchBeforeBackgroundWarmup() throws IOException {
    // given
    wireMock.stubFor(post(TOKEN_PATH).willReturn(tokenResponse("tok-A", 3600)));
    provider = newProvider();

    // when — the very first applyCredentials may run before the background fetch completes
    final String authorization = applyAndCaptureAuthorization();

    // then — it still yields a valid bearer header via the synchronous fallback
    assertThat(authorization).isEqualTo("Bearer tok-A");
  }

  @Test
  void shouldKeepRetryingBackgroundRefreshWhenTokenEndpointFails() {
    // given — the token endpoint fails once, then succeeds
    wireMock.stubFor(
        post(TOKEN_PATH)
            .inScenario("retry")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("recovered"));
    wireMock.stubFor(
        post(TOKEN_PATH)
            .inScenario("retry")
            .whenScenarioStateIs("recovered")
            .willReturn(tokenResponse("tok-A", 3600)));

    // when — construction must not throw despite the initial failure
    provider = newProvider();

    // then — the provider eventually serves a valid token
    await()
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> assertThat(applyAndCaptureAuthorization()).isEqualTo("Bearer tok-A"));

    // and the initial failure was recorded
    assertThat(registry.get("zeebe.app.integrations.exporter.token.fetch.failed").counter().count())
        .isGreaterThanOrEqualTo(1.0);
  }

  @Test
  void shouldReuseHttpClientAcrossTokenFetches() throws IOException {
    // given
    wireMock.stubFor(post(TOKEN_PATH).willReturn(tokenResponse("tok-A", 3600)));
    provider = newProvider();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(tokenRequestCount()).isGreaterThanOrEqualTo(1));

    // when — drive several fetches through the single shared client
    for (int i = 0; i < 3; i++) {
      provider.invalidate();
      final int expected = i + 2;
      await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> assertThat(tokenRequestCount()).isGreaterThanOrEqualTo(expected));
    }

    // then — the provider keeps serving a valid token without per-call client teardown
    assertThat(applyAndCaptureAuthorization()).isEqualTo("Bearer tok-A");
  }

  private DefaultOAuthCredentialsProvider newProvider() {
    registry = new SimpleMeterRegistry();
    return new DefaultOAuthCredentialsProvider(
        wireMock.baseUrl() + TOKEN_PATH,
        "client-id",
        "client-secret",
        null,
        null,
        null,
        Duration.ofSeconds(5),
        Duration.ofSeconds(5),
        new AppIntegrationsExporterMetrics(registry));
  }

  private String applyAndCaptureAuthorization() throws IOException {
    final Map<String, String> headers = new HashMap<>();
    provider.applyCredentials(headers::put);
    return headers.get(DefaultOAuthCredentialsProvider.AUTHORIZATION_HEADER);
  }

  private int tokenRequestCount() {
    return wireMock.findAll(postRequestedFor(urlEqualTo(TOKEN_PATH))).size();
  }

  private static ResponseDefinitionBuilder tokenResponse(
      final String accessToken, final long expiresInSeconds) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(
            "{\"access_token\":\""
                + accessToken
                + "\",\"token_type\":\"Bearer\",\"expires_in\":"
                + expiresInSeconds
                + "}");
  }
}

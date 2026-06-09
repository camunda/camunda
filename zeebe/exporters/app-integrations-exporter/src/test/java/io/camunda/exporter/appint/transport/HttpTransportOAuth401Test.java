/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.transport;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.exporter.appint.metrics.AppIntegrationsExporterMetrics;
import io.camunda.exporter.appint.subscription.SubscriptionFactory;
import io.camunda.exporter.appint.transport.Authentication.ApiKey;
import io.camunda.exporter.appint.transport.Authentication.OAuth;
import io.camunda.exporter.appint.transport.Authentication.OAuthCredentialsProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HttpTransportOAuth401Test {

  @RegisterExtension
  public static WireMockExtension wireMock =
      WireMockExtension.extensionOptions().options(wireMockConfig().dynamicPort()).build();

  @Test
  void shouldInvalidateAndRetryOnUnauthorized() {
    // given — the backend rejects the stale token with 401, then accepts the fresh one
    final String url = "http://localhost:" + wireMock.getPort();
    wireMock.stubFor(
        post("/")
            .inScenario("unauthorized")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(401))
            .willSetStateTo("authorized"));
    wireMock.stubFor(
        post("/").inScenario("unauthorized").whenScenarioStateIs("authorized").willReturn(ok()));

    final RotatingProvider provider = new RotatingProvider();
    final var httpConfig = new HttpTransportConfig(url, new OAuth(provider), 2, 50, 500);
    final var registry = new SimpleMeterRegistry();
    final var transport =
        new HttpTransportImpl(
            SubscriptionFactory.createJsonMapper(),
            httpConfig,
            new AppIntegrationsExporterMetrics(registry));

    // when
    transport.send(new ArrayList<>());

    // then — the token was invalidated once and the retry carried the fresh token
    assertThat(provider.invalidations.get()).isEqualTo(1);
    assertThat(
            registry.get("zeebe.app.integrations.exporter.export.unauthorized").counter().count())
        .isEqualTo(1.0);
    wireMock.verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/")).withHeader("Authorization", equalTo("Bearer stale")));
    wireMock.verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/")).withHeader("Authorization", equalTo("Bearer fresh")));
  }

  @Test
  void shouldNotRetryNonOauth401() {
    // given — a 401 with non-OAuth auth must remain a non-retried client error
    final String url = "http://localhost:" + wireMock.getPort();
    wireMock.stubFor(post("/").willReturn(aResponse().withStatus(401)));
    final var httpConfig = new HttpTransportConfig(url, new ApiKey("test-key"), 2, 50, 500);
    final var transport =
        new HttpTransportImpl(
            SubscriptionFactory.createJsonMapper(),
            httpConfig,
            AppIntegrationsExporterMetrics.disabled());

    // when / then
    assertThatCode(() -> transport.send(new ArrayList<>()))
        .isInstanceOf(TransportClientException.class);
    wireMock.verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/")).withHeader(ApiKey.HEADER_NAME, equalTo("test-key")));
  }

  /**
   * Returns {@code Bearer stale} until {@link #invalidate()} is called, then {@code Bearer fresh}.
   */
  private static final class RotatingProvider implements OAuthCredentialsProvider {
    private final AtomicInteger invalidations = new AtomicInteger();
    private volatile boolean invalidated;

    @Override
    public void applyCredentials(final BiConsumer<String, String> headerConsumer) {
      headerConsumer.accept("Authorization", invalidated ? "Bearer fresh" : "Bearer stale");
    }

    @Override
    public void invalidate() {
      invalidations.incrementAndGet();
      invalidated = true;
    }
  }
}

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
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.exporter.appint.metrics.AppIntegrationsExporterMetrics;
import io.camunda.exporter.appint.subscription.SubscriptionFactory;
import io.camunda.exporter.appint.transport.Authentication.ApiKey;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class HttpTransportTest {

  @RegisterExtension
  public static WireMockExtension wireMock =
      WireMockExtension.extensionOptions().options(wireMockConfig().dynamicPort()).build();

  private String url;
  private Transport transport;
  private SimpleMeterRegistry registry;

  @BeforeEach
  public void beforeAll() {
    url = "http://localhost:" + wireMock.getPort();
    final var httpConfig = new HttpTransportConfig(url, new ApiKey("test-key"), 2, 50, 5000);
    final var jsonMapper = SubscriptionFactory.createJsonMapper();
    registry = new SimpleMeterRegistry();
    transport =
        new HttpTransportImpl(jsonMapper, httpConfig, new AppIntegrationsExporterMetrics(registry));
  }

  @Test
  public void testForStatusCode2XX() {
    wireMock.stubFor(post("/").willReturn(ok()));

    transport.send(new ArrayList<>());

    wireMock.verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/")).withHeader(ApiKey.HEADER_NAME, equalTo("test-key")));
    assertThat(counter("zeebe.app.integrations.exporter.export.failed")).isZero();
  }

  @Test
  public void testRetryForStatusCode3XX() {
    wireMock.stubFor(
        post("/").willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(302)));

    Assertions.assertThatCode(() -> transport.send(new ArrayList<>()))
        .isInstanceOf(TransportException.class);

    wireMock.verify(
        exactly(3),
        postRequestedFor(urlEqualTo("/")).withHeader(ApiKey.HEADER_NAME, equalTo("test-key")));
  }

  @Test
  public void testNoRetryForStatusCode4XX() {
    wireMock.stubFor(
        post("/").willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(404)));

    Assertions.assertThatCode(() -> transport.send(new ArrayList<>()))
        .isInstanceOf(TransportClientException.class);

    wireMock.verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/")).withHeader(ApiKey.HEADER_NAME, equalTo("test-key")));
  }

  @Test
  public void testRetryForStatusCode5XX() {
    wireMock.stubFor(
        post("/").willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    Assertions.assertThatCode(() -> transport.send(new ArrayList<>()))
        .isInstanceOf(TransportException.class);

    wireMock.verify(
        exactly(3),
        postRequestedFor(urlEqualTo("/")).withHeader(ApiKey.HEADER_NAME, equalTo("test-key")));
  }

  @Test
  public void shouldRecordExportFailedWhenSendFails() {
    // given
    wireMock.stubFor(
        post("/").willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    // when
    Assertions.assertThatCode(() -> transport.send(new ArrayList<>()))
        .isInstanceOf(TransportException.class);

    // then
    assertThat(counter("zeebe.app.integrations.exporter.export.failed")).isEqualTo(1.0);
  }

  @Test
  public void shouldRecordExportTimeoutWhenRequestTimesOut() {
    // given
    final var httpConfig = new HttpTransportConfig(url, new ApiKey("test-key"), 2, 50, 500);
    final var jsonMapper = SubscriptionFactory.createJsonMapper();
    transport =
        new HttpTransportImpl(jsonMapper, httpConfig, new AppIntegrationsExporterMetrics(registry));
    wireMock.stubFor(post("/").willReturn(aResponse().withFixedDelay(2000).withStatus(200)));

    // when
    Assertions.assertThatCode(() -> transport.send(new ArrayList<>()))
        .isInstanceOf(Throwable.class);

    // then
    assertThat(timeoutCounter("export")).isEqualTo(1.0);
  }

  private double counter(final String name) {
    return registry.get(name).counter().count();
  }

  private double timeoutCounter(final String phase) {
    return registry
        .get("zeebe.app.integrations.exporter.timeout")
        .tag("phase", phase)
        .counter()
        .count();
  }
}

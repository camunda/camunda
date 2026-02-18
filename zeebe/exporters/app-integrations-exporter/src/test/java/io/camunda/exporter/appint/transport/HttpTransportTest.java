/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.transport;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.exporter.appint.subscription.SubscriptionFactory;
import io.camunda.exporter.appint.transport.Authentication.ApiKey;
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

  @BeforeEach
  public void beforeAll() {
    url = "http://localhost:" + wireMock.getPort();
    final var httpConfig = new HttpTransportConfig(url, new ApiKey("test-key"), 2, 50, 500);
    final var jsonMapper = SubscriptionFactory.createJsonMapper();
    transport = new HttpTransportImpl(jsonMapper, httpConfig);
  }

  @Test
  public void testSuccessfulTransport() {
    wireMock.stubFor(post("/").willReturn(ok()));

    transport.send(new ArrayList<>());

    wireMock.verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/")).withHeader(ApiKey.HEADER_NAME, equalTo("test-key")));
  }

  @Test
  public void testRetryForStatusCode500() {
    wireMock.stubFor(
        post("/").willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

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
}

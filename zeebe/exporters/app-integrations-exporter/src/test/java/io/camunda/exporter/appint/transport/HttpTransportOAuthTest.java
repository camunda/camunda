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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.exporter.appint.subscription.SubscriptionFactory;
import io.camunda.exporter.appint.transport.Authentication.OAuth;
import io.camunda.exporter.appint.transport.Authentication.OAuthCredentialsProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HttpTransportOAuthTest {

  @RegisterExtension
  public static WireMockExtension wireMock =
      WireMockExtension.extensionOptions().options(wireMockConfig().dynamicPort()).build();

  @Test
  void shouldSetAuthorizationHeaderFromOAuthProvider() {
    // given
    final String url = "http://localhost:" + wireMock.getPort();
    wireMock.stubFor(post("/").willReturn(ok()));
    final OAuthCredentialsProvider provider =
        headerConsumer -> headerConsumer.accept("Authorization", "Bearer test-token");
    final var httpConfig = new HttpTransportConfig(url, new OAuth(provider), 2, 50, 500);
    final var transport = new HttpTransportImpl(SubscriptionFactory.createJsonMapper(), httpConfig);

    // when
    transport.send(new ArrayList<>());

    // then
    wireMock.verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/"))
            .withHeader("Authorization", equalTo("Bearer test-token")));
  }

  @Test
  void shouldRetryWhenOAuthProviderThrows() {
    // given
    final String url = "http://localhost:" + wireMock.getPort();
    wireMock.stubFor(post("/").willReturn(ok()));
    final AtomicInteger calls = new AtomicInteger();
    final OAuthCredentialsProvider flakyProvider =
        headerConsumer -> {
          if (calls.getAndIncrement() == 0) {
            throw new IOException("transient");
          }
          headerConsumer.accept("Authorization", "Bearer late-token");
        };
    final var httpConfig = new HttpTransportConfig(url, new OAuth(flakyProvider), 2, 50, 500);
    final var transport = new HttpTransportImpl(SubscriptionFactory.createJsonMapper(), httpConfig);

    // when
    transport.send(new ArrayList<>());

    // then
    Assertions.assertThat(calls.get()).isGreaterThanOrEqualTo(2);
    wireMock.verify(
        exactly(1),
        postRequestedFor(urlEqualTo("/"))
            .withHeader("Authorization", equalTo("Bearer late-token")));
  }
}

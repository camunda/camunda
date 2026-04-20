/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.oidc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OidcUserInfoClientTest {

  @RegisterExtension
  static WireMockExtension idp =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private final OidcUserInfoClient client =
      new OidcUserInfoClient(HttpClient.newHttpClient(), Duration.ofSeconds(2));

  @Test
  void returnsClaimsFromSuccessfulResponse() {
    idp.stubFor(
        get("/userinfo")
            .withHeader("Authorization", equalTo("Bearer token-abc"))
            .willReturn(okJson("{\"sub\":\"alice\",\"groups\":[\"engineering\",\"ops\"]}")));

    final var claims = client.fetch(URI.create(idp.baseUrl() + "/userinfo"), "token-abc");

    assertThat(claims).containsEntry("sub", "alice");
    assertThat(claims).containsEntry("groups", List.of("engineering", "ops"));
  }

  @Test
  void throwsOnNon2xx() {
    idp.stubFor(get("/userinfo").willReturn(aResponse().withStatus(401)));

    assertThatThrownBy(() -> client.fetch(URI.create(idp.baseUrl() + "/userinfo"), "bad-token"))
        .isInstanceOf(OidcUserInfoException.class)
        .hasMessageContaining("401");
  }

  @Test
  void throwsOnTimeout() {
    idp.stubFor(get("/userinfo").willReturn(aResponse().withFixedDelay(3_000).withBody("{}")));

    assertThatThrownBy(() -> client.fetch(URI.create(idp.baseUrl() + "/userinfo"), "token-abc"))
        .isInstanceOf(OidcUserInfoException.class);
  }
}

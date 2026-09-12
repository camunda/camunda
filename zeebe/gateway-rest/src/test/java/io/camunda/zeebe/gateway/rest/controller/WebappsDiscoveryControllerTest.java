/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.config.WebappsDiscoveryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;

@WebMvcTest(WebappsDiscoveryController.class)
class WebappsDiscoveryControllerTest extends RestControllerTest {

  static final String WEBAPPS_DISCOVERY_URL = "/.well-known/camunda/webapps";

  @Test
  void shouldAnnounceWebappsAsRelativePathsByDefault() {
    // given no explicit camunda.webapps.*.url configuration — relative URLs resolve against the
    // origin the client used for the API call, correct behind proxies by construction

    // when / then
    webClient
        .get()
        .uri(WEBAPPS_DISCOVERY_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("{\"operateUrl\":\"/operate\",\"tasklistUrl\":\"/tasklist\"}");
  }

  @Test
  void shouldAnnounceExplicitlyConfiguredUrls() {
    // given
    final var properties = new WebappsDiscoveryProperties();
    properties.getOperate().setUrl("https://operate.example.com");
    properties.getTasklist().setUrl("https://tasklist.example.com/ui");

    // when
    final var response = invokeController(properties);

    // then
    assertThat(response.operateUrl()).isEqualTo("https://operate.example.com");
    assertThat(response.tasklistUrl()).isEqualTo("https://tasklist.example.com/ui");
  }

  @Test
  void shouldNotAnnounceWebappWhenDisabled() {
    // given
    final var properties = new WebappsDiscoveryProperties();
    properties.getOperate().setEnabled(false);

    // when
    final var response = invokeController(properties);

    // then
    assertThat(response.operateUrl()).isNull();
    assertThat(response.tasklistUrl()).isEqualTo("/tasklist");
  }

  @Test
  void shouldNotAnnounceWebappWhenUiDisabled() {
    // given — the API stays available, but there is no UI to link to
    final var properties = new WebappsDiscoveryProperties();
    properties.getTasklist().setUiEnabled(false);

    // when
    final var response = invokeController(properties);

    // then
    assertThat(response.operateUrl()).isEqualTo("/operate");
    assertThat(response.tasklistUrl()).isNull();
  }

  @Test
  void shouldNotAnnounceWebappWhenLegacyWebappEnabledIsFalse() {
    // given — disabled via the legacy kill-switch, which still gates the actual webapp
    final var properties = new WebappsDiscoveryProperties();
    final var environment =
        new MockEnvironment().withProperty("camunda.operate.webappEnabled", "false");

    // when
    final var response = invokeController(properties, environment);

    // then
    assertThat(response.operateUrl()).isNull();
    assertThat(response.tasklistUrl()).isEqualTo("/tasklist");
  }

  @Test
  void shouldAnnounceExplicitUrlEvenWhenWebappIsDisabled() {
    // given — the webapp is not served by this app, but lives at a known location
    final var properties = new WebappsDiscoveryProperties();
    properties.getOperate().setEnabled(false);
    properties.getOperate().setUrl("https://operate.example.com");

    // when
    final var response = invokeController(properties);

    // then
    assertThat(response.operateUrl()).isEqualTo("https://operate.example.com");
  }

  private static WebappsDiscoveryResponse invokeController(
      final WebappsDiscoveryProperties properties) {
    return invokeController(properties, new MockEnvironment());
  }

  private static WebappsDiscoveryResponse invokeController(
      final WebappsDiscoveryProperties properties, final MockEnvironment environment) {
    return new WebappsDiscoveryController(properties, environment).getWebapps();
  }
}

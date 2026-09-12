/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;

import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.config.WebappsDiscoveryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@WebMvcTest(WebappsDiscoveryController.class)
class WebappsDiscoveryControllerTest extends RestControllerTest {

  static final String WEBAPPS_DISCOVERY_URL = "/.well-known/camunda/webapps";

  @Test
  void shouldAnnounceWebappsOnApiOriginByDefault() {
    // given no explicit camunda.webapps.*.url configuration

    // when / then
    webClient
        .get()
        .uri(WEBAPPS_DISCOVERY_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.operateUrl")
        .value(startsWith("http"))
        .jsonPath("$.operateUrl")
        .value(endsWith("/operate"))
        .jsonPath("$.tasklistUrl")
        .value(startsWith("http"))
        .jsonPath("$.tasklistUrl")
        .value(endsWith("/tasklist"));
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
  void shouldDeriveUrlFromRequestOriginWhenNoUrlConfigured() {
    // given
    final var properties = new WebappsDiscoveryProperties();

    // when
    final var response = invokeController(properties);

    // then
    assertThat(response.operateUrl()).isEqualTo("https://cluster.example.com/operate");
    assertThat(response.tasklistUrl()).isEqualTo("https://cluster.example.com/tasklist");
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
    assertThat(response.tasklistUrl()).isEqualTo("https://cluster.example.com/tasklist");
  }

  @Test
  void shouldNotAnnounceWebappWhenUiDisabled() {
    // given — the API stays available, but there is no UI to link to
    final var properties = new WebappsDiscoveryProperties();
    properties.getTasklist().setUiEnabled(false);

    // when
    final var response = invokeController(properties);

    // then
    assertThat(response.operateUrl()).isEqualTo("https://cluster.example.com/operate");
    assertThat(response.tasklistUrl()).isNull();
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
    final var request = new MockHttpServletRequest("GET", WEBAPPS_DISCOVERY_URL);
    request.setScheme("https");
    request.setServerName("cluster.example.com");
    request.setServerPort(443);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      return new WebappsDiscoveryController(properties).getWebapps();
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ClusterApiPortFilterTest {

  private static final int MAIN_PORT = 8080;
  private static final int CLUSTER_PORT = 9700;

  private final ClusterApiPortFilter filter = new ClusterApiPortFilter(CLUSTER_PORT);

  @ParameterizedTest
  @CsvSource({
    // port, normalized path, expected status (200 = passed on to the chain)
    "9700, /cluster/v2/topology, 200",
    "9700, /cluster/v2, 200",
    "9700, /cluster/v2/status, 200",
    "9700, /v2/topology, 404",
    "9700, /physical-tenants/default/v2/topology, 404",
    "9700, /cluster/v20/topology, 404",
    "9700, /operate, 404",
    "8080, /cluster/v2/topology, 404",
    "8080, /cluster/v2, 404",
    "8080, /cluster/v2/status, 200",
    "8080, /cluster/v2/status/upgrade, 200",
    "8080, /v2/topology, 200",
    "8080, /cluster/v20/topology, 200",
  })
  void shouldServeEachPathOnItsPort(final int port, final String path, final int expected)
      throws Exception {
    // when
    final var response = filter(request(port, path, path));

    // then
    assertThat(response.getStatus()).isEqualTo(expected);
  }

  @Test
  void shouldDecideOnContainerNormalizedPath() throws Exception {
    // given — path parameters and an encoded slash, which Tomcat strips/decodes into the servlet
    // path before routing
    final var request = request(MAIN_PORT, "/cluster;x=y/v2%2Ftopology", "/cluster/v2/topology");

    // when
    final var response = filter(request);

    // then
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  void shouldIgnoreHostHeaderPort() throws Exception {
    // given
    final var request = request(MAIN_PORT, "/cluster/v2/topology", "/cluster/v2/topology");
    request.setServerPort(CLUSTER_PORT);

    // when
    final var response = filter(request);

    // then
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/cluster/v2/../../v2/authentication/me",
        "/v2/../cluster/v2/topology",
        "/cluster/v2/./topology",
        "/v2/%2e%2e/cluster/v2/topology",
        "/v2/..;x=y/cluster/v2/topology",
        "/v2/..%2Fcluster/v2/topology",
        "/v2/%zz",
      })
  void shouldRejectDotSegmentsOnEitherPort(final String rawPath) throws Exception {
    // when
    final var onMainPort = filter(request(MAIN_PORT, rawPath, "/irrelevant"));
    final var onClusterPort = filter(request(CLUSTER_PORT, rawPath, "/cluster/v2/irrelevant"));

    // then
    assertThat(onMainPort.getStatus()).isEqualTo(400);
    assertThat(onClusterPort.getStatus()).isEqualTo(400);
  }

  private MockHttpServletResponse filter(final MockHttpServletRequest request) throws Exception {
    final var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  private static MockHttpServletRequest request(
      final int localPort, final String rawUri, final String servletPath) {
    final var request = new MockHttpServletRequest("GET", rawUri);
    request.setServletPath(servletPath);
    request.setLocalPort(localPort);
    return request;
  }
}

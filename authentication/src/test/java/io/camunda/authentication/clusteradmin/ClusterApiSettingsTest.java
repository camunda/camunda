/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.authentication.clusteradmin.ClusterApiSettings.ClusterAdminAuthenticationMethod;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class ClusterApiSettingsTest {

  @Test
  void shouldShareMainPortByDefault() {
    // given
    final var environment = new MockEnvironment();

    // when
    final var matcher = ClusterApiSettings.clusterApiMatcher(environment);

    // then — any port matches, only the path matters
    assertThat(ClusterApiSettings.dedicatedPort(environment)).isEmpty();
    assertThat(matcher.matches(request(8080, "/cluster/v2/topology"))).isTrue();
    assertThat(matcher.matches(request(8080, "/v2/topology"))).isFalse();
  }

  @Test
  void shouldMatchOnlyDedicatedLocalPort() {
    // given
    final var environment = new MockEnvironment().withProperty("camunda.api.cluster.port", "9700");

    // when
    final var matcher = ClusterApiSettings.clusterApiMatcher(environment);

    // then
    assertThat(matcher.matches(request(9700, "/cluster/v2/topology"))).isTrue();
    assertThat(matcher.matches(request(8080, "/cluster/v2/topology"))).isFalse();
  }

  @Test
  void shouldIgnoreHostHeaderPort() {
    // given
    final var environment = new MockEnvironment().withProperty("camunda.api.cluster.port", "9700");
    final var request = request(8080, "/cluster/v2/topology");
    request.setServerPort(9700);

    // when
    final boolean matches = ClusterApiSettings.clusterApiMatcher(environment).matches(request);

    // then
    assertThat(matches).isFalse();
  }

  @Test
  void shouldRejectPortCollidingWithMainPort() {
    // given
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.api.cluster.port", "8080")
            .withProperty("server.port", "8080");

    // when / then
    assertThatThrownBy(() -> ClusterApiSettings.dedicatedPort(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("server.port");
  }

  @Test
  void shouldFollowGlobalAuthenticationMethodByDefault() {
    // given
    final var environment =
        new MockEnvironment().withProperty("camunda.security.authentication.method", "oidc");

    // when / then
    assertThat(ClusterApiSettings.authenticationMethod(environment))
        .isEqualTo(ClusterAdminAuthenticationMethod.OIDC);
    assertThat(ClusterApiSettings.authenticationMethod(new MockEnvironment()))
        .isEqualTo(ClusterAdminAuthenticationMethod.BASIC);
  }

  @Test
  void shouldPreferExplicitClusterAdminAuthenticationMethod() {
    // given
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.security.authentication.method", "oidc")
            .withProperty("camunda.security.cluster-admin.authentication.method", "none");

    // when / then
    assertThat(ClusterApiSettings.authenticationMethod(environment))
        .isEqualTo(ClusterAdminAuthenticationMethod.NONE);
  }

  private static MockHttpServletRequest request(final int localPort, final String path) {
    final var request = new MockHttpServletRequest("GET", path);
    request.setLocalPort(localPort);
    return request;
  }
}

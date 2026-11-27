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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class ClusterApiSettingsTest {

  @Test
  void shouldShareMainPortByDefault() {
    // when
    final var settings = ClusterApiSettings.load(new MockEnvironment());

    // then
    assertThat(settings.hasDedicatedPort()).isFalse();
    assertThat(settings.authenticationMethod()).isEqualTo(ClusterAdminAuthenticationMethod.BASIC);
  }

  @Test
  void shouldTreatBlankPortAsNotConfigured() {
    // given — e.g. an empty environment variable rendered by a Helm template
    final var environment = new MockEnvironment().withProperty("camunda.api.cluster.port", " ");

    // when
    final var settings = ClusterApiSettings.load(environment);

    // then
    assertThat(settings.hasDedicatedPort()).isFalse();
  }

  @Test
  void shouldMatchClusterApiOnEveryPortForAuthenticatedChains() {
    // given
    final var settings = load("camunda.api.cluster.port", "9700");

    // when
    final var matcher = settings.clusterApiMatcher();

    // then — credentials are required wherever the path is reachable
    assertThat(matcher.matches(request(9700, "/cluster/v2/topology"))).isTrue();
    assertThat(matcher.matches(request(8080, "/cluster/v2/topology"))).isTrue();
    assertThat(matcher.matches(request(9700, "/v2/topology"))).isFalse();
  }

  @Test
  void shouldSplitDedicatedAndOtherPorts() {
    // given
    final var settings = load("camunda.api.cluster.port", "9700");

    // when / then
    assertThat(settings.dedicatedPortMatcher().matches(request(9700, "/cluster/v2/topology")))
        .isTrue();
    assertThat(settings.dedicatedPortMatcher().matches(request(8080, "/cluster/v2/topology")))
        .isFalse();
    assertThat(settings.otherPortsMatcher().matches(request(8080, "/cluster/v2/topology")))
        .isTrue();
    assertThat(settings.otherPortsMatcher().matches(request(9700, "/cluster/v2/topology")))
        .isFalse();
  }

  @Test
  void shouldIgnoreHostHeaderPort() {
    // given
    final var settings = load("camunda.api.cluster.port", "9700");
    final var request = request(8080, "/cluster/v2/topology");
    request.setServerPort(9700);

    // when
    final boolean matches = settings.dedicatedPortMatcher().matches(request);

    // then
    assertThat(matches).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"server.port", "management.server.port", "camunda.api.grpc.port"})
  void shouldRejectPortCollidingWithAnotherListener(final String otherPortProperty) {
    // given
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.api.cluster.port", "9700")
            .withProperty(otherPortProperty, "9700");

    // when / then
    assertThatThrownBy(() -> ClusterApiSettings.load(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(otherPortProperty);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "65536", "not-a-port"})
  void shouldRejectInvalidPort(final String port) {
    // when / then
    assertThatThrownBy(() -> load("camunda.api.cluster.port", port))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("camunda.api.cluster.port");
  }

  @Test
  void shouldFollowGlobalAuthenticationMethodByDefault() {
    // when
    final var settings = load("camunda.security.authentication.method", "oidc");

    // then
    assertThat(settings.authenticationMethod()).isEqualTo(ClusterAdminAuthenticationMethod.OIDC);
  }

  @Test
  void shouldPreferExplicitClusterAdminAuthenticationMethod() {
    // given
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.security.authentication.method", "oidc")
            .withProperty("camunda.api.cluster.port", "9700")
            .withProperty("camunda.api.cluster.address", "127.0.0.1")
            .withProperty("camunda.security.cluster-admin.authentication.method", "none");

    // when
    final var settings = ClusterApiSettings.load(environment);

    // then
    assertThat(settings.authenticationMethod()).isEqualTo(ClusterAdminAuthenticationMethod.NONE);
  }

  @Test
  void shouldRejectNoAuthenticationOnSharedPort() {
    // when / then
    assertThatThrownBy(() -> load("camunda.security.cluster-admin.authentication.method", "none"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("camunda.api.cluster.port");
  }

  @Test
  void shouldRejectNoAuthenticationWithoutExplicitAddress() {
    // given — without an address the port would bind like server.address, usually all interfaces
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.api.cluster.port", "9700")
            .withProperty("camunda.security.cluster-admin.authentication.method", "none");

    // when / then
    assertThatThrownBy(() -> ClusterApiSettings.load(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("camunda.api.cluster.address");
  }

  @Test
  void shouldRejectUnresolvableAddress() {
    // given — Tomcat would silently keep the wildcard address for a value it cannot resolve
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.api.cluster.port", "9700")
            .withProperty("camunda.api.cluster.address", "no-such-host.invalid");

    // when / then
    assertThatThrownBy(() -> ClusterApiSettings.load(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no-such-host.invalid");
  }

  @Test
  void shouldResolveAddressToLiteral() {
    // given
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.api.cluster.port", "9700")
            .withProperty("camunda.api.cluster.address", " 127.0.0.1 ");

    // when
    final var settings = ClusterApiSettings.load(environment);

    // then
    assertThat(settings.address()).isEqualTo("127.0.0.1");
  }

  @Test
  void shouldRejectUnknownAuthenticationMethod() {
    // when / then
    assertThatThrownBy(
            () -> load("camunda.security.cluster-admin.authentication.method", "kerberos"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("kerberos");
  }

  private static ClusterApiSettings load(final String key, final String value) {
    return ClusterApiSettings.load(new MockEnvironment().withProperty(key, value));
  }

  private static MockHttpServletRequest request(final int localPort, final String path) {
    final var request = new MockHttpServletRequest("GET", path);
    request.setLocalPort(localPort);
    return request;
  }
}

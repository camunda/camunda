/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.appint.DeploymentContext;
import io.camunda.exporter.appint.config.Config;
import io.camunda.exporter.appint.config.OAuthConfig;
import io.camunda.exporter.appint.metrics.AppIntegrationsExporterMetrics;
import io.camunda.exporter.appint.transport.ContextHeaders;
import io.camunda.exporter.appint.transport.HttpTransportImpl;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SubscriptionFactoryContextHeadersTest {

  @Test
  void shouldResolveHeadersIndependentlyOfAuth() throws Exception {
    // given — OAuth auth plus org and cluster id from the deployment context (SaaS environment)
    final Config config =
        new Config()
            .setUrl("http://example.com")
            .setOauth(
                new OAuthConfig()
                    .setClientId("id")
                    .setClientSecret("secret")
                    .setAuthorizationServerUrl("https://auth.example.com/oauth/token"));
    final var deploymentContext = new DeploymentContext("org-123", "cluster-env");

    // when
    final var subscription =
        SubscriptionFactory.createDefault(
            config, deploymentContext, position -> {}, AppIntegrationsExporterMetrics.disabled());

    // then
    assertThat(extractHeaders(subscription))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                ContextHeaders.X_ORG_ID, "org-123",
                ContextHeaders.X_CLUSTER_ID, "cluster-env"));
    subscription.close();
  }

  @Test
  void shouldPreferConfiguredClusterIdOverEnvironment() throws Exception {
    // given — Self-Managed style: operator clusterId overrides the environment-provided one
    final Config config =
        new Config().setUrl("http://example.com").setApiKey("key").setClusterId("sm-cluster");
    final var deploymentContext = new DeploymentContext(null, "cluster-env");

    // when
    final var subscription =
        SubscriptionFactory.createDefault(
            config, deploymentContext, position -> {}, AppIntegrationsExporterMetrics.disabled());

    // then
    assertThat(extractHeaders(subscription))
        .containsExactlyInAnyOrderEntriesOf(Map.of(ContextHeaders.X_CLUSTER_ID, "sm-cluster"));
    subscription.close();
  }

  @Test
  void shouldEmitNoHeadersWhenNothingAvailable() throws Exception {
    // given
    final Config config = new Config().setUrl("http://example.com");

    // when
    final var subscription =
        SubscriptionFactory.createDefault(
            config,
            DeploymentContext.EMPTY,
            position -> {},
            AppIntegrationsExporterMetrics.disabled());

    // then
    assertThat(extractHeaders(subscription)).isEmpty();
    subscription.close();
  }

  private static Map<String, String> extractHeaders(final Subscription<?> subscription)
      throws Exception {
    final Field transportField = Subscription.class.getDeclaredField("transport");
    transportField.setAccessible(true);
    final HttpTransportImpl transport = (HttpTransportImpl) transportField.get(subscription);
    final Field headersField = HttpTransportImpl.class.getDeclaredField("contextHeaders");
    headersField.setAccessible(true);
    final ContextHeaders headers = (ContextHeaders) headersField.get(transport);
    final Map<String, String> collected = new LinkedHashMap<>();
    headers.applyTo(collected::put);
    return collected;
  }
}

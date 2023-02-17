/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.probes.health.HealthZeebeClientProperties.SecurityProperties.OAuthSecurityProperties;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator that sends a request to the gateway to check its responsiveness. The request
 * must yield a response within a given timeout for this health indicator to report {@code
 * Status.UP}
 */
public class ResponsiveHealthIndicator implements HealthIndicator {
  private final GatewayCfg gatewayCfg;

  private final HealthZeebeClientProperties healthZeebeClientProperties;

  private ZeebeClient zeebeClient;

  public ResponsiveHealthIndicator(
      final GatewayCfg gatewayCfg, final HealthZeebeClientProperties healthZeebeClientProperties) {
    this.gatewayCfg = requireNonNull(gatewayCfg);
    this.healthZeebeClientProperties = requireNonNull(healthZeebeClientProperties);
  }

  GatewayCfg getGatewayCfg() {
    return gatewayCfg;
  }

  HealthZeebeClientProperties getHealthZeebeClientProperties() {
    return healthZeebeClientProperties;
  }

  @Override
  public Health health() {
    Builder resultBuilder;

    initZeebeClient();
    if (zeebeClient == null) {
      resultBuilder = Health.unknown();
    } else {
      try {
        zeebeClient.newTopologyRequest().send().get();
        resultBuilder = Health.up();
      } catch (final Throwable t) {
        resultBuilder = Health.down().withException(t);
      }
    }

    return resultBuilder
        .withDetails(
            Map.of(
                "timeOut",
                healthZeebeClientProperties.getRequestTimeout(),
                "healthZeebeClientProperties",
                healthZeebeClientProperties))
        .build();
  }

  void initZeebeClient() {
    if (zeebeClient == null && gatewayCfg.isInitialized()) {
      zeebeClient = createZeebeClient(gatewayCfg, healthZeebeClientProperties);
    }
  }

  public ZeebeClient getZeebeClient() {
    return zeebeClient;
  }

  /**
   * Only for test purposes
   *
   * @param zeebeClient new ZeebeClient that should be set
   */
  void setZeebeClient(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  static ZeebeClient createZeebeClient(
      final GatewayCfg gatewayCfg, final HealthZeebeClientProperties healthZeebeClientProperties) {
    final String gatewayAddress = getContactPoint(gatewayCfg);

    ZeebeClientBuilder clientBuilder =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(gatewayAddress)
            .defaultRequestTimeout(healthZeebeClientProperties.getRequestTimeout());

    if (gatewayCfg.getSecurity().isEnabled()) {
      clientBuilder =
          clientBuilder.caCertificatePath(
              gatewayCfg.getSecurity().getCertificateChainPath().getAbsolutePath());
    } else {
      clientBuilder = clientBuilder.usePlaintext();
    }
    final OAuthSecurityProperties oauthSecurityProperties =
        healthZeebeClientProperties.getSecurityProperties().getOauthSecurityProperties();
    if (oauthSecurityProperties != null) {
      clientBuilder =
          clientBuilder.credentialsProvider(
              new OAuthCredentialsProviderBuilder()
                  .clientId(oauthSecurityProperties.getClientId())
                  .clientSecret(oauthSecurityProperties.getClientSecret())
                  .credentialsCachePath(oauthSecurityProperties.getCredentialsCachePath())
                  .connectTimeout(oauthSecurityProperties.getConnectTimeout())
                  .authorizationServerUrl(
                      oauthSecurityProperties.getAuthorizationServer().toString())
                  .audience(oauthSecurityProperties.getAudience())
                  .readTimeout(oauthSecurityProperties.getReadTimeout())
                  .build());
    }

    return clientBuilder.build();
  }

  static String getContactPoint(final GatewayCfg gatewayCfg) {
    final String host = gatewayCfg.getNetwork().getHost();
    final int port = gatewayCfg.getNetwork().getPort();

    return host + ":" + port;
  }
}

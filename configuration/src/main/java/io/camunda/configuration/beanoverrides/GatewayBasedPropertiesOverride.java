/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.Grpc;
import io.camunda.configuration.Interceptor;
import io.camunda.configuration.Ssl;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.GatewayBasedProperties;
import io.camunda.configuration.beans.LegacyGatewayBasedProperties;
import io.camunda.zeebe.gateway.impl.configuration.InterceptorCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import io.camunda.zeebe.gateway.impl.configuration.ThreadsCfg;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableConfigurationProperties(LegacyGatewayBasedProperties.class)
@Profile("!broker")
@DependsOn("unifiedConfigurationHelper")
public class GatewayBasedPropertiesOverride {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(GatewayBasedPropertiesOverride.class);

  private final UnifiedConfiguration unifiedConfiguration;
  private final LegacyGatewayBasedProperties legacyGatewayBasedProperties;

  public GatewayBasedPropertiesOverride(
      final UnifiedConfiguration unifiedConfiguration,
      final LegacyGatewayBasedProperties legacyGatewayBasedProperties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacyGatewayBasedProperties = legacyGatewayBasedProperties;
  }

  @Bean
  @Primary
  public GatewayBasedProperties gatewayBasedProperties() {
    final GatewayBasedProperties override = new GatewayBasedProperties();
    BeanUtils.copyProperties(legacyGatewayBasedProperties, override);

    // from camunda.cluster.* sections
    populateFromCluster(override);
    populateFromGrpc(override);
    populateFromLongPolling(override);

    return override;
  }

  private void populateFromGrpc(final GatewayBasedProperties override) {
    final Grpc grpc =
        unifiedConfiguration.getCamunda().getApi().getGrpc().withGatewayNetworkProperties();

    final NetworkCfg networkCfg = override.getNetwork();
    networkCfg.setHost(grpc.getAddress());
    networkCfg.setPort(grpc.getPort());

    populateFromSsl(override);
    populateFromInterceptors(override);

    final ThreadsCfg threadsCfg = override.getThreads();
    threadsCfg.setManagementThreads(grpc.getManagementThreads());
  }

  private void populateFromSsl(final GatewayBasedProperties override) {
    final Ssl ssl =
        unifiedConfiguration.getCamunda().getApi().getGrpc().getSsl().withGatewaySslProperties();
    final SecurityCfg securityCfg = override.getSecurity();
    securityCfg.setEnabled(ssl.isEnabled());
    securityCfg.setCertificateChainPath(ssl.getCertificate());
    securityCfg.setPrivateKeyPath(ssl.getCertificatePrivateKey());
  }

  private void populateFromInterceptors(final GatewayBasedProperties override) {
    // Order between legacy and new interceptor props is not guaranteed.
    // Log common interceptors warning instead of using UnifiedConfigurationHelper logging.
    if (!override.getInterceptors().isEmpty()) {
      final String warningMessage =
          String.format(
              "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
              "camunda.api.grpc.interceptors", "zeebe.gateway.interceptors");
      LOGGER.warn(warningMessage);
    }

    final List<Interceptor> interceptors =
        unifiedConfiguration.getCamunda().getApi().getGrpc().getInterceptors();
    if (!interceptors.isEmpty()) {
      final List<InterceptorCfg> interceptorCfgList =
          interceptors.stream().map(Interceptor::toInterceptorCfg).toList();
      override.setInterceptors(interceptorCfgList);
    }
  }

  private void populateFromLongPolling(final GatewayBasedProperties override) {
    final var longPolling =
        unifiedConfiguration
            .getCamunda()
            .getApi()
            .getLongPolling()
            .withGatewayLongPollingProperties();
    final var longPollingCfg = override.getLongPolling();
    longPollingCfg.setEnabled(longPolling.isEnabled());
    longPollingCfg.setTimeout(longPolling.getTimeout());
    longPollingCfg.setProbeTimeout(longPolling.getProbeTimeout());
    longPollingCfg.setMinEmptyResponses(longPolling.getMinEmptyResponses());
  }

  private void populateFromCluster(final GatewayBasedProperties override) {
    populateFromClusterNetwork(override);
    // Rest of camunda.cluster.* sections
  }

  private void populateFromClusterNetwork(final GatewayBasedProperties override) {
    final var network =
        unifiedConfiguration.getCamunda().getCluster().getNetwork().withGatewayNetworkProperties();

    override.getCluster().setHost(network.getHost());
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.atomix.cluster.messaging.MessagingConfig.CompressionAlgorithm;
import io.camunda.configuration.Cluster;
import io.camunda.configuration.Filter;
import io.camunda.configuration.Grpc;
import io.camunda.configuration.Interceptor;
import io.camunda.configuration.InternalApi;
import io.camunda.configuration.KeyStore;
import io.camunda.configuration.Membership;
import io.camunda.configuration.Ssl;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.GatewayBasedProperties;
import io.camunda.configuration.beans.LegacyGatewayBasedProperties;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import io.camunda.zeebe.gateway.impl.configuration.InterceptorCfg;
import io.camunda.zeebe.gateway.impl.configuration.KeyStoreCfg;
import io.camunda.zeebe.gateway.impl.configuration.MembershipCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import io.camunda.zeebe.gateway.impl.configuration.ThreadsCfg;
import java.util.List;
import java.util.Optional;
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
    populateFromRestFilters(override);
    populateFromSecurity(override);

    return override;
  }

  private void populateFromSecurity(final GatewayBasedProperties override) {
    final var tlsCluster =
        unifiedConfiguration
            .getCamunda()
            .getSecurity()
            .getTransportLayerSecurity()
            .getCluster()
            .withGatewayTlsClusterProperties();

    final SecurityCfg clusterSecurity = override.getCluster().getSecurity();
    clusterSecurity.setEnabled(tlsCluster.isEnabled());
    clusterSecurity.setCertificateChainPath(tlsCluster.getCertificateChainPath());
    clusterSecurity.setPrivateKeyPath(tlsCluster.getCertificatePrivateKeyPath());
    clusterSecurity
        .getKeyStore()
        .setFilePath(
            tlsCluster.getKeyStore().withGatewayTlsClusterKeyStoreProperties().getFilePath());
    clusterSecurity
        .getKeyStore()
        .setPassword(
            tlsCluster.getKeyStore().withGatewayTlsClusterKeyStoreProperties().getPassword());
  }

  private void populateFromGrpc(final GatewayBasedProperties override) {
    final Grpc grpc =
        unifiedConfiguration.getCamunda().getApi().getGrpc().withGatewayNetworkProperties();

    final NetworkCfg networkCfg = override.getNetwork();
    networkCfg.setHost(grpc.getAddress());
    networkCfg.setPort(grpc.getPort());
    networkCfg.setMinKeepAliveInterval(grpc.getMinKeepAliveInterval());

    final var ucNetwork =
        unifiedConfiguration.getCamunda().getCluster().getNetwork().withGatewayNetworkProperties();
    networkCfg.setMaxMessageSize(ucNetwork.getMaxMessageSize());

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

    populateFromKeyStore(override);
  }

  private void populateFromKeyStore(final GatewayBasedProperties override) {
    final KeyStore keyStore =
        unifiedConfiguration
            .getCamunda()
            .getApi()
            .getGrpc()
            .getSsl()
            .getKeyStore()
            .withGatewayKeyStoreProperties();
    final KeyStoreCfg keyStoreCfg = override.getSecurity().getKeyStore();
    keyStoreCfg.setFilePath(keyStore.getFilePath());
    keyStoreCfg.setPassword(keyStore.getPassword());
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

  private void populateFromRestFilters(final GatewayBasedProperties override) {
    // Order between legacy and new filters props is not guaranteed.
    // Log common filters warning instead of using UnifiedConfigurationHelper logging.
    if (!override.getFilters().isEmpty()) {
      final String warningMessage =
          String.format(
              "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
              "camunda.api.rest.filters", "zeebe.gateway.filters");
      LOGGER.warn(warningMessage);
    }

    final List<Filter> filters = unifiedConfiguration.getCamunda().getApi().getRest().getFilters();
    if (!filters.isEmpty()) {
      final List<FilterCfg> filterCfgList = filters.stream().map(Filter::toFilterCfg).toList();
      override.setFilters(filterCfgList);
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
    final Cluster cluster = unifiedConfiguration.getCamunda().getCluster().withGatewayProperties();

    populateFromClusterNetwork(override);
    populateFromMembership(override);
    // Rest of camunda.cluster.* sections

    override.getCluster().setInitialContactPoints(cluster.getInitialContactPoints());
    override.getCluster().setClusterName(cluster.getName());
    override.getCluster().setMemberId(cluster.getGatewayId());
    override.getCluster().setSendOnLegacySubject(cluster.isSendOnLegacySubject());

    override
        .getCluster()
        .setMessageCompression(
            CompressionAlgorithm.valueOf(cluster.getCompressionAlgorithm().name()));
  }

  private void populateFromClusterNetwork(final GatewayBasedProperties override) {
    final var network =
        unifiedConfiguration.getCamunda().getCluster().getNetwork().withGatewayNetworkProperties();

    final var gatewayCluster = override.getCluster();

    gatewayCluster.setHost(resolveHost());
    gatewayCluster.setAdvertisedHost(resolveAdvertisedHost());
    gatewayCluster.setSocketSendBuffer(network.getSocketSendBuffer());
    gatewayCluster.setSocketReceiveBuffer(network.getSocketReceiveBuffer());

    populateFromInternalApi(override);
  }

  private String resolveHost() {
    final String internalApiHost =
        unifiedConfiguration
            .getCamunda()
            .getCluster()
            .getNetwork()
            .getInternalApi()
            .withGatewayInternalApiProperties()
            .getHost();

    return internalApiHost != null
        ? internalApiHost
        : unifiedConfiguration
            .getCamunda()
            .getCluster()
            .getNetwork()
            .withGatewayNetworkProperties()
            .getHost();
  }

  private String resolveAdvertisedHost() {
    final String internalApiAdvertisedHost =
        unifiedConfiguration
            .getCamunda()
            .getCluster()
            .getNetwork()
            .getInternalApi()
            .withGatewayInternalApiProperties()
            .getAdvertisedHost();

    return internalApiAdvertisedHost != null
        ? internalApiAdvertisedHost
        : unifiedConfiguration
            .getCamunda()
            .getCluster()
            .getNetwork()
            .withGatewayNetworkProperties()
            .getAdvertisedHost();
  }

  private void populateFromInternalApi(final GatewayBasedProperties override) {
    final InternalApi internalApi =
        unifiedConfiguration
            .getCamunda()
            .getCluster()
            .getNetwork()
            .getInternalApi()
            .withGatewayInternalApiProperties();
    final ClusterCfg clusterCfg = override.getCluster();

    clusterCfg.setPort(internalApi.getPort());
    Optional.ofNullable(internalApi.getAdvertisedPort()).ifPresent(clusterCfg::setAdvertisedPort);
  }

  private void populateFromMembership(final GatewayBasedProperties override) {
    final Membership membership =
        unifiedConfiguration
            .getCamunda()
            .getCluster()
            .getMembership()
            .withGatewayMembershipProperties();
    final MembershipCfg membershipCfg = override.getCluster().getMembership();
    membershipCfg.setBroadcastUpdates(membership.isBroadcastUpdates());
    membershipCfg.setBroadcastDisputes(membership.isBroadcastDisputes());
    membershipCfg.setNotifySuspect(membership.isNotifySuspect());
    membershipCfg.setGossipInterval(membership.getGossipInterval());
    membershipCfg.setGossipFanout(membership.getGossipFanout());
    membershipCfg.setProbeInterval(membership.getProbeInterval());
    membershipCfg.setProbeTimeout(membership.getProbeTimeout());
    membershipCfg.setSuspectProbes(membership.getSuspectProbes());
    membershipCfg.setFailureTimeout(membership.getFailureTimeout());
    membershipCfg.setSyncInterval(membership.getSyncInterval());
  }
}

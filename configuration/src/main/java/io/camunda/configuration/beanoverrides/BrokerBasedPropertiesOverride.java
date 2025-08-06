/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.Azure;
import io.camunda.configuration.Filter;
import io.camunda.configuration.Gcs;
import io.camunda.configuration.Interceptor;
import io.camunda.configuration.S3;
import io.camunda.configuration.SasToken;
import io.camunda.configuration.Ssl;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.LegacyBrokerBasedProperties;
import io.camunda.zeebe.backup.azure.SasTokenConfig;
import io.camunda.zeebe.broker.system.configuration.ConfigManagerCfg;
import io.camunda.zeebe.broker.system.configuration.RaftCfg.FlushConfig;
import io.camunda.zeebe.broker.system.configuration.ThreadsCfg;
import io.camunda.zeebe.broker.system.configuration.backup.AzureBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig.GcsBackupStoreAuth;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import io.camunda.zeebe.gateway.impl.configuration.InterceptorCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
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
@EnableConfigurationProperties(LegacyBrokerBasedProperties.class)
@Profile(value = {"broker", "restore"})
@DependsOn("unifiedConfigurationHelper")
public class BrokerBasedPropertiesOverride {

  private static final Logger LOGGER = LoggerFactory.getLogger(BrokerBasedPropertiesOverride.class);

  private final UnifiedConfiguration unifiedConfiguration;
  private final LegacyBrokerBasedProperties legacyBrokerBasedProperties;

  public BrokerBasedPropertiesOverride(
      final UnifiedConfiguration unifiedConfiguration,
      final LegacyBrokerBasedProperties properties) {
    this.unifiedConfiguration = unifiedConfiguration;
    legacyBrokerBasedProperties = properties;
  }

  @Bean
  @Primary
  public BrokerBasedProperties brokerBasedProperties() {
    final BrokerBasedProperties override = new BrokerBasedProperties();
    BeanUtils.copyProperties(legacyBrokerBasedProperties, override);

    // from camunda.cluster.* sections
    populateFromCluster(override);

    populateFromLongPolling(override);

    populateFromRestFilters(override);

    // from camunda.system.* sections in relation
    // with zeebe.broker.*
    populateFromSystem(override);

    populateFromPrimaryStorage(override);

    populateFromGrpc(override);

    // from camunda.data.* sections
    populateFromData(override);

    // TODO: Populate the rest of the bean using unifiedConfiguration
    //  override.setSampleField(unifiedConfiguration.getSampleField());

    return override;
  }

  private void populateFromGrpc(final BrokerBasedProperties override) {
    final var grpc =
        unifiedConfiguration.getCamunda().getApi().getGrpc().withBrokerNetworkProperties();

    final NetworkCfg networkCfg = override.getGateway().getNetwork();
    networkCfg.setHost(grpc.getAddress());
    networkCfg.setPort(grpc.getPort());

    populateFromSsl(override);
    populateFromInterceptors(override);

    final io.camunda.zeebe.gateway.impl.configuration.ThreadsCfg threadsCfg =
        override.getGateway().getThreads();
    threadsCfg.setManagementThreads(grpc.getManagementThreads());
  }

  private void populateFromSsl(final BrokerBasedProperties override) {
    final Ssl ssl =
        unifiedConfiguration.getCamunda().getApi().getGrpc().getSsl().withBrokerSslProperties();
    final SecurityCfg securityCfg = override.getGateway().getSecurity();
    securityCfg.setEnabled(ssl.isEnabled());
    securityCfg.setCertificateChainPath(ssl.getCertificate());
    securityCfg.setPrivateKeyPath(ssl.getCertificatePrivateKey());
  }

  private void populateFromInterceptors(final BrokerBasedProperties override) {
    // Order between legacy and new interceptor props is not guaranteed.
    // Log common interceptors warning instead of using UnifiedConfigurationHelper logging.
    if (!override.getGateway().getInterceptors().isEmpty()) {
      final String warningMessage =
          String.format(
              "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
              "camunda.api.grpc.interceptors", "zeebe.broker.gateway.interceptors");
      LOGGER.warn(warningMessage);
    }

    final List<Interceptor> interceptors =
        unifiedConfiguration.getCamunda().getApi().getGrpc().getInterceptors();
    if (!interceptors.isEmpty()) {
      final List<InterceptorCfg> interceptorCfgList =
          interceptors.stream().map(Interceptor::toInterceptorCfg).toList();
      override.getGateway().setInterceptors(interceptorCfgList);
    }
  }

  private void populateFromCluster(final BrokerBasedProperties override) {
    final var cluster = unifiedConfiguration.getCamunda().getCluster();

    override.getCluster().setNodeId(cluster.getNodeId());
    override.getCluster().setPartitionsCount(cluster.getPartitionCount());
    override.getCluster().setReplicationFactor(cluster.getReplicationFactor());
    override.getCluster().setClusterSize(cluster.getSize());

    populateFromRaftProperties(override);
    populateFromClusterMetadata(override);
    populateFromClusterNetwork(override);
    // Rest of camunda.cluster.* sections
  }

  private void populateFromLongPolling(final BrokerBasedProperties override) {
    final var longPolling =
        unifiedConfiguration
            .getCamunda()
            .getApi()
            .getLongPolling()
            .withBrokerLongPollingProperties();
    final var longPollingCfg = override.getGateway().getLongPolling();
    longPollingCfg.setEnabled(longPolling.isEnabled());
    longPollingCfg.setTimeout(longPolling.getTimeout());
    longPollingCfg.setProbeTimeout(longPolling.getProbeTimeout());
    longPollingCfg.setMinEmptyResponses(longPolling.getMinEmptyResponses());
  }

  private void populateFromRaftProperties(final BrokerBasedProperties override) {
    final var raft = unifiedConfiguration.getCamunda().getCluster().getRaft();
    override.getCluster().setHeartbeatInterval(raft.getHeartbeatInterval());
    override.getCluster().setElectionTimeout(raft.getElectionTimeout());
    override.getCluster().getRaft().setEnablePriorityElection(raft.isPriorityElectionEnabled());

    // Set flush configuration
    final var flushConfig = new FlushConfig(raft.isFlushEnabled(), raft.getFlushDelay());
    override.getCluster().getRaft().setFlush(flushConfig);
  }

  private void populateFromClusterMetadata(final BrokerBasedProperties override) {
    final var metadata = unifiedConfiguration.getCamunda().getCluster().getMetadata();
    final var syncDelay = metadata.getSyncDelay();
    final var syncTimeout = metadata.getSyncRequestTimeout();
    final var gossipFanout = metadata.getGossipFanout();
    final var configManagerGossipConfig =
        new ClusterConfigurationGossiperConfig(syncDelay, syncTimeout, gossipFanout);
    override.getCluster().setConfigManager(new ConfigManagerCfg(configManagerGossipConfig));
  }

  private void populateFromClusterNetwork(final BrokerBasedProperties override) {
    final var network =
        unifiedConfiguration.getCamunda().getCluster().getNetwork().withBrokerNetworkProperties();

    override.getNetwork().setHost(network.getHost());
  }

  private void populateFromRestFilters(final BrokerBasedProperties override) {
    // Order between legacy and new filters props is not guaranteed.
    // Log common filters warning instead of using UnifiedConfigurationHelper logging.
    if (!override.getGateway().getFilters().isEmpty()) {
      final String warningMessage =
          String.format(
              "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
              "camunda.api.rest.filters", "zeebe.broker.gateway.filters");
      LOGGER.warn(warningMessage);
    }

    final List<Filter> filters = unifiedConfiguration.getCamunda().getApi().getRest().getFilters();
    if (!filters.isEmpty()) {
      final List<FilterCfg> filterCfgList = filters.stream().map(Filter::toFilterCfg).toList();
      override.getGateway().setFilters(filterCfgList);
    }
  }

  private void populateFromSystem(final BrokerBasedProperties override) {
    final var system = unifiedConfiguration.getCamunda().getSystem();

    final var threadsCfg = new ThreadsCfg();
    threadsCfg.setCpuThreadCount(system.getCpuThreadCount());
    threadsCfg.setIoThreadCount(system.getIoThreadCount());
    override.setThreads(threadsCfg);

    final var enableVersionCheck =
        unifiedConfiguration.getCamunda().getSystem().getUpgrade().getEnableVersionCheck();
    override.getExperimental().setVersionCheckRestrictionEnabled(enableVersionCheck);
  }

  private void populateFromData(final BrokerBasedProperties override) {
    populateFromBackup(override);
  }

  private void populateFromBackup(final BrokerBasedProperties override) {
    populateFromS3(override);
    populateFromGcs(override);
    populateFromAzure(override);
  }

  private void populateFromS3(final BrokerBasedProperties override) {
    final S3 s3 = unifiedConfiguration.getCamunda().getData().getBackup().getS3();
    final S3BackupStoreConfig s3BackupStoreConfig = override.getData().getBackup().getS3();
    s3BackupStoreConfig.setBucketName(s3.getBucketName());
    s3BackupStoreConfig.setEndpoint(s3.getEndpoint());
    s3BackupStoreConfig.setRegion(s3.getRegion());
    s3BackupStoreConfig.setAccessKey(s3.getAccessKey());
    s3BackupStoreConfig.setSecretKey(s3.getSecretKey());
    s3BackupStoreConfig.setApiCallTimeout(s3.getApiCallTimeout());
    s3BackupStoreConfig.setForcePathStyleAccess(s3.isForcePathStyleAccess());
    s3BackupStoreConfig.setCompression(s3.getCompression());
    s3BackupStoreConfig.setMaxConcurrentConnections(s3.getMaxConcurrentConnections());
    s3BackupStoreConfig.setConnectionAcquisitionTimeout(s3.getConnectionAcquisitionTimeout());
    s3BackupStoreConfig.setBasePath(s3.getBasePath());
    s3BackupStoreConfig.setSupportLegacyMd5(s3.isSupportLegacyMd5());

    override.getData().getBackup().setS3(s3BackupStoreConfig);
  }

  private void populateFromPrimaryStorage(final BrokerBasedProperties override) {
    final var primaryStorage = unifiedConfiguration.getCamunda().getData().getPrimaryStorage();
    final var data = override.getData();
    data.setDirectory(primaryStorage.getDirectory());
    data.setRuntimeDirectory(primaryStorage.getRuntimeDirectory());
    data.setLogIndexDensity(primaryStorage.getLogStream().getLogIndexDensity());
    data.setLogSegmentSize(primaryStorage.getLogStream().getLogSegmentSize());
    final var brokerDiskConfig = data.getDisk();
    final var unifiedDiskConfig = primaryStorage.getDisk();
    brokerDiskConfig.getFreeSpace().setProcessing(unifiedDiskConfig.getFreeSpace().getProcessing());
    brokerDiskConfig
        .getFreeSpace()
        .setReplication(unifiedDiskConfig.getFreeSpace().getReplication());
    brokerDiskConfig.setEnableMonitoring(unifiedDiskConfig.isMonitoringEnabled());
    brokerDiskConfig.setMonitoringInterval(unifiedDiskConfig.getMonitoringInterval());
  }

  private void populateFromGcs(final BrokerBasedProperties override) {
    final Gcs gcs = unifiedConfiguration.getCamunda().getData().getBackup().getGcs();
    final GcsBackupStoreConfig gcsBackupStoreConfig = override.getData().getBackup().getGcs();
    gcsBackupStoreConfig.setBucketName(gcs.getBucketName());
    gcsBackupStoreConfig.setBasePath(gcs.getBasePath());
    gcsBackupStoreConfig.setHost(gcs.getHost());
    gcsBackupStoreConfig.setAuth(GcsBackupStoreAuth.valueOf(gcs.getAuth().name()));

    override.getData().getBackup().setGcs(gcsBackupStoreConfig);
  }

  private void populateFromAzure(final BrokerBasedProperties override) {
    final Azure azure = unifiedConfiguration.getCamunda().getData().getBackup().getAzure();
    final AzureBackupStoreConfig azureBackupStoreConfig = override.getData().getBackup().getAzure();
    azureBackupStoreConfig.setEndpoint(azure.getEndpoint());
    azureBackupStoreConfig.setAccountName(azure.getAccountName());
    azureBackupStoreConfig.setAccountKey(azure.getAccountKey());
    azureBackupStoreConfig.setConnectionString(azure.getConnectionString());
    azureBackupStoreConfig.setBasePath(azure.getBasePath());
    azureBackupStoreConfig.setCreateContainer(azure.isCreateContainer());
    populateFromSasToken(override);

    override.getData().getBackup().setAzure(azureBackupStoreConfig);
  }

  private void populateFromSasToken(final BrokerBasedProperties override) {
    final SasToken sasToken =
        unifiedConfiguration.getCamunda().getData().getBackup().getAzure().getSasToken();
    final SasTokenConfig sasTokenConfig = override.getData().getBackup().getAzure().getSasToken();

    if (sasToken != null) {
      override.getData().getBackup().getAzure().setSasToken(sasToken.toSasTokenConfig());
    } else if (sasTokenConfig != null) {
      override
          .getData()
          .getBackup()
          .getAzure()
          .setSasToken(SasToken.fromSasTokenConfig(sasTokenConfig).toSasTokenConfig());
    }
  }
}

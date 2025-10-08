/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.atomix.cluster.messaging.MessagingConfig.CompressionAlgorithm;
import io.camunda.configuration.Azure;
import io.camunda.configuration.CommandApi;
import io.camunda.configuration.Data;
import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.Export;
import io.camunda.configuration.Exporter;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.Filter;
import io.camunda.configuration.FixedPartition;
import io.camunda.configuration.Gcs;
import io.camunda.configuration.Interceptor;
import io.camunda.configuration.InterceptorPlugin;
import io.camunda.configuration.InternalApi;
import io.camunda.configuration.KeyStore;
import io.camunda.configuration.Limit;
import io.camunda.configuration.Membership;
import io.camunda.configuration.Metrics;
import io.camunda.configuration.NodeIdProvider.Type;
import io.camunda.configuration.Partitioning;
import io.camunda.configuration.PrimaryStorage;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.Processing;
import io.camunda.configuration.Rdbms;
import io.camunda.configuration.S3;
import io.camunda.configuration.SasToken;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.Ssl;
import io.camunda.configuration.Throttle;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.Write;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.LegacyBrokerBasedProperties;
import io.camunda.zeebe.backup.azure.SasTokenConfig;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.system.configuration.ConfigManagerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.broker.system.configuration.ExportingCfg;
import io.camunda.zeebe.broker.system.configuration.MembershipCfg;
import io.camunda.zeebe.broker.system.configuration.PartitioningCfg;
import io.camunda.zeebe.broker.system.configuration.RaftCfg.FlushConfig;
import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg;
import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg.CommandApiCfg;
import io.camunda.zeebe.broker.system.configuration.ThreadsCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.LimitCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.RateLimitCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.ThrottleCfg;
import io.camunda.zeebe.broker.system.configuration.backup.AzureBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig.GcsBackupStoreAuth;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import io.camunda.zeebe.gateway.impl.configuration.InterceptorCfg;
import io.camunda.zeebe.gateway.impl.configuration.KeyStoreCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
@EnableConfigurationProperties(LegacyBrokerBasedProperties.class)
@Profile(value = {"broker", "restore"})
@DependsOn("unifiedConfigurationHelper")
public class BrokerBasedPropertiesOverride {

  private static final Logger LOGGER = LoggerFactory.getLogger(BrokerBasedPropertiesOverride.class);
  private static final String CAMUNDA_EXPORTER_CLASS_NAME = "io.camunda.exporter.CamundaExporter";
  private static final String CAMUNDA_EXPORTER_NAME = "camundaexporter";
  private static final String RDBMS_EXPORTER_CLASS_NAME = "io.camunda.exporter.rdbms.RdbmsExporter";
  private static final String RDBMS_EXPORTER_NAME = "rdbms";
  private static final String CONNECT_INTERCEPTOR_PLUGINS_BREADCRUMB = "connect.interceptorPlugins";

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

    if (unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getType()
        == SecondaryStorageType.rdbms) {
      populateRdbmsExporter(override);
    } else {
      populateCamundaExporter(override);
    }

    populateFromExporters(override);

    populateFromMonitoring(override);

    // TODO: Populate the rest of the bean using unifiedConfiguration
    //  override.setSampleField(unifiedConfiguration.getSampleField());
    populateFromProcessing(override);

    populateFromFlowControl(override);

    return override;
  }

  private void populateFromProcessing(final BrokerBasedProperties override) {
    final Processing processing = unifiedConfiguration.getCamunda().getProcessing();

    // processing
    override.getProcessing().setMaxCommandsInBatch(processing.getMaxCommandsInBatch());
    override.getProcessing().setEnableAsyncScheduledTasks(processing.isEnableAsyncScheduledTasks());
    override
        .getProcessing()
        .setScheduledTaskCheckInterval(processing.getScheduledTasksCheckInterval());
    override.getProcessing().setSkipPositions(processing.getSkipPositions());

    // consistency checks
    override
        .getExperimental()
        .getConsistencyChecks()
        .setEnablePreconditions(processing.isEnablePreconditionsCheck());
    override
        .getExperimental()
        .getConsistencyChecks()
        .setEnableForeignKeyChecks(processing.isEnableForeignKeyChecks());
    // features
    override
        .getExperimental()
        .getFeatures()
        .setEnableYieldingDueDateChecker(processing.isEnableYieldingDueDateChecker());
    override
        .getExperimental()
        .getFeatures()
        .setEnableMessageTtlCheckerAsync(processing.isEnableAsyncMessageTtlChecker());
    override
        .getExperimental()
        .getFeatures()
        .setEnableTimerDueDateCheckerAsync(processing.isEnableAsyncTimerDuedateChecker());
    override
        .getExperimental()
        .getFeatures()
        .setEnableStraightThroughProcessingLoopDetector(
            processing.isEnableStraightthroughProcessingLoopDetector());
    override
        .getExperimental()
        .getFeatures()
        .setEnableMessageBodyOnExpired(processing.isEnableMessageBodyOnExpired());

    populateFromEngine(override);
  }

  private void populateFromEngine(final BrokerBasedProperties override) {
    populateFromDistribution(override);
    populateFromBatchOperations(override);
  }

  private void populateFromDistribution(final BrokerBasedProperties override) {
    final var distribution =
        unifiedConfiguration.getCamunda().getProcessing().getEngine().getDistribution();

    final var distributionCfg = override.getExperimental().getEngine().getDistribution();
    distributionCfg.setMaxBackoffDuration(distribution.getMaxBackoffDuration());
    distributionCfg.setRedistributionInterval(distribution.getRedistributionInterval());
  }

  private void populateFromBatchOperations(final BrokerBasedProperties override) {
    final var engineBatchOperation =
        unifiedConfiguration.getCamunda().getProcessing().getEngine().getBatchOperations();
    override
        .getExperimental()
        .getEngine()
        .getBatchOperations()
        .setSchedulerInterval(engineBatchOperation.getSchedulerInterval());
  }

  private void populateFromFlowControl(final BrokerBasedProperties override) {
    populateFromBackpressureLimitRequest(override);
    populateFromWrite(override);
  }

  private void populateFromBackpressureLimitRequest(final BrokerBasedProperties override) {
    final Limit request =
        unifiedConfiguration.getCamunda().getProcessing().getFlowControl().getRequest();

    if (request == null) {
      return;
    }

    final LimitCfg limitCfg =
        Optional.ofNullable(override.getFlowControl().getRequest()).orElse(new LimitCfg());
    limitCfg.setEnabled(request.isEnabled());
    limitCfg.setUseWindowed(request.isWindowed());
    // Convert kebab-case to uppercase with underscores for enum compatibility
    limitCfg.setAlgorithm(
        request.getAlgorithm() == null
            ? "AIMD"
            : request.getAlgorithm().replace("-", "_").toUpperCase());

    // AIMD algorithm properties
    limitCfg.getAimd().setRequestTimeout(request.getAimdRequestTimeout());
    limitCfg.getAimd().setInitialLimit(request.getAimdInitialLimit());
    limitCfg.getAimd().setMinLimit(request.getAimdMinLimit());
    limitCfg.getAimd().setMaxLimit(request.getAimdMaxLimit());
    limitCfg.getAimd().setBackoffRatio(request.getAimdBackoffRatio());

    // Fixed algorithm properties
    limitCfg.getFixed().setLimit(request.getFixedLimit());

    // Vegas algorithm properties
    limitCfg.getVegas().setAlpha(request.getVegasAlpha());
    limitCfg.getVegas().setBeta(request.getVegasBeta());
    limitCfg.getVegas().setInitialLimit(request.getVegasInitialLimit());

    // Gradient algorithm properties
    limitCfg.getGradient().setMinLimit(request.getGradientMinLimit());
    limitCfg.getGradient().setInitialLimit(request.getGradientInitialLimit());
    limitCfg.getGradient().setRttTolerance(request.getGradientRttTolerance());

    // Gradient2 algorithm properties
    limitCfg.getGradient2().setMinLimit(request.getGradient2MinLimit());
    limitCfg.getGradient2().setInitialLimit(request.getGradient2InitialLimit());
    limitCfg.getGradient2().setRttTolerance(request.getGradient2RttTolerance());
    limitCfg.getGradient2().setLongWindow(request.getGradient2LongWindow());

    // Legacy Vegas algorithm properties
    limitCfg.getLegacyVegas().setInitialLimit(request.getLegacyVegasInitialLimit());
    limitCfg.getLegacyVegas().setMaxConcurrency(request.getLegacyVegasMaxConcurrency());
    limitCfg.getLegacyVegas().setAlphaLimit(request.getLegacyVegasAlphaLimit());
    limitCfg.getLegacyVegas().setBetaLimit(request.getLegacyVegasBetaLimit());

    override.getFlowControl().setRequest(limitCfg);
  }

  private void populateFromWrite(final BrokerBasedProperties override) {
    final Write write =
        unifiedConfiguration.getCamunda().getProcessing().getFlowControl().getWrite();

    if (write == null) {
      return;
    }

    final RateLimitCfg rateLimitCfg =
        Optional.ofNullable(override.getFlowControl().getWrite()).orElse(new RateLimitCfg());
    rateLimitCfg.setEnabled(write.isEnabled());
    rateLimitCfg.setLimit(write.getLimit());
    rateLimitCfg.setRampUp(write.getRampUp());
    override.getFlowControl().setWrite(rateLimitCfg);

    populateFromThrottle(override);
  }

  private void populateFromThrottle(final BrokerBasedProperties override) {
    final Throttle throttle =
        unifiedConfiguration.getCamunda().getProcessing().getFlowControl().getWrite().getThrottle();

    final ThrottleCfg throttleCfg = override.getFlowControl().getWrite().getThrottling();
    throttleCfg.setEnabled(throttle.isEnabled());
    throttleCfg.setAcceptableBacklog(throttle.getAcceptableBacklog());
    throttleCfg.setMinimumLimit(throttle.getMinimumLimit());
    throttleCfg.setResolution(throttle.getResolution());
  }

  private void populateFromGrpc(final BrokerBasedProperties override) {
    final var grpc =
        unifiedConfiguration.getCamunda().getApi().getGrpc().withBrokerNetworkProperties();

    final NetworkCfg networkCfg = override.getGateway().getNetwork();
    networkCfg.setHost(grpc.getAddress());
    networkCfg.setPort(grpc.getPort());
    networkCfg.setMinKeepAliveInterval(grpc.getMinKeepAliveInterval());

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

    populateFromKeyStore(override);
  }

  private void populateFromKeyStore(final BrokerBasedProperties override) {
    final KeyStore keyStore =
        unifiedConfiguration
            .getCamunda()
            .getApi()
            .getGrpc()
            .getSsl()
            .getKeyStore()
            .withBrokerKeyStoreProperties();
    final KeyStoreCfg keyStoreCfg = override.getGateway().getSecurity().getKeyStore();
    keyStoreCfg.setFilePath(keyStore.getFilePath());
    keyStoreCfg.setPassword(keyStore.getPassword());
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
    final var cluster = unifiedConfiguration.getCamunda().getCluster().withBrokerProperties();

    override.getCluster().setInitialContactPoints(cluster.getInitialContactPoints());
    if (cluster.getNodeIdProvider().getType() == Type.FIXED) {
      override.getCluster().setNodeId(cluster.getNodeId());
    }
    override.getCluster().setPartitionsCount(cluster.getPartitionCount());
    override.getCluster().setReplicationFactor(cluster.getReplicationFactor());
    override.getCluster().setClusterSize(cluster.getSize());
    override.getCluster().setClusterName(cluster.getName());
    override.getCluster().setClusterId(cluster.getClusterId());

    populateFromMembership(override);
    populateFromRaftProperties(override);
    populateFromClusterMetadata(override);
    populateFromClusterNetwork(override);

    override
        .getCluster()
        .setMessageCompression(
            CompressionAlgorithm.valueOf(cluster.getCompressionAlgorithm().name()));

    populateFromGlobalListeners(override);

    populateFromPartitioning(override);
  }

  private void populateFromPartitioning(final BrokerBasedProperties override) {
    final Partitioning partitioning =
        unifiedConfiguration.getCamunda().getCluster().getPartitioning();

    // Order between legacy and new partitioning props is not guaranteed.
    // Log common partitioning warning instead of using UnifiedConfigurationHelper logging.
    final var partioningCfg = override.getExperimental().getPartitioning();
    if (partioningCfg.getScheme() == Scheme.FIXED && !partioningCfg.getFixed().isEmpty()) {
      final String warningMessage =
          String.format(
              "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
              "camunda.cluster.partitioning.fixed", "zeebe.broker.experimental.partitioning.fixed");
      LOGGER.warn(warningMessage);
    }

    if (partitioning.getScheme() == Partitioning.Scheme.FIXED
        && !partitioning.getFixed().isEmpty()) {
      final PartitioningCfg partitioningCfg = override.getExperimental().getPartitioning();
      partitioningCfg.setScheme(Scheme.valueOf(partitioning.getScheme().name()));
      final var fixedPartitionCfgList =
          partitioning.getFixed().stream().map(FixedPartition::toFixedPartitionCfg).toList();
      partitioningCfg.setFixed(fixedPartitionCfgList);
    }
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

  private void populateFromMembership(final BrokerBasedProperties override) {
    final Membership membership =
        unifiedConfiguration
            .getCamunda()
            .getCluster()
            .getMembership()
            .withBrokerMembershipProperties();
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

  private void populateFromRaftProperties(final BrokerBasedProperties override) {
    final var raft = unifiedConfiguration.getCamunda().getCluster().getRaft();
    override.getCluster().setHeartbeatInterval(raft.getHeartbeatInterval());
    override.getCluster().setElectionTimeout(raft.getElectionTimeout());
    override.getCluster().getRaft().setEnablePriorityElection(raft.isPriorityElectionEnabled());

    // Set flush configuration
    final var flushConfig = new FlushConfig(raft.isFlushEnabled(), raft.getFlushDelay());
    override.getCluster().getRaft().setFlush(flushConfig);

    override.getExperimental().setMaxAppendsPerFollower(raft.getMaxAppendsPerFollower());
    override.getExperimental().setMaxAppendBatchSize(raft.getMaxAppendBatchSize());
    override.getExperimental().getRaft().setRequestTimeout(raft.getRequestTimeout());
    override
        .getExperimental()
        .getRaft()
        .setSnapshotRequestTimeout(raft.getSnapshotRequestTimeout());
    override.getExperimental().getRaft().setSnapshotChunkSize(raft.getSnapshotChunkSize());
    override
        .getExperimental()
        .getRaft()
        .setConfigurationChangeTimeout(raft.getConfigurationChangeTimeout());
    override
        .getExperimental()
        .getRaft()
        .setMaxQuorumResponseTimeout(raft.getMaxQuorumResponseTimeout());
    override
        .getExperimental()
        .getRaft()
        .setMinStepDownFailureCount(raft.getMinStepDownFailureCount());
    override
        .getExperimental()
        .getRaft()
        .setPreferSnapshotReplicationThreshold(raft.getPreferSnapshotReplicationThreshold());
    override
        .getExperimental()
        .getRaft()
        .setPreallocateSegmentFiles(raft.isPreallocateSegmentFiles());
    override
        .getExperimental()
        .getRaft()
        .setSegmentPreallocationStrategy(raft.getSegmentPreallocationStrategy());
  }

  private void populateFromClusterMetadata(final BrokerBasedProperties override) {
    final var metadata = unifiedConfiguration.getCamunda().getCluster().getMetadata();
    final var syncDelay = metadata.getSyncDelay();
    final var syncTimeout = metadata.getSyncRequestTimeout();
    final var gossipFanout = metadata.getGossipFanout();
    final var syncInitializerDelay = metadata.getSyncInitializerDelay();
    final var configManagerGossipConfig =
        new ClusterConfigurationGossiperConfig(
            syncDelay, syncTimeout, gossipFanout, syncInitializerDelay);
    override.getCluster().setConfigManager(new ConfigManagerCfg(configManagerGossipConfig));
  }

  private void populateFromClusterNetwork(final BrokerBasedProperties override) {
    final var network =
        unifiedConfiguration.getCamunda().getCluster().getNetwork().withBrokerNetworkProperties();

    final var brokerNetwork = override.getNetwork();
    brokerNetwork.setHost(network.getHost());
    brokerNetwork.setAdvertisedHost(network.getAdvertisedHost());
    brokerNetwork.setPortOffset(network.getPortOffset());
    brokerNetwork.setMaxMessageSize(network.getMaxMessageSize());
    brokerNetwork.setSocketSendBuffer(network.getSocketSendBuffer());
    brokerNetwork.setSocketReceiveBuffer(network.getSocketReceiveBuffer());
    brokerNetwork.setHeartbeatTimeout(network.getHeartbeatTimeout());
    brokerNetwork.setHeartbeatInterval(network.getHeartbeatInterval());

    final var ucNetwork =
        unifiedConfiguration.getCamunda().getCluster().getNetwork().withBrokerNetworkProperties();
    override.getGateway().getNetwork().setMaxMessageSize(ucNetwork.getMaxMessageSize());

    populateFromCommandApi(override);
    populateFromInternalApi(override);
  }

  private void populateFromInternalApi(final BrokerBasedProperties override) {
    final InternalApi internalApi =
        unifiedConfiguration
            .getCamunda()
            .getCluster()
            .getNetwork()
            .getInternalApi()
            .withBrokerInternalApiProperties();

    final SocketBindingCfg socketBindingCfg = override.getNetwork().getInternalApi();

    socketBindingCfg.setHost(internalApi.getHost());
    socketBindingCfg.setPort(internalApi.getPort());
    socketBindingCfg.setAdvertisedHost(internalApi.getAdvertisedHost());
    Optional.ofNullable(internalApi.getAdvertisedPort())
        .ifPresent(socketBindingCfg::setAdvertisedPort);
  }

  private void populateFromCommandApi(final BrokerBasedProperties override) {
    final CommandApi commandApi =
        unifiedConfiguration.getCamunda().getCluster().getNetwork().getCommandApi();
    final CommandApiCfg commandApiCfg = override.getNetwork().getCommandApi();

    commandApiCfg.setHost(commandApi.getHost());
    Optional.ofNullable(commandApi.getPort()).ifPresent(commandApiCfg::setPort);
    commandApiCfg.setAdvertisedHost(commandApi.getAdvertisedHost());
    Optional.ofNullable(commandApi.getAdvertisedPort()).ifPresent(commandApiCfg::setAdvertisedPort);
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
    final Data data = unifiedConfiguration.getCamunda().getData();
    override.getData().setSnapshotPeriod(data.getSnapshotPeriod());

    populateFromExport(override);
    populateFromBackup(override);
  }

  private void populateFromExport(final BrokerBasedProperties override) {
    final Export export = unifiedConfiguration.getCamunda().getData().getExport();
    final var exportingCfg =
        new ExportingCfg(export.getSkipRecords(), export.getDistributionInterval());
    override.setExporting(exportingCfg);
  }

  private void populateFromBackup(final BrokerBasedProperties override) {
    final PrimaryStorageBackup primaryStorageBackup =
        unifiedConfiguration.getCamunda().getData().getPrimaryStorage().getBackup();
    final BackupCfg backupCfg = override.getData().getBackup();
    backupCfg.setStore(BackupStoreType.valueOf(primaryStorageBackup.getStore().name()));

    populateFromS3(override);
    populateFromGcs(override);
    populateFromAzure(override);
    populateFromFilesystem(override);

    override.getData().setBackup(backupCfg);
  }

  private void populateFromS3(final BrokerBasedProperties override) {
    final S3 s3 =
        unifiedConfiguration.getCamunda().getData().getPrimaryStorage().getBackup().getS3();
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
    data.setInitializationMode(primaryStorage.getInitializationMode());
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

    // Migrate RocksDB configuration from new unified config to old broker config structure
    populateFromRocksDb(override, primaryStorage);

    populateBackupScheduler(override, primaryStorage.getBackup());
  }

  private void populateFromRocksDb(
      final BrokerBasedProperties override, final PrimaryStorage primaryStorage) {
    final var unifiedRocksDb = primaryStorage.getRocksDb();
    final var brokerRocksDb = override.getExperimental().getRocksdb();

    if (brokerRocksDb.getColumnFamilyOptions() != null
        && !brokerRocksDb.getColumnFamilyOptions().isEmpty()) {
      LOGGER.warn(
          "Legacy column family options are deprecated! "
              + "Please use camunda.data.primary-storage.rocks-db.column-family-options.* instead.");
    }

    if (!unifiedRocksDb.getColumnFamilyOptions().isEmpty()) {
      brokerRocksDb.setColumnFamilyOptions(unifiedRocksDb.getColumnFamilyOptions());
    }

    brokerRocksDb.setEnableStatistics(unifiedRocksDb.isStatisticsEnabled());
    brokerRocksDb.setAccessMetrics(
        AccessMetricsConfiguration.Kind.valueOf(unifiedRocksDb.getAccessMetrics().name()));
    brokerRocksDb.setMemoryLimit(unifiedRocksDb.getMemoryLimit());
    brokerRocksDb.setMemoryAllocationStrategy(unifiedRocksDb.getMemoryAllocationStrategy());
    brokerRocksDb.setMaxOpenFiles(unifiedRocksDb.getMaxOpenFiles());
    brokerRocksDb.setMaxWriteBufferNumber(unifiedRocksDb.getMaxWriteBufferNumber());
    brokerRocksDb.setMinWriteBufferNumberToMerge(unifiedRocksDb.getMinWriteBufferNumberToMerge());
    brokerRocksDb.setIoRateBytesPerSecond(unifiedRocksDb.getIoRateBytesPerSecond());
    brokerRocksDb.setDisableWal(unifiedRocksDb.isWalDisabled());
    brokerRocksDb.setEnableSstPartitioning(unifiedRocksDb.isSstPartitioningEnabled());
  }

  private void populateFromGcs(final BrokerBasedProperties override) {
    final Gcs gcs =
        unifiedConfiguration.getCamunda().getData().getPrimaryStorage().getBackup().getGcs();
    final GcsBackupStoreConfig gcsBackupStoreConfig = override.getData().getBackup().getGcs();
    gcsBackupStoreConfig.setBucketName(gcs.getBucketName());
    gcsBackupStoreConfig.setBasePath(gcs.getBasePath());
    gcsBackupStoreConfig.setHost(gcs.getHost());
    gcsBackupStoreConfig.setAuth(GcsBackupStoreAuth.valueOf(gcs.getAuth().name()));

    override.getData().getBackup().setGcs(gcsBackupStoreConfig);
  }

  private void populateFromAzure(final BrokerBasedProperties override) {
    final Azure azure =
        unifiedConfiguration.getCamunda().getData().getPrimaryStorage().getBackup().getAzure();
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
        unifiedConfiguration
            .getCamunda()
            .getData()
            .getPrimaryStorage()
            .getBackup()
            .getAzure()
            .getSasToken();
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

  private void populateFromFilesystem(final BrokerBasedProperties override) {
    final Filesystem filesystem =
        unifiedConfiguration.getCamunda().getData().getPrimaryStorage().getBackup().getFilesystem();
    final FilesystemBackupStoreConfig filesystemBackupStoreConfig =
        override.getData().getBackup().getFilesystem();
    filesystemBackupStoreConfig.setBasePath(filesystem.getBasePath());

    override.getData().getBackup().setFilesystem(filesystemBackupStoreConfig);
  }

  private void populateBackupScheduler(
      final BrokerBasedProperties override, final PrimaryStorageBackup primaryStorageBackup) {
    final BackupCfg backupCfg = override.getData().getBackup();
    backupCfg.setRequired(primaryStorageBackup.isRequired());
    backupCfg.setContinuous(primaryStorageBackup.isContinuous());
    backupCfg.setSchedule(primaryStorageBackup.getSchedule());
    backupCfg.setCheckpointInterval(primaryStorageBackup.getCheckpointInterval());
    backupCfg.setOffset(primaryStorageBackup.getOffset());
    backupCfg.setRetention(primaryStorageBackup.getRetention());
  }

  private void populateCamundaExporter(final BrokerBasedProperties override) {
    final SecondaryStorage secondaryStorage =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    if (!secondaryStorage.getAutoconfigureCamundaExporter()) {
      LOGGER.debug("Skipping autoconfiguration of the (default) exporter 'camundaexporter'");
      return;
    }

    final DocumentBasedSecondaryStorageDatabase database;
    switch (secondaryStorage.getType()) {
      case elasticsearch ->
          database =
              unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getElasticsearch();
      case opensearch ->
          database =
              unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getOpensearch();
      default -> {
        // RDBMS and NONE are not supported.
        return;
      }
    }

    /* Load exporter config map */

    ExporterCfg exporter = override.getCamundaExporter();
    if (exporter == null) {
      exporter = new ExporterCfg();
      exporter.setClassName(CAMUNDA_EXPORTER_CLASS_NAME);
      exporter.setArgs(new LinkedHashMap<>());
      override.getExporters().put(CAMUNDA_EXPORTER_NAME, exporter);
    }

    /* Override config map values */

    // https://github.com/camunda/camunda/issues/37880
    // it is possible to have an exporter with no args defined
    Map<String, Object> args = exporter.getArgs();
    if (args == null) {
      args = new LinkedHashMap<>();
      exporter.setArgs(args);
    }

    setArg(args, "history.retention.enabled", secondaryStorage.getRetention().isEnabled());
    setArg(args, "history.retention.minimumAge", secondaryStorage.getRetention().getMinimumAge());

    setArg(args, "connect.type", secondaryStorage.getType().name());
    setArg(args, "connect.url", database.getUrl());
    setArg(args, "connect.clusterName", database.getClusterName());
    setArg(args, "connect.dateFormat", database.getDateFormat());
    final var socketTimeout = database.getSocketTimeout();
    setArg(args, "connect.socketTimeout", socketTimeout != null ? socketTimeout.toMillis() : null);
    final var connectionTimeout = database.getConnectionTimeout();
    setArg(
        args,
        "connect.connectTimeout",
        connectionTimeout != null ? connectionTimeout.toMillis() : null);

    // Add security configuration mapping
    if (database.getSecurity() != null) {
      setArg(args, "connect.security.enabled", database.getSecurity().isEnabled());
      setArg(args, "connect.security.certificatePath", database.getSecurity().getCertificatePath());
      setArg(args, "connect.security.verifyHostname", database.getSecurity().isVerifyHostname());
      setArg(args, "connect.security.selfSigned", database.getSecurity().isSelfSigned());
    }

    populateInterceptorPlugins(database, args);

    setArg(args, "connect.username", database.getUsername());
    setArg(args, "connect.password", database.getPassword());

    setArg(args, "connect.indexPrefix", database.getIndexPrefix());

    setArg(args, "index.numberOfShards", database.getNumberOfShards());
    setArg(args, "index.numberOfReplicas", database.getNumberOfReplicas());
    setArg(args, "index.variableSizeThreshold", database.getVariableSizeThreshold());
    if (database.getTemplatePriority() != null) {
      setArg(args, "index.templatePriority", database.getTemplatePriority());
    }
    if (!database.getNumberOfReplicasPerIndex().isEmpty()) {
      setArg(args, "index.replicasByIndexName", database.getNumberOfReplicasPerIndex());
    }
    if (!database.getNumberOfShardsPerIndex().isEmpty()) {
      setArg(args, "index.shardsByIndexName", database.getNumberOfShardsPerIndex());
    }

    setArg(
        args, "history.processInstanceEnabled", database.getHistory().isProcessInstanceEnabled());

    setArg(args, "history.retention.policyName", database.getHistory().getPolicyName());
    setArg(args, "history.elsRolloverDateFormat", database.getHistory().getElsRolloverDateFormat());
    setArg(args, "history.rolloverInterval", database.getHistory().getRolloverInterval());
    setArg(args, "history.rolloverBatchSize", database.getHistory().getRolloverBatchSize());
    setArg(
        args,
        "history.waitPeriodBeforeArchiving",
        database.getHistory().getWaitPeriodBeforeArchiving());
    setArg(
        args, "history.delayBetweenRuns", database.getHistory().getDelayBetweenRuns().toMillis());
    setArg(
        args,
        "history.maxDelayBetweenRuns",
        database.getHistory().getMaxDelayBetweenRuns().toMillis());

    setArg(args, "createSchema", database.isCreateSchema());

    if (database.getIncidentNotifier() != null) {
      setArg(args, "notifier.webhook", database.getIncidentNotifier().getWebhook());
      setArg(args, "notifier.auth0Domain", database.getIncidentNotifier().getAuth0Domain());
      setArg(args, "notifier.auth0Protocol", database.getIncidentNotifier().getAuth0Protocol());
      setArg(args, "notifier.m2mClientId", database.getIncidentNotifier().getM2mClientId());
      setArg(args, "notifier.m2mClientSecret", database.getIncidentNotifier().getM2mClientSecret());
      setArg(args, "notifier.m2mAudience", database.getIncidentNotifier().getM2mAudience());
    }

    setArg(
        args, "batchOperationCache.maxCacheSize", database.getBatchOperationCache().getMaxSize());
    setArg(args, "processCache.maxCacheSize", database.getProcessCache().getMaxSize());
    setArg(
        args,
        "decisionRequirementsCache.maxCacheSize",
        database.getDecisionRequirementsCache().getMaxSize());
    setArg(args, "formCache.maxCacheSize", database.getFormCache().getMaxSize());

    setArg(args, "postExport.batchSize", database.getPostExport().getBatchSize());
    // Duration supports only seconds and nanos; other units need to be converted
    setArg(
        args,
        "postExport.delayBetweenRuns",
        database.getPostExport().getDelayBetweenRuns().getSeconds() * 1000);
    setArg(
        args,
        "postExport.maxDelayBetweenRuns",
        database.getPostExport().getMaxDelayBetweenRuns().getSeconds() * 1000);
    setArg(args, "postExport.ignoreMissingData", database.getPostExport().isIgnoreMissingData());
    setArg(
        args,
        "batchOperation.exportItemsOnCreation",
        database.getBatchOperations().isExportItemsOnCreation());

    setArg(args, "bulk.delay", database.getBulk().getDelay().getSeconds());
    setArg(args, "bulk.size", database.getBulk().getSize());
    setArg(args, "bulk.memoryLimit", database.getBulk().getMemoryLimit().toMegabytes());

    final var auditLog = unifiedConfiguration.getCamunda().getData().getAuditLog();
    exporter.setArgs(
        ExporterConfiguration.of(io.camunda.exporter.config.ExporterConfiguration.class, args)
            .apply(config -> config.setAuditLog(auditLog.toConfiguration()))
            .toArgs());
  }

  private void populateRdbmsExporter(final BrokerBasedProperties override) {
    final SecondaryStorage secondaryStorage =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    final Rdbms database = secondaryStorage.getRdbms();

    /* Load exporter config map */

    var exporter = override.getRdbmsExporter();
    if (exporter == null) {
      exporter = new ExporterCfg();
      exporter.setClassName(RDBMS_EXPORTER_CLASS_NAME);
      exporter.setArgs(new LinkedHashMap<>());
      override.getExporters().put(RDBMS_EXPORTER_NAME, exporter);
    }

    /* Override config map values */

    // https://github.com/camunda/camunda/issues/37880
    // it is possible to have an exporter with no args defined
    final Map<String, Object> args =
        exporter.getArgs() == null ? new LinkedHashMap<>() : exporter.getArgs();
    setArgIfNotNull(args, "queueSize", database.getQueueSize());
    setArgIfNotNull(args, "queueMemoryLimit", database.getQueueMemoryLimit());
    setArgIfNotNull(args, "flushInterval", database.getFlushInterval());

    if (database.getHistory() != null) {
      final var history = database.getHistory();

      setArgIfNotNull(args, "history.defaultHistoryTTL", history.getDefaultHistoryTTL());
      setArgIfNotNull(
          args,
          "history.defaultBatchOperationHistoryTTL",
          history.getDefaultBatchOperationHistoryTTL());
      setArgIfNotNull(
          args,
          "history.batchOperationCancelProcessInstanceHistoryTTL",
          history.getBatchOperationCancelProcessInstanceHistoryTTL());
      setArgIfNotNull(
          args,
          "history.batchOperationMigrateProcessInstanceHistoryTTL",
          history.getBatchOperationMigrateProcessInstanceHistoryTTL());
      setArgIfNotNull(
          args,
          "history.batchOperationModifyProcessInstanceHistoryTTL",
          history.getBatchOperationModifyProcessInstanceHistoryTTL());
      setArgIfNotNull(
          args,
          "history.batchOperationResolveIncidentHistoryTTL",
          history.getBatchOperationResolveIncidentHistoryTTL());
      setArgIfNotNull(
          args, "history.minHistoryCleanupInterval", history.getMinHistoryCleanupInterval());
      setArgIfNotNull(
          args, "history.maxHistoryCleanupInterval", history.getMaxHistoryCleanupInterval());
      setArgIfNotNull(
          args, "history.historyCleanupBatchSize", history.getHistoryCleanupBatchSize());
      setArgIfNotNull(args, "history.usageMetricsCleanup", history.getUsageMetricsCleanup());
      setArgIfNotNull(args, "history.usageMetricsTTL", history.getUsageMetricsTTL());
    }

    if (database.getProcessCache() != null) {
      setArgIfNotNull(args, "processCache.maxSize", database.getProcessCache().getMaxSize());
    }

    if (database.getBatchOperationCache() != null) {
      setArgIfNotNull(
          args, "batchOperationCache.maxSize", database.getBatchOperationCache().getMaxSize());
    }

    setArgIfNotNull(
        args,
        "exportBatchOperationItemsOnCreation",
        database.isExportBatchOperationItemsOnCreation());
    setArgIfNotNull(
        args, "batchOperationItemInsertBlockSize", database.getBatchOperationItemInsertBlockSize());

    final var auditLog = unifiedConfiguration.getCamunda().getData().getAuditLog();
    exporter.setArgs(
        ExporterConfiguration.of(io.camunda.exporter.rdbms.ExporterConfiguration.class, args)
            .apply(config -> config.setAuditLog(auditLog.toConfiguration()))
            .toArgs());
  }

  private void populateFromMonitoring(final BrokerBasedProperties override) {
    populateFromMetrics(override);
  }

  private void populateFromMetrics(final BrokerBasedProperties override) {
    final Metrics metrics = unifiedConfiguration.getCamunda().getMonitoring().getMetrics();
    override.getExperimental().getFeatures().setEnableActorMetrics(metrics.isActor());
    override.setExecutionMetricsExporterEnabled(metrics.isEnableExporterExecutionMetrics());
  }

  private void setArgIfNotNull(
      final Map<String, Object> args, final String breadcrumb, final Object value) {
    if (value != null) {
      setArg(args, breadcrumb, value);
    }
  }

  private void populateInterceptorPlugins(
      final DocumentBasedSecondaryStorageDatabase database, final Map<String, Object> args) {

    final Map<Object, String> connectMap = (Map<Object, String>) args.get("connect");
    if (connectMap != null && connectMap.containsKey("interceptorPlugins")) {
      final String warningMessage =
          String.format(
              "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
              "camunda.data.secondary-storage." + database.databaseName() + ".interceptor-plugins",
              "zeebe.broker.exporters.camundaexporter.args.connect.interceptorPlugins");
      LOGGER.warn(warningMessage);
    }

    for (int i = 0; i < database.getInterceptorPlugins().size(); i++) {
      final InterceptorPlugin interceptorPlugin = database.getInterceptorPlugins().get(i);
      setArg(
          args,
          String.format(CONNECT_INTERCEPTOR_PLUGINS_BREADCRUMB + ".%s.id", i),
          interceptorPlugin.getId());
      setArg(
          args,
          String.format(CONNECT_INTERCEPTOR_PLUGINS_BREADCRUMB + ".%s.className", i),
          interceptorPlugin.getClassName());
      setArg(
          args,
          String.format(CONNECT_INTERCEPTOR_PLUGINS_BREADCRUMB + ".%s.jarPath", i),
          interceptorPlugin.getJarPath());
    }
  }

  @SuppressWarnings("unchecked")
  private void setArg(final Map<String, Object> args, final String breadcrumb, final Object value) {
    final String[] keys = breadcrumb.split("\\.");
    Map<String, Object> cursor = args;
    for (int i = 0; i < keys.length - 1; i++) {
      cursor = (Map<String, Object>) cursor.computeIfAbsent(keys[i], k -> new LinkedHashMap<>());
    }
    cursor.put(keys[keys.length - 1], value);
  }

  private void populateFromExporters(final BrokerBasedProperties override) {
    final Map<String, Exporter> exporters =
        unifiedConfiguration.getCamunda().getData().getExporters();

    // Log common legacy exporters warning instead of using UnifiedConfigurationHelper logging.
    if (!override.getExporters().isEmpty()) {
      final String warningMessage =
          String.format(
              "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
              "camunda.data.exporters", "zeebe.broker.exporters");
      LOGGER.warn(warningMessage);
    }

    exporters.forEach(
        (name, exporter) -> override.getExporters().put(name, exporter.toExporterCfg()));
  }

  private void populateFromGlobalListeners(final BrokerBasedProperties override) {
    override
        .getExperimental()
        .getEngine()
        .setGlobalListeners(unifiedConfiguration.getCamunda().getCluster().getGlobalListeners());
  }
}

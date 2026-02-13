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
import io.camunda.configuration.Export;
import io.camunda.configuration.Exporter;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.Filter;
import io.camunda.configuration.FixedPartition;
import io.camunda.configuration.Gcs;
import io.camunda.configuration.Interceptor;
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

  public static final String RDBMS_EXPORTER_NAME = "rdbms";
  private static final Logger LOGGER = LoggerFactory.getLogger(BrokerBasedPropertiesOverride.class);
  private static final String CAMUNDA_EXPORTER_CLASS_NAME = "io.camunda.exporter.CamundaExporter";
  private static final String CAMUNDA_EXPORTER_NAME = "camundaexporter";
  private static final String RDBMS_EXPORTER_CLASS_NAME = "io.camunda.exporter.rdbms.RdbmsExporter";
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

    populateFromCluster(override);

    populateFromLongPolling(override);

    populateFromRestFilters(override);

    populateFromSystem(override);

    populateFromPrimaryStorage(override);

    populateFromGrpc(override);

    populateFromData(override);

    if (unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getType()
        == SecondaryStorageType.rdbms) {
      populateRdbmsExporter(override);
    } else {
      populateCamundaExporter(override);
    }

    populateFromExporters(override);

    populateFromMonitoring(override);

    populateFromProcessing(override);

    populateFromFlowControl(override);

    populateFromSecurity(override);

    return override;
  }

  private void populateFromSecurity(final BrokerBasedProperties override) {
    final var tlsCluster =
        unifiedConfiguration
            .getCamunda()
            .getSecurity()
            .getTransportLayerSecurity()
            .getCluster()
            .withBrokerTlsClusterProperties();

    final var networkSecurity = override.getNetwork().getSecurity();
    networkSecurity.setEnabled(tlsCluster.isEnabled());
    networkSecurity.setCertificateChainPath(tlsCluster.getCertificateChainPath());
    networkSecurity.setPrivateKeyPath(tlsCluster.getCertificatePrivateKeyPath());
    networkSecurity
        .getKeyStore()
        .setFilePath(
            tlsCluster.getKeyStore().withBrokerTlsClusterKeyStoreProperties().getFilePath());
    networkSecurity
        .getKeyStore()
        .setPassword(
            tlsCluster.getKeyStore().withBrokerTlsClusterKeyStoreProperties().getPassword());
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
    populateFromExpression(override);
    populateFromProcessInstanceCreation(override);
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
    final var batchOperationsCfg = override.getExperimental().getEngine().getBatchOperations();
    batchOperationsCfg.setSchedulerInterval(engineBatchOperation.getSchedulerInterval());
    batchOperationsCfg.setChunkSize(engineBatchOperation.getChunkSize());
    batchOperationsCfg.setQueryPageSize(engineBatchOperation.getQueryPageSize());
    batchOperationsCfg.setQueryInClauseSize(engineBatchOperation.getQueryInClauseSize());
    batchOperationsCfg.setQueryRetryMax(engineBatchOperation.getQueryRetryMax());
    batchOperationsCfg.setQueryRetryInitialDelay(engineBatchOperation.getQueryRetryInitialDelay());
    batchOperationsCfg.setQueryRetryMaxDelay(engineBatchOperation.getQueryRetryMaxDelay());
    batchOperationsCfg.setQueryRetryBackoffFactor(
        engineBatchOperation.getQueryRetryBackoffFactor());
  }

  private void populateFromExpression(final BrokerBasedProperties override) {
    final var expression = unifiedConfiguration.getCamunda().getExpression();
    override.getExperimental().getEngine().getExpression().setTimeout(expression.getTimeout());
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

    override.getExperimental().setSendOnLegacySubject(cluster.isSendOnLegacySubject());
    override.getExperimental().setReceiveOnLegacySubject(cluster.isReceiveOnLegacySubject());
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
    brokerRocksDb.setMemoryFraction(unifiedRocksDb.getMemoryFraction());
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

    validateSchedulerConfiguration(primaryStorageBackup);

    final BackupCfg backupCfg = override.getData().getBackup();
    backupCfg.setRequired(primaryStorageBackup.isRequired());
    backupCfg.setContinuous(primaryStorageBackup.isContinuous());
    backupCfg.setSchedule(primaryStorageBackup.getSchedule());
    backupCfg.setCheckpointInterval(primaryStorageBackup.getCheckpointInterval());
    backupCfg.setOffset(primaryStorageBackup.getOffset());
    backupCfg.setRetention(primaryStorageBackup.getRetention());
  }

  private void validateSchedulerConfiguration(final PrimaryStorageBackup primaryStorageBackup) {
    final var dbType = unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getType();

    final var continuousBackupsEnabledOnDocumentBasedStore =
        (dbType.isElasticSearch() || dbType.isOpenSearch())
            && (primaryStorageBackup.isContinuous() || hasScheduleConfig(primaryStorageBackup));

    if (continuousBackupsEnabledOnDocumentBasedStore) {
      throw new IllegalArgumentException(
          "Continuous backups are not compatible with secondary storage: `%s`. Please disable continuous backups."
              .formatted(dbType));
    }
  }

  private boolean hasScheduleConfig(final PrimaryStorageBackup primaryStorageBackup) {
    return primaryStorageBackup.getSchedule() != null
        && !primaryStorageBackup.getSchedule().isBlank()
        && !primaryStorageBackup.getSchedule().equalsIgnoreCase("none");
  }

  private void populateCamundaExporter(final BrokerBasedProperties override) {
    final Data data = unifiedConfiguration.getCamunda().getData();
    final SecondaryStorage secondaryStorage = data.getSecondaryStorage();

    if (!secondaryStorage.getAutoconfigureCamundaExporter()) {
      LOGGER.debug("Skipping autoconfiguration of the (default) exporter 'camundaexporter'");
      return;
    }

    if (secondaryStorage.getDocumentBasedDatabase() == null) {
      LOGGER.debug(
          "Skipping Camunda exporter configuration because database type {} is not document based",
          secondaryStorage.getType());
      return;
    }

    /* Load exporter config map */
    ExporterCfg exporter = override.getCamundaExporter();
    if (exporter == null) {
      exporter = new ExporterCfg();
      exporter.setClassName(CAMUNDA_EXPORTER_CLASS_NAME);
      exporter.setArgs(new LinkedHashMap<>());
      override.getExporters().put(CAMUNDA_EXPORTER_NAME, exporter);
    }

    // https://github.com/camunda/camunda/issues/37880
    // it is possible to have an exporter with no args defined
    Map<String, Object> args = exporter.getArgs();
    if (args == null) {
      args = new LinkedHashMap<>();
      exporter.setArgs(args);
    }

    exporter.setArgs(
        ExporterConfiguration.of(io.camunda.exporter.config.ExporterConfiguration.class, args)
            .apply(
                config -> {
                  CamundaExporterConfigurationApplier.applyRetention(config, unifiedConfiguration);
                  CamundaExporterConfigurationApplier.applyConnect(config, unifiedConfiguration);
                  CamundaExporterConfigurationApplier.applyIndex(config, unifiedConfiguration);
                  CamundaExporterConfigurationApplier.applyHistory(config, unifiedConfiguration);
                  CamundaExporterConfigurationApplier.applyPostExportConfiguration(
                      config, unifiedConfiguration);
                  CamundaExporterConfigurationApplier.applyBulk(config, unifiedConfiguration);
                  CamundaExporterConfigurationApplier.applyIncidentNotifier(
                      config, unifiedConfiguration);
                  CamundaExporterConfigurationApplier.applyMisc(config, unifiedConfiguration);
                })
            .toArgs());
  }

  private void populateRdbmsExporter(final BrokerBasedProperties override) {
    final Data data = unifiedConfiguration.getCamunda().getData();
    final SecondaryStorage secondaryStorage = data.getSecondaryStorage();

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

    exporter.setArgs(
        ExporterConfiguration.of(io.camunda.exporter.rdbms.ExporterConfiguration.class, args)
            .apply(
                config -> {
                  config.setQueueSize(database.getQueueSize());
                  config.setQueueMemoryLimit(database.getQueueMemoryLimit());
                  config.setFlushInterval(database.getFlushInterval());
                  config.setExportBatchOperationItemsOnCreation(
                      database.isExportBatchOperationItemsOnCreation());
                  config.setBatchOperationItemInsertBlockSize(
                      database.getBatchOperationItemInsertBlockSize());
                  config.setAuditLog(
                      unifiedConfiguration.getCamunda().getData().getAuditLog().toConfiguration());
                  config.setHistoryDeletion(
                      unifiedConfiguration
                          .getCamunda()
                          .getData()
                          .getHistoryDeletion()
                          .toConfiguration());

                  applyRdbmsHistoryExporterConfiguration(config.getHistory(), database);

                  if (database.getProcessCache() != null) {
                    config.getProcessCache().setMaxSize(database.getProcessCache().getMaxSize());
                  }

                  if (database.getBatchOperationCache() != null) {
                    config
                        .getBatchOperationCache()
                        .setMaxSize(database.getBatchOperationCache().getMaxSize());
                  }

                  if (database.getInsertBatching() != null) {
                    config
                        .getInsertBatching()
                        .setMaxAuditLogInsertBatchSize(
                            database.getInsertBatching().getMaxAuditLogInsertBatchSize());
                    config
                        .getInsertBatching()
                        .setMaxVariableInsertBatchSize(
                            database.getInsertBatching().getMaxVariableInsertBatchSize());
                    config
                        .getInsertBatching()
                        .setMaxJobInsertBatchSize(
                            database.getInsertBatching().getMaxJobInsertBatchSize());
                    config
                        .getInsertBatching()
                        .setMaxFlowNodeInsertBatchSize(
                            database.getInsertBatching().getMaxFlowNodeInsertBatchSize());
                  }
                })
            .toArgs());
  }

  private void applyRdbmsHistoryExporterConfiguration(
      final io.camunda.exporter.rdbms.ExporterConfiguration.HistoryConfiguration history,
      final Rdbms database) {
    if (database.getHistory() == null) {
      return;
    }

    history.setDefaultHistoryTTL(database.getHistory().getDefaultHistoryTTL());
    history.setDefaultBatchOperationHistoryTTL(
        database.getHistory().getDefaultBatchOperationHistoryTTL());
    history.setBatchOperationCancelProcessInstanceHistoryTTL(
        database.getHistory().getBatchOperationCancelProcessInstanceHistoryTTL());
    history.setBatchOperationMigrateProcessInstanceHistoryTTL(
        database.getHistory().getBatchOperationMigrateProcessInstanceHistoryTTL());
    history.setBatchOperationModifyProcessInstanceHistoryTTL(
        database.getHistory().getBatchOperationModifyProcessInstanceHistoryTTL());
    history.setBatchOperationResolveIncidentHistoryTTL(
        database.getHistory().getBatchOperationResolveIncidentHistoryTTL());
    history.setMinHistoryCleanupInterval(database.getHistory().getMinHistoryCleanupInterval());
    history.setMaxHistoryCleanupInterval(database.getHistory().getMaxHistoryCleanupInterval());
    history.setHistoryCleanupBatchSize(database.getHistory().getHistoryCleanupBatchSize());
    history.setHistoryCleanupProcessInstanceBatchSize(
        database.getHistory().getHistoryCleanupProcessInstanceBatchSize());
    history.setUsageMetricsCleanup(database.getHistory().getUsageMetricsCleanup());
    history.setUsageMetricsTTL(database.getHistory().getUsageMetricsTTL());
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

  private void populateFromProcessInstanceCreation(final BrokerBasedProperties override) {
    override
        .getExperimental()
        .getEngine()
        .getProcessInstanceCreation()
        .setBusinessIdUniquenessEnabled(
            unifiedConfiguration
                .getCamunda()
                .getProcessInstanceCreation()
                .isBusinessIdUniquenessEnabled());
  }
}

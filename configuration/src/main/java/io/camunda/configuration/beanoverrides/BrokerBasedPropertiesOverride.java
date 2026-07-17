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
import io.camunda.configuration.Camunda;
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
import io.camunda.configuration.Zone;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.LegacyBrokerBasedProperties;
import io.camunda.zeebe.backup.azure.SasTokenConfig;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.system.configuration.ConfigManagerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.broker.system.configuration.ExportingCfg;
import io.camunda.zeebe.broker.system.configuration.MembershipCfg;
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
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneAwareCfg;
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
  // also referenced by the per-physical-tenant exporter resolution, which must treat the
  // autoconfigured exporter ids as outside the generic-exporter catalog (ADR-0008 §1)
  public static final String CAMUNDA_EXPORTER_NAME = "camundaexporter";
  private static final Logger LOGGER = LoggerFactory.getLogger(BrokerBasedPropertiesOverride.class);
  private static final String CAMUNDA_EXPORTER_CLASS_NAME = "io.camunda.exporter.CamundaExporter";
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

    populateFromCamunda(override, unifiedConfiguration.getCamunda());

    return override;
  }

  /**
   * Builds a per-physical-tenant {@link BrokerBasedProperties} entirely from the supplied
   * per-tenant {@link Camunda} object, using the same population logic as the root {@link
   * #brokerBasedProperties()} bean.
   *
   * <p>The per-tenant {@code Camunda} is produced by {@code PhysicalTenantResolver} via a two-step
   * bind: it inherits root values for every un-overridden key, so cluster-wide sections come out
   * identical to the root while per-tenant overrides apply on top.
   *
   * <p>Unlike the root bean, the legacy {@code zeebe.broker.*} properties that do not have a
   * corresponding unified configuration property are not inherited. The default tenant keeps legacy
   * support by reusing the root bean directly instead of this method.
   *
   * <p>The caller must stamp {@code cluster.nodeId} / {@code cluster.nodeVersion} (from {@code
   * NodeIdProvider}) and call {@code init()}, just as {@code BrokerBasedConfiguration} does for the
   * root bean.
   */
  public static BrokerBasedProperties convert(final Camunda perTenant) {
    final BrokerBasedProperties props = new BrokerBasedProperties();
    populateFromCamunda(props, perTenant);
    return props;
  }

  /**
   * Populates the given {@link BrokerBasedProperties} from a {@link Camunda} object. Single code
   * path shared by the root bean and the per-tenant build so the two cannot drift.
   */
  private static void populateFromCamunda(
      final BrokerBasedProperties override, final Camunda camunda) {
    populateFromCluster(override, camunda);

    populateFromLongPolling(override, camunda);

    populateFromRestFilters(override, camunda);

    populateFromSystem(override, camunda);

    populateFromPrimaryStorage(override, camunda);

    populateFromGrpc(override, camunda);

    populateFromData(override, camunda);

    if (camunda.getData().getSecondaryStorage().getType() == SecondaryStorageType.rdbms) {
      populateRdbmsExporter(override, camunda);
    } else {
      populateCamundaExporter(override, camunda);
    }

    populateFromExporters(override, camunda);

    populateFromMonitoring(override, camunda);

    populateFromProcessing(override, camunda);

    populateFromFlowControl(override, camunda);

    populateFromSecurity(override, camunda);

    override.setLicenseKey(camunda.getLicense().getKey());
  }

  private static void populateFromSecurity(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var tlsCluster =
        camunda
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

  private static void populateFromProcessing(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Processing processing = camunda.getProcessing();

    // processing
    override.getProcessing().setMaxCommandsInBatch(processing.getMaxCommandsInBatch());
    override.getProcessing().setMaxRecoverableRetries(processing.getMaxRecoverableRetries());
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

    populateFromEngine(override, camunda);
  }

  private static void populateFromEngine(
      final BrokerBasedProperties override, final Camunda camunda) {
    populateFromDistribution(override, camunda);
    populateFromBatchOperations(override, camunda);
    populateFromExpression(override, camunda);
    populateFromProcessInstanceCreation(override, camunda);
    populateFromJobs(override, camunda);
    populateFromLoopDetection(override, camunda);
  }

  private static void populateFromDistribution(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var distribution = camunda.getProcessing().getEngine().getDistribution();

    final var distributionCfg = override.getExperimental().getEngine().getDistribution();
    distributionCfg.setMaxBackoffDuration(distribution.getMaxBackoffDuration());
    distributionCfg.setRedistributionInterval(distribution.getRedistributionInterval());
  }

  private static void populateFromBatchOperations(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var engineBatchOperation = camunda.getProcessing().getEngine().getBatchOperations();
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

  private static void populateFromLoopDetection(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var loopDetection = camunda.getProcessing().getEngine().getLoopDetection();
    final var loopDetectionCfg = override.getExperimental().getEngine().getLoopDetection();
    loopDetectionCfg.setMaxElementActivationCount(loopDetection.getMaxElementActivationCount());
    loopDetectionCfg.setElementActivationRetryCooldown(
        loopDetection.getElementActivationRetryCooldown());
    loopDetectionCfg.setMaxElementActivationCountByType(
        loopDetection.getMaxElementActivationCountByType());
  }

  private static void populateFromExpression(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var expression = camunda.getExpression();
    override.getExperimental().getEngine().getExpression().setTimeout(expression.getTimeout());
  }

  private static void populateFromFlowControl(
      final BrokerBasedProperties override, final Camunda camunda) {
    populateFromBackpressureLimitRequest(override, camunda);
    populateFromWrite(override, camunda);
  }

  private static void populateFromBackpressureLimitRequest(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Limit request = camunda.getProcessing().getFlowControl().getRequest();

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

  private static void populateFromWrite(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Write write = camunda.getProcessing().getFlowControl().getWrite();

    if (write == null) {
      return;
    }

    final RateLimitCfg rateLimitCfg =
        Optional.ofNullable(override.getFlowControl().getWrite()).orElse(new RateLimitCfg());
    rateLimitCfg.setEnabled(write.isEnabled());
    rateLimitCfg.setLimit(write.getLimit());
    rateLimitCfg.setRampUp(write.getRampUp());
    override.getFlowControl().setWrite(rateLimitCfg);

    populateFromThrottle(override, camunda);
  }

  private static void populateFromThrottle(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Throttle throttle = camunda.getProcessing().getFlowControl().getWrite().getThrottle();

    final ThrottleCfg throttleCfg = override.getFlowControl().getWrite().getThrottling();
    throttleCfg.setEnabled(throttle.isEnabled());
    throttleCfg.setAcceptableBacklog(throttle.getAcceptableBacklog());
    throttleCfg.setMinimumLimit(throttle.getMinimumLimit());
    throttleCfg.setResolution(throttle.getResolution());
  }

  private static void populateFromGrpc(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var grpc = camunda.getApi().getGrpc().withBrokerNetworkProperties();

    final NetworkCfg networkCfg = override.getGateway().getNetwork();
    networkCfg.setHost(grpc.getAddress());
    networkCfg.setPort(grpc.getPort());
    networkCfg.setMinKeepAliveInterval(grpc.getMinKeepAliveInterval());

    populateFromSsl(override, camunda);
    populateFromInterceptors(override, camunda);

    final io.camunda.zeebe.gateway.impl.configuration.ThreadsCfg threadsCfg =
        override.getGateway().getThreads();
    threadsCfg.setManagementThreads(grpc.getManagementThreads());
  }

  private static void populateFromSsl(final BrokerBasedProperties override, final Camunda camunda) {
    final Ssl ssl = camunda.getApi().getGrpc().getSsl().withBrokerSslProperties();
    final SecurityCfg securityCfg = override.getGateway().getSecurity();
    securityCfg.setEnabled(ssl.isEnabled());
    securityCfg.setCertificateChainPath(ssl.getCertificate());
    securityCfg.setPrivateKeyPath(ssl.getCertificatePrivateKey());

    populateFromKeyStore(override, camunda);
  }

  private static void populateFromKeyStore(
      final BrokerBasedProperties override, final Camunda camunda) {
    final KeyStore keyStore =
        camunda.getApi().getGrpc().getSsl().getKeyStore().withBrokerKeyStoreProperties();
    final KeyStoreCfg keyStoreCfg = override.getGateway().getSecurity().getKeyStore();
    keyStoreCfg.setFilePath(keyStore.getFilePath());
    keyStoreCfg.setPassword(keyStore.getPassword());
  }

  private static void populateFromInterceptors(
      final BrokerBasedProperties override, final Camunda camunda) {
    // Order between legacy and new interceptor props is not guaranteed.
    // Log common interceptors warning instead of using UnifiedConfigurationHelper logging.
    if (!override.getGateway().getInterceptors().isEmpty()) {
      final String warningMessage =
          String.format(
              "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
              "camunda.api.grpc.interceptors", "zeebe.broker.gateway.interceptors");
      LOGGER.warn(warningMessage);
    }

    final List<Interceptor> interceptors = camunda.getApi().getGrpc().getInterceptors();
    if (!interceptors.isEmpty()) {
      final List<InterceptorCfg> interceptorCfgList =
          interceptors.stream().map(Interceptor::toInterceptorCfg).toList();
      override.getGateway().setInterceptors(interceptorCfgList);
    }
  }

  private static void populateFromCluster(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var cluster = camunda.getCluster().withBrokerProperties();

    override.getCluster().setInitialContactPoints(cluster.getInitialContactPoints());
    if (cluster.getNodeIdProvider().getType() == Type.FIXED) {
      override.getCluster().setNodeId(cluster.getNodeId());
    }
    override.getCluster().setPartitionsCount(cluster.getPartitionCount());
    override.getCluster().setReplicationFactor(cluster.getReplicationFactor());
    override.getCluster().setClusterSize(cluster.getSize());
    override.getCluster().setClusterName(cluster.getName());
    override.getCluster().setClusterId(cluster.getClusterId());
    override.getCluster().setZone(cluster.getZone());

    populateFromMembership(override, camunda);
    populateFromRaftProperties(override, camunda);
    populateFromClusterMetadata(override, camunda);
    populateFromClusterNetwork(override, camunda);

    override
        .getCluster()
        .setMessageCompression(
            CompressionAlgorithm.valueOf(cluster.getCompressionAlgorithm().name()));

    populateFromGlobalListeners(override, camunda);

    populateFromPartitioning(override, camunda);

    override.getExperimental().setReceiveOnLegacySubject(cluster.isReceiveOnLegacySubject());
  }

  @SuppressWarnings("MissingSwitchDefault")
  private static void populateFromPartitioning(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Partitioning partitioning = camunda.getCluster().getPartitioning();

    // Order between legacy and new partitioning props is not guaranteed.
    // Log common partitioning warning instead of using UnifiedConfigurationHelper logging.
    final var partitioningCfg = override.getExperimental().getPartitioning();
    switch (partitioning.getScheme()) {
      case FIXED -> {
        if (!partitioningCfg.getFixed().isEmpty()) {
          final String warningMessage =
              String.format(
                  "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
                  "camunda.cluster.partitioning.fixed",
                  "zeebe.broker.experimental.partitioning.fixed");
          LOGGER.warn(warningMessage);
        }
        if (!partitioning.getFixed().isEmpty()) {
          partitioningCfg.setScheme(Scheme.FIXED);
          partitioningCfg.setFixed(
              partitioning.getFixed().stream().map(FixedPartition::toFixedPartitionCfg).toList());
        }
      }
      case ZONE_AWARE -> {
        partitioningCfg.setScheme(Scheme.ZONE_AWARE);
        partitioningCfg.setZoneAware(
            new ZoneAwareCfg(
                partitioning.getZoneAware().zones().stream().map(Zone::toZoneCfg).toList()));
      }
      case ROUND_ROBIN -> partitioningCfg.setScheme(Scheme.ROUND_ROBIN);
    }
  }

  private static void populateFromLongPolling(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var longPolling = camunda.getApi().getLongPolling().withBrokerLongPollingProperties();
    final var longPollingCfg = override.getGateway().getLongPolling();
    longPollingCfg.setEnabled(longPolling.isEnabled());
    longPollingCfg.setTimeout(longPolling.getTimeout());
    longPollingCfg.setProbeTimeout(longPolling.getProbeTimeout());
    longPollingCfg.setMinEmptyResponses(longPolling.getMinEmptyResponses());
  }

  private static void populateFromMembership(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Membership membership =
        camunda.getCluster().getMembership().withBrokerMembershipProperties();
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

  private static void populateFromRaftProperties(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var raft = camunda.getCluster().getRaft();
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

  private static void populateFromClusterMetadata(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var metadata = camunda.getCluster().getMetadata();
    final var syncDelay = metadata.getSyncDelay();
    final var syncTimeout = metadata.getSyncRequestTimeout();
    final var gossipFanout = metadata.getGossipFanout();
    final var syncInitializerDelay = metadata.getSyncInitializerDelay();
    final var configManagerGossipConfig =
        new ClusterConfigurationGossiperConfig(
            syncDelay, syncTimeout, gossipFanout, syncInitializerDelay);
    override.getCluster().setConfigManager(new ConfigManagerCfg(configManagerGossipConfig));
  }

  private static void populateFromClusterNetwork(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var network = camunda.getCluster().getNetwork().withBrokerNetworkProperties();

    final var brokerNetwork = override.getNetwork();
    brokerNetwork.setHost(network.getHost());
    brokerNetwork.setAdvertisedHost(network.getAdvertisedHost());
    brokerNetwork.setPortOffset(network.getPortOffset());
    brokerNetwork.setMaxMessageSize(network.getMaxMessageSize());
    brokerNetwork.setSocketSendBuffer(network.getSocketSendBuffer());
    brokerNetwork.setSocketReceiveBuffer(network.getSocketReceiveBuffer());
    brokerNetwork.setHeartbeatTimeout(network.getHeartbeatTimeout());
    brokerNetwork.setHeartbeatInterval(network.getHeartbeatInterval());

    override.getGateway().getNetwork().setMaxMessageSize(network.getMaxMessageSize());

    populateFromCommandApi(override, camunda);
    populateFromInternalApi(override, camunda);
  }

  private static void populateFromInternalApi(
      final BrokerBasedProperties override, final Camunda camunda) {
    final InternalApi internalApi =
        camunda.getCluster().getNetwork().getInternalApi().withBrokerInternalApiProperties();

    final SocketBindingCfg socketBindingCfg = override.getNetwork().getInternalApi();

    socketBindingCfg.setHost(internalApi.getHost());
    socketBindingCfg.setPort(internalApi.getPort());
    socketBindingCfg.setAdvertisedHost(internalApi.getAdvertisedHost());
    Optional.ofNullable(internalApi.getAdvertisedPort())
        .ifPresent(socketBindingCfg::setAdvertisedPort);
  }

  private static void populateFromCommandApi(
      final BrokerBasedProperties override, final Camunda camunda) {
    final CommandApi commandApi = camunda.getCluster().getNetwork().getCommandApi();
    final CommandApiCfg commandApiCfg = override.getNetwork().getCommandApi();

    commandApiCfg.setHost(commandApi.getHost());
    Optional.ofNullable(commandApi.getPort()).ifPresent(commandApiCfg::setPort);
    commandApiCfg.setAdvertisedHost(commandApi.getAdvertisedHost());
    Optional.ofNullable(commandApi.getAdvertisedPort()).ifPresent(commandApiCfg::setAdvertisedPort);
  }

  private static void populateFromRestFilters(
      final BrokerBasedProperties override, final Camunda camunda) {
    // Order between legacy and new filters props is not guaranteed.
    // Log common filters warning instead of using UnifiedConfigurationHelper logging.
    if (!override.getGateway().getFilters().isEmpty()) {
      final String warningMessage =
          String.format(
              "The following legacy property is no longer supported and should be removed in favor of '%s': %s",
              "camunda.api.rest.filters", "zeebe.broker.gateway.filters");
      LOGGER.warn(warningMessage);
    }

    final List<Filter> filters = camunda.getApi().getRest().getFilters();
    if (!filters.isEmpty()) {
      final List<FilterCfg> filterCfgList = filters.stream().map(Filter::toFilterCfg).toList();
      override.getGateway().setFilters(filterCfgList);
    }
  }

  private static void populateFromSystem(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var system = camunda.getSystem();

    final var threadsCfg = new ThreadsCfg();
    threadsCfg.setCpuThreadCount(system.getCpuThreadCount());
    threadsCfg.setIoThreadCount(system.getIoThreadCount());
    override.setThreads(threadsCfg);

    final var enableVersionCheck = system.getUpgrade().getEnableVersionCheck();
    override.getExperimental().setVersionCheckRestrictionEnabled(enableVersionCheck);
  }

  private static void populateFromData(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Data data = camunda.getData();
    override.getData().setSnapshotPeriod(data.getSnapshotPeriod());

    populateFromExport(override, camunda);
    populateFromBackup(override, camunda);
  }

  private static void populateFromExport(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Export export = camunda.getData().getExport();
    final var exportingCfg =
        new ExportingCfg(export.getSkipRecords(), export.getDistributionInterval());
    override.setExporting(exportingCfg);
  }

  private static void populateFromBackup(
      final BrokerBasedProperties override, final Camunda camunda) {
    final PrimaryStorageBackup primaryStorageBackup =
        camunda.getData().getPrimaryStorage().getBackup();
    final BackupCfg backupCfg = override.getData().getBackup();
    backupCfg.setStore(BackupStoreType.valueOf(primaryStorageBackup.getStore().name()));

    populateFromS3(override, camunda);
    populateFromGcs(override, camunda);
    populateFromAzure(override, camunda);
    populateFromFilesystem(override, camunda);

    override.getData().setBackup(backupCfg);
  }

  private static void populateFromPrimaryStorage(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var primaryStorage = camunda.getData().getPrimaryStorage();
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

    populateBackupScheduler(override, primaryStorage.getBackup(), camunda);
  }

  private static void populateFromRocksDb(
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

  private static void populateFromS3(final BrokerBasedProperties override, final Camunda camunda) {
    final S3 s3 = camunda.getData().getPrimaryStorage().getBackup().getS3();
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
    s3BackupStoreConfig.setSsecKey(s3.getSsecKey());

    override.getData().getBackup().setS3(s3BackupStoreConfig);
  }

  private static void populateFromGcs(final BrokerBasedProperties override, final Camunda camunda) {
    final Gcs gcs = camunda.getData().getPrimaryStorage().getBackup().getGcs();
    final GcsBackupStoreConfig gcsBackupStoreConfig = override.getData().getBackup().getGcs();
    gcsBackupStoreConfig.setBucketName(gcs.getBucketName());
    gcsBackupStoreConfig.setBasePath(gcs.getBasePath());
    gcsBackupStoreConfig.setHost(gcs.getHost());
    gcsBackupStoreConfig.setAuth(GcsBackupStoreAuth.valueOf(gcs.getAuth().name()));
    gcsBackupStoreConfig.setMaxConcurrentTransfers(gcs.getMaxConcurrentTransfers());
    gcsBackupStoreConfig.setBufferSize(gcs.getBufferSize());

    override.getData().getBackup().setGcs(gcsBackupStoreConfig);
  }

  private static void populateFromAzure(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Azure azure = camunda.getData().getPrimaryStorage().getBackup().getAzure();
    final AzureBackupStoreConfig azureBackupStoreConfig = override.getData().getBackup().getAzure();
    azureBackupStoreConfig.setEndpoint(azure.getEndpoint());
    azureBackupStoreConfig.setAccountName(azure.getAccountName());
    azureBackupStoreConfig.setAccountKey(azure.getAccountKey());
    azureBackupStoreConfig.setConnectionString(azure.getConnectionString());
    azureBackupStoreConfig.setBasePath(azure.getBasePath());
    azureBackupStoreConfig.setCreateContainer(azure.isCreateContainer());
    populateFromSasToken(override, camunda);

    override.getData().getBackup().setAzure(azureBackupStoreConfig);
  }

  private static void populateFromSasToken(
      final BrokerBasedProperties override, final Camunda camunda) {
    final SasToken sasToken =
        camunda.getData().getPrimaryStorage().getBackup().getAzure().getSasToken();
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

  private static void populateFromFilesystem(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Filesystem filesystem = camunda.getData().getPrimaryStorage().getBackup().getFilesystem();
    final FilesystemBackupStoreConfig filesystemBackupStoreConfig =
        override.getData().getBackup().getFilesystem();
    filesystemBackupStoreConfig.setBasePath(filesystem.getBasePath());

    override.getData().getBackup().setFilesystem(filesystemBackupStoreConfig);
  }

  private static void populateBackupScheduler(
      final BrokerBasedProperties override,
      final PrimaryStorageBackup primaryStorageBackup,
      final Camunda camunda) {
    validateSchedulerConfiguration(primaryStorageBackup, camunda);

    final BackupCfg backupCfg = override.getData().getBackup();
    backupCfg.setRequired(primaryStorageBackup.isRequired());
    backupCfg.setContinuous(primaryStorageBackup.isContinuous());
    backupCfg.setSchedule(primaryStorageBackup.getSchedule());
    backupCfg.setCheckpointInterval(primaryStorageBackup.getCheckpointInterval());
    backupCfg.setOffset(primaryStorageBackup.getOffset());
    backupCfg.setRetention(primaryStorageBackup.getRetention());
  }

  private static void validateSchedulerConfiguration(
      final PrimaryStorageBackup primaryStorageBackup, final Camunda camunda) {
    final var dbType = camunda.getData().getSecondaryStorage().getType();

    final var continuousBackupsEnabledOnDocumentBasedStore =
        (dbType.isElasticSearch() || dbType.isOpenSearch())
            && (primaryStorageBackup.isContinuous() || hasScheduleConfig(primaryStorageBackup));

    if (continuousBackupsEnabledOnDocumentBasedStore) {
      throw new IllegalArgumentException(
          "Continuous backups are not compatible with secondary storage: `%s`. Please disable continuous backups."
              .formatted(dbType));
    }
  }

  private static boolean hasScheduleConfig(final PrimaryStorageBackup primaryStorageBackup) {
    return primaryStorageBackup.getSchedule() != null
        && !primaryStorageBackup.getSchedule().isBlank()
        && !primaryStorageBackup.getSchedule().equalsIgnoreCase("none");
  }

  private static void populateCamundaExporter(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Data data = camunda.getData();
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
                  CamundaExporterConfigurationApplier.applyRetention(config, camunda);
                  CamundaExporterConfigurationApplier.applyConnect(config, camunda);
                  CamundaExporterConfigurationApplier.applyIndex(config, camunda);
                  CamundaExporterConfigurationApplier.applyHistory(config, camunda);
                  CamundaExporterConfigurationApplier.applyPostExportConfiguration(config, camunda);
                  CamundaExporterConfigurationApplier.applyBulk(config, camunda);
                  CamundaExporterConfigurationApplier.applyIncidentNotifier(config, camunda);
                  CamundaExporterConfigurationApplier.applyMisc(config, camunda);
                  CamundaExporterConfigurationApplier.applyExtensionProperties(config, camunda);
                })
            .toArgs());
  }

  private static void populateRdbmsExporter(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Data data = camunda.getData();
    final SecondaryStorage secondaryStorage = data.getSecondaryStorage();

    final Rdbms database = secondaryStorage.getRdbms();

    /* Limit */
    override
        .getExperimental()
        .getEngine()
        .getValidators()
        .setMaxIdFieldLength(database.getMaxVarcharFieldLength());
    override
        .getExperimental()
        .getEngine()
        .getValidators()
        .setMaxNameFieldLength(database.getMaxVarcharFieldLength());
    override
        .getExperimental()
        .getEngine()
        .getValidators()
        .setMaxWorkerTypeLength(database.getMaxVarcharFieldLength());

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
                  config.setAuditLog(camunda.getData().getAuditLog().toConfiguration());
                  config.setWaitState(camunda.getData().getWaitStates().toConfiguration());
                  config.setHistoryDeletion(
                      camunda.getData().getHistoryDeletion().toConfiguration());

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

                  if (database.getAsyncReplication() != null) {
                    final var asyncReplication = database.getAsyncReplication();
                    config.getAsyncReplication().setEnabled(asyncReplication.isEnabled());
                    config
                        .getAsyncReplication()
                        .setPollingInterval(asyncReplication.getPollingInterval());
                    config
                        .getAsyncReplication()
                        .setMinSyncReplicas(asyncReplication.getMinSyncReplicas());
                    config.getAsyncReplication().setMaxLag(asyncReplication.getMaxLag());
                    config
                        .getAsyncReplication()
                        .setPauseOnMaxLagExceeded(asyncReplication.isPauseOnMaxLagExceeded());
                  }

                  applyRdbmsExtensionPropertyConfiguration(
                      config.getExtensionProperties(), camunda.getData().getExtensionProperties());
                })
            .toArgs());
  }

  private static void applyRdbmsExtensionPropertyConfiguration(
      final io.camunda.zeebe.exporter.common.extensionproperty.ExtensionPropertyConfiguration
          extensionProperties,
      final io.camunda.configuration.ExtensionProperties source) {
    extensionProperties.setToolNameProperty(source.getToolNameProperty());
    extensionProperties.setInboundConnectorTypeProperty(source.getInboundConnectorTypeProperty());
    extensionProperties.setToolPropertiesPrefix(source.getToolPropertiesPrefix());
  }

  private static void applyRdbmsHistoryExporterConfiguration(
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
    history.setMaxHistoryCleanupUsage(database.getHistory().getMaxHistoryCleanupUsage());
    history.setDecisionInstanceTTL(database.getHistory().getDecisionInstanceTTL());
  }

  private static void populateFromMonitoring(
      final BrokerBasedProperties override, final Camunda camunda) {
    populateFromMetrics(override, camunda);
  }

  private static void populateFromMetrics(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Metrics metrics = camunda.getMonitoring().getMetrics();
    override.getExperimental().getFeatures().setEnableActorMetrics(metrics.isActor());
    override.setExecutionMetricsExporterEnabled(metrics.isEnableExporterExecutionMetrics());

    final var jobMetrics = metrics.getJobMetrics();
    final var jobMetricsCfg = override.getExperimental().getEngine().getJobMetrics();
    jobMetricsCfg.setExportInterval(jobMetrics.getExportInterval());
    jobMetricsCfg.setMaxWorkerNameLength(jobMetrics.getMaxWorkerNameLength());
    jobMetricsCfg.setMaxJobTypeLength(jobMetrics.getMaxJobTypeLength());
    jobMetricsCfg.setMaxTenantIdLength(jobMetrics.getMaxTenantIdLength());
    jobMetricsCfg.setMaxUniqueKeys(jobMetrics.getMaxUniqueKeys());
    jobMetricsCfg.setEnabled(jobMetrics.isEnabled());
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

  private static void populateFromExporters(
      final BrokerBasedProperties override, final Camunda camunda) {
    final Map<String, Exporter> exporters = camunda.getData().getExporters();

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

  private static void populateFromGlobalListeners(
      final BrokerBasedProperties override, final Camunda camunda) {
    override
        .getExperimental()
        .getEngine()
        .setGlobalListeners(camunda.getCluster().getGlobalListeners());
  }

  private static void populateFromProcessInstanceCreation(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var processInstanceCreation = camunda.getProcessInstanceCreation();
    final var processInstanceCreationCfg =
        override.getExperimental().getEngine().getProcessInstanceCreation();
    processInstanceCreationCfg.setBusinessIdUniquenessEnabled(
        processInstanceCreation.isBusinessIdUniquenessEnabled());
    processInstanceCreationCfg.setMessageStartDedupExpirationSweepInterval(
        processInstanceCreation.getMessageStartDedupExpirationSweepInterval());
    processInstanceCreationCfg.setMessageStartDedupExpirationSweepBatchLimit(
        processInstanceCreation.getMessageStartDedupExpirationSweepBatchLimit());
    processInstanceCreationCfg.setMessageStartAskRetryInterval(
        processInstanceCreation.getMessageStartAskRetryInterval());
    processInstanceCreationCfg.setMessageStartLockReleasePollInterval(
        processInstanceCreation.getMessageStartLockReleasePollInterval());
    processInstanceCreationCfg.setMessageStartLockReleasePollMaxBackoff(
        processInstanceCreation.getMessageStartLockReleasePollMaxBackoff());
    processInstanceCreationCfg.setMessageStartLockReleasePollBatchLimit(
        processInstanceCreation.getMessageStartLockReleasePollBatchLimit());
  }

  private static void populateFromJobs(
      final BrokerBasedProperties override, final Camunda camunda) {
    override
        .getExperimental()
        .getEngine()
        .getJobs()
        .setIncludeVariablesInJobCompletedEvent(
            camunda.getProcessing().getEngine().getJob().isIncludeVariablesInJobCompletedEvent());
  }
}

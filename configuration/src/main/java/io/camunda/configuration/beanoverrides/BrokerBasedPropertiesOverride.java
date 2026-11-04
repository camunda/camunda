/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.Azure;
import io.camunda.configuration.Backup;
import io.camunda.configuration.Data;
import io.camunda.configuration.Export;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.Filter;
import io.camunda.configuration.Gcs;
import io.camunda.configuration.Interceptor;
import io.camunda.configuration.KeyStore;
import io.camunda.configuration.PrimaryStorage;
import io.camunda.configuration.S3;
import io.camunda.configuration.SasToken;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.SecondaryStorageDatabase;
import io.camunda.configuration.Ssl;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.LegacyBrokerBasedProperties;
import io.camunda.zeebe.backup.azure.SasTokenConfig;
import io.camunda.zeebe.broker.system.configuration.ConfigManagerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.broker.system.configuration.ExportingCfg;
import io.camunda.zeebe.broker.system.configuration.RaftCfg.FlushConfig;
import io.camunda.zeebe.broker.system.configuration.ThreadsCfg;
import io.camunda.zeebe.broker.system.configuration.backup.AzureBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig.GcsBackupStoreAuth;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
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

    populateCamundaExporter(override);

    populateFromExpression(override);

    // TODO: Populate the rest of the bean using unifiedConfiguration
    //  override.setSampleField(unifiedConfiguration.getSampleField());

    return override;
  }

<<<<<<< HEAD
  private void populateFromExpression(final BrokerBasedProperties override) {
    final var expression = unifiedConfiguration.getCamunda().getExpression();
=======
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
    override.getProcessing().setMaxPendingSideEffects(processing.getMaxPendingSideEffects());
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
    override
        .getExperimental()
        .getFeatures()
        .setEvaluateBoundaryEventCorrelationKeyInActivityScope(
            processing.isEvaluateBoundaryEventCorrelationKeyInActivityScope());

    populateFromEngine(override, camunda);
  }

  private static void populateFromEngine(
      final BrokerBasedProperties override, final Camunda camunda) {
    populateFromDistribution(override, camunda);
    populateFromBatchOperations(override, camunda);
    populateFromExpression(override, camunda);
    populateFromProcessInstanceCreation(override, camunda);
    populateFromJobs(override, camunda);
    populateFromCaches(override, camunda);
    populateFromMessages(override, camunda);
    populateFromValidators(override, camunda);
    populateFromSecretResolution(override, camunda);
    populateFromStorageOrdinals(override, camunda);

    override
        .getExperimental()
        .getEngine()
        .setMaxProcessDepth(camunda.getProcessing().getEngine().getMaxProcessDepth());

    override
        .getExperimental()
        .getEngine()
        .getStartup()
        .setRpaReexportMigrationEnabled(
            camunda.getProcessing().getEngine().getStartup().isRpaReexportMigrationEnabled());

    override
        .getExperimental()
        .getEngine()
        .getStartup()
        .setProcessDefinitionRestoreEnabled(
            camunda.getProcessing().getEngine().getStartup().isProcessDefinitionRestoreEnabled());

    override
        .getExperimental()
        .getEngine()
        .setInputMappingMode(
            toEngineMode(camunda.getProcessing().getEngine().getMappings().getInputMode()));

    final var inputComparisonMode =
        camunda.getProcessing().getEngine().getMappings().getInputComparisonMode();
    override
        .getExperimental()
        .getEngine()
        .setInputComparisonMode(
            inputComparisonMode != null ? toEngineMode(inputComparisonMode) : null);

    override
        .getExperimental()
        .getEngine()
        .setOutputMappingMode(
            toEngineOutputMode(camunda.getProcessing().getEngine().getMappings().getOutputMode()));

    final var outputComparisonMode =
        camunda.getProcessing().getEngine().getMappings().getOutputComparisonMode();
    override
        .getExperimental()
        .getEngine()
        .setOutputComparisonMode(
            outputComparisonMode != null ? toEngineOutputMode(outputComparisonMode) : null);
  }

  private static InputMappingMode toEngineMode(final InputMode mode) {
    return mode == InputMode.COMBINED ? InputMappingMode.COMBINED : InputMappingMode.ORDERED;
  }

  private static OutputMappingMode toEngineOutputMode(final OutputMode outputMode) {
    return outputMode == OutputMode.COMBINED
        ? OutputMappingMode.COMBINED
        : OutputMappingMode.ORDERED;
  }

  private static void populateFromDistribution(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var distribution = camunda.getProcessing().getEngine().getDistribution();

    final var distributionCfg = override.getExperimental().getEngine().getDistribution();
    distributionCfg.setMaxBackoffDuration(distribution.getMaxBackoffDuration());
    distributionCfg.setRedistributionInterval(distribution.getRedistributionInterval());
    distributionCfg.setPauseCommandDistribution(distribution.isPauseCommandDistribution());
  }

  private static void populateFromCaches(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var caches = camunda.getProcessing().getEngine().getCaches();

    final var cachesCfg = override.getExperimental().getEngine().getCaches();
    cachesCfg.setDrgCacheCapacity(caches.getDrgCacheCapacity());
    cachesCfg.setFormCacheCapacity(caches.getFormCacheCapacity());
    cachesCfg.setProcessCacheCapacity(caches.getProcessCacheCapacity());
    cachesCfg.setProcessCacheSoftValues(caches.isProcessCacheSoftValues());
    cachesCfg.setResourceCacheCapacity(caches.getResourceCacheCapacity());
    cachesCfg.setAuthorizationsCacheCapacity(caches.getAuthorizationsCacheCapacity());
    cachesCfg.setAuthorizationsCacheTtl(caches.getAuthorizationsCacheTtl());
    cachesCfg.setGroupNameCacheCapacity(caches.getGroupNameCacheCapacity());
    cachesCfg.setCandidateGroupNameResolution(caches.isCandidateGroupNameResolution());
  }

  private static void populateFromMessages(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var messages = camunda.getProcessing().getEngine().getMessages();

    final var messagesCfg = override.getExperimental().getEngine().getMessages();
    messagesCfg.setTtlCheckerBatchLimit(messages.getTtlCheckerBatchLimit());
    messagesCfg.setTtlCheckerInterval(messages.getTtlCheckerInterval());
    messagesCfg.setPendingSubscriptionRecoveryBatchLimit(
        messages.getPendingSubscriptionRecoveryBatchLimit());
  }

  private static void populateFromValidators(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var validators = camunda.getProcessing().getEngine().getValidators();

    override
        .getExperimental()
        .getEngine()
        .getValidators()
        .setResultsOutputMaxSize(validators.getResultsOutputMaxSize());
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

  private static void populateFromExpression(
      final BrokerBasedProperties override, final Camunda camunda) {
    final var expression = camunda.getExpression();
>>>>>>> f77c7758 (feat: make the pending subscription recovery batch limit configurable)
    override.getExperimental().getEngine().getExpression().setTimeout(expression.getTimeout());
  }

  private void populateFromGrpc(final BrokerBasedProperties override) {
    final var grpc =
        unifiedConfiguration.getCamunda().getApi().getGrpc().withBrokerNetworkProperties();

    final NetworkCfg networkCfg = override.getGateway().getNetwork();
    networkCfg.setHost(grpc.getAddress());
    networkCfg.setPort(grpc.getPort());
    networkCfg.setMinKeepAliveInterval(grpc.getMinKeepAliveInterval());
    networkCfg.setMaxMessageSize(grpc.getMaxMessageSize());

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
        new ClusterConfigurationGossiperConfig(
            syncDelay, syncTimeout, gossipFanout, metadata.getBootstrapTimeout());
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
    final Backup backup =
        unifiedConfiguration.getCamunda().getData().getBackup().withBrokerBackupProperties();
    final BackupStoreCfg backupStoreCfg = override.getData().getBackup();
    backupStoreCfg.setStore(BackupStoreType.valueOf(backup.getStore().name()));

    if (backup.getReadTimeout() != null && !backup.getReadTimeout().isPositive()) {
      throw new IllegalArgumentException("BackupStore readTimeout must be positive");
    }
    backupStoreCfg.setReadTimeout(backup.getReadTimeout());

    if (backup.getWriteTimeout() != null && !backup.getWriteTimeout().isPositive()) {
      throw new IllegalArgumentException("BackupStore writeTimeout must be positive");
    }
    backupStoreCfg.setWriteTimeout(backup.getWriteTimeout());

    populateFromS3(override);
    populateFromGcs(override);
    populateFromAzure(override);
    populateFromFilesystem(override);

    override.getData().setBackup(backupStoreCfg);
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

    // Migrate RocksDB configuration from new unified config to old broker config structure
    populateFromRocksDb(override, primaryStorage);
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
    final Gcs gcs = unifiedConfiguration.getCamunda().getData().getBackup().getGcs();
    final GcsBackupStoreConfig gcsBackupStoreConfig = override.getData().getBackup().getGcs();
    gcsBackupStoreConfig.setBucketName(gcs.getBucketName());
    gcsBackupStoreConfig.setBasePath(gcs.getBasePath());
    gcsBackupStoreConfig.setHost(gcs.getHost());
    gcsBackupStoreConfig.setAuth(GcsBackupStoreAuth.valueOf(gcs.getAuth().name()));
    gcsBackupStoreConfig.setBufferSize(gcs.getBufferSize());

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

  private void populateFromFilesystem(final BrokerBasedProperties override) {
    final Filesystem filesystem =
        unifiedConfiguration.getCamunda().getData().getBackup().getFilesystem();
    final FilesystemBackupStoreConfig filesystemBackupStoreConfig =
        override.getData().getBackup().getFilesystem();
    filesystemBackupStoreConfig.setBasePath(filesystem.getBasePath());

    override.getData().getBackup().setFilesystem(filesystemBackupStoreConfig);
  }

  private void populateCamundaExporter(final BrokerBasedProperties override) {
    final SecondaryStorage secondaryStorage =
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage();

    if (!secondaryStorage.getAutoconfigureCamundaExporter()) {
      LOGGER.debug("Skipping autoconfiguration of the (default) exporter 'camundaexporter'");
      return;
    }

    final SecondaryStorageDatabase database;
    if (SecondaryStorageType.elasticsearch == secondaryStorage.getType()) {
      database =
          unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getElasticsearch();
    } else if (SecondaryStorageType.opensearch == secondaryStorage.getType()) {
      database = unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getOpensearch();
    } else {
      // RDBMS and NONE are not supported.
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

    /* Override config map values */

    // https://github.com/camunda/camunda/issues/37880
    // it is possible to have an exporter with no args defined
    Map<String, Object> args = exporter.getArgs();
    if (args == null) {
      args = new LinkedHashMap<>();
      exporter.setArgs(args);
    }

    setArg(args, "connect.type", secondaryStorage.getType().name());
    setArg(args, "connect.url", database.getUrl());
    setArg(args, "connect.clusterName", database.getClusterName());

    // Add security configuration mapping
    if (database.getSecurity() != null) {
      setArg(args, "connect.security.enabled", database.getSecurity().isEnabled());
      setArg(args, "connect.security.certificatePath", database.getSecurity().getCertificatePath());
      setArg(args, "connect.security.verifyHostname", database.getSecurity().isVerifyHostname());
      setArg(args, "connect.security.selfSigned", database.getSecurity().isSelfSigned());
    }
    setArg(args, "connect.username", database.getUsername());
    setArg(args, "connect.password", database.getPassword());

    setArg(args, "connect.indexPrefix", database.getIndexPrefix());

    // Guarded, unlike the fields above: those all carry a non-null default, so writing them
    // unconditionally is harmless, while these two are unset unless someone configures them.
    setArgIfNotNull(args, "connect.maxConnections", database.getMaxConnections());
    setArgIfNotNull(args, "connect.maxConnectionsPerRoute", database.getMaxConnectionsPerRoute());

    setArg(
        args, "history.processInstanceEnabled", database.getHistory().isProcessInstanceEnabled());
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
}

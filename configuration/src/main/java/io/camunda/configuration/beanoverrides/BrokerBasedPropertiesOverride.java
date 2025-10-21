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
import io.camunda.configuration.CommandApi;
import io.camunda.configuration.Data;
import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.Export;
import io.camunda.configuration.Exporter;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.Filter;
import io.camunda.configuration.Gcs;
import io.camunda.configuration.Interceptor;
import io.camunda.configuration.InterceptorPlugin;
import io.camunda.configuration.InternalApi;
import io.camunda.configuration.KeyStore;
import io.camunda.configuration.Membership;
import io.camunda.configuration.PrimaryStorage;
import io.camunda.configuration.Rdbms;
import io.camunda.configuration.S3;
import io.camunda.configuration.SasToken;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.Ssl;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.LegacyBrokerBasedProperties;
import io.camunda.zeebe.backup.azure.SasTokenConfig;
import io.camunda.zeebe.broker.system.configuration.ConfigManagerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.broker.system.configuration.ExportingCfg;
import io.camunda.zeebe.broker.system.configuration.MembershipCfg;
import io.camunda.zeebe.broker.system.configuration.RaftCfg.FlushConfig;
import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg;
import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg.CommandApiCfg;
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
    final var cluster = unifiedConfiguration.getCamunda().getCluster();

    override.getCluster().setNodeId(cluster.getNodeId());
    override.getCluster().setPartitionsCount(cluster.getPartitionCount());
    override.getCluster().setReplicationFactor(cluster.getReplicationFactor());
    override.getCluster().setClusterSize(cluster.getSize());

    populateFromMembership(override);
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
    final Backup backup =
        unifiedConfiguration.getCamunda().getData().getBackup().withBrokerBackupProperties();
    final BackupStoreCfg backupStoreCfg = override.getData().getBackup();
    backupStoreCfg.setStore(BackupStoreType.valueOf(backup.getStore().name()));

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

    final DocumentBasedSecondaryStorageDatabase database;
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

    populateInterceptorPlugins(database, args);

    setArg(args, "connect.username", database.getUsername());
    setArg(args, "connect.password", database.getPassword());

    setArg(args, "connect.indexPrefix", database.getIndexPrefix());
    setArg(args, "index.numberOfShards", database.getNumberOfShards());

    setArg(
        args, "history.processInstanceEnabled", database.getHistory().isProcessInstanceEnabled());
    setArg(
        args,
        "batchOperation.exportItemsOnCreation",
        database.getBatchOperations().isExportItemsOnCreation());

    setArg(args, "bulk.delay", database.getBulk().getDelay().getSeconds());
    setArg(args, "bulk.size", database.getBulk().getSize());
    setArg(args, "bulk.memoryLimit", database.getBulk().getMemoryLimit().toMegabytes());
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

    exporter.setArgs(args);
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
}

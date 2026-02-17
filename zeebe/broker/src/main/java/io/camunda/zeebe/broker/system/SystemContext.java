/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import static io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector.MINIMUM_SNAPSHOT_PERIOD;

import io.atomix.cluster.AtomixCluster;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.validation.AuthorizationValidator;
import io.camunda.security.validation.GroupValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.IdentityInitializationException;
import io.camunda.security.validation.RoleValidator;
import io.camunda.security.validation.TenantValidator;
import io.camunda.service.UserServices;
import io.camunda.zeebe.backup.azure.AzureBackupStore;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.backup.schedule.Schedule.NoneSchedule;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.partitioning.RocksDbSharedCache;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.DiskCfg.FreeSpaceCfg;
import io.camunda.zeebe.broker.system.configuration.ExperimentalCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.broker.system.configuration.RocksdbCfg;
import io.camunda.zeebe.broker.system.configuration.SecurityCfg;
import io.camunda.zeebe.broker.system.configuration.backup.AzureBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.engine.BatchOperationCfg;
import io.camunda.zeebe.broker.system.configuration.engine.EngineCfg;
import io.camunda.zeebe.broker.system.configuration.engine.GlobalListenerCfg;
import io.camunda.zeebe.broker.system.configuration.engine.GlobalListenersCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.engine.processing.globallistener.GlobalListenerValidator;
import io.camunda.zeebe.engine.processing.identity.initialize.AuthorizationConfigurer;
import io.camunda.zeebe.engine.processing.identity.initialize.GroupConfigurer;
import io.camunda.zeebe.engine.processing.identity.initialize.RoleConfigurer;
import io.camunda.zeebe.engine.processing.identity.initialize.TenantConfigurer;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperationChunk;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.TlsConfigUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public final class SystemContext {

  public static final Logger LOG = Loggers.SYSTEM_LOGGER;
  public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
  public static final String LEGACY_INITIAL_CONTACT_POINTS_PROPERTY =
      "zeebe.broker.cluster.initialContactPoints";
  public static final String UNIFIED_INITIAL_CONTACT_POINTS_PROPERTY =
      "camunda.cluster.initial-contact-points";
  private static final String BROKER_ID_LOG_PROPERTY = "broker-id";
  private static final String SNAPSHOT_PERIOD_ERROR_MSG =
      "Snapshot period %s needs to be larger then or equals to one minute.";
  private static final String MAX_BATCH_SIZE_ERROR_MSG =
      "Expected to have an append batch size maximum which is non negative and smaller then '%d', but was '%s'.";
  private static final String INITIAL_CONTACT_POINTS_ERROR_MSG =
      "Initial contact points must be configured when cluster size is greater than 1. "
          + "Please configure '"
          + UNIFIED_INITIAL_CONTACT_POINTS_PROPERTY
          + "' or the legacy property '"
          + LEGACY_INITIAL_CONTACT_POINTS_PROPERTY
          + "'.";

  /**
   * The maximum number of items stored in a single batch operation chunk. This value is limited by
   * two factors:
   *
   * <ul>
   *   <li>The hard-coded max record size of 4MB in the engine
   *   <li>The RocksDB block size configured at 32KB. Chunks significantly larger than this would
   *       span multiple blocks, which is not optimal for read/write performance.
   *       <p>This value is chosen to balance between the number of database entries and the size of
   *       each entry. A chunk size of 3000 item keys results in a chunk size of approximately 24KB
   *       (31KB with overhead), which is reasonable for RocksDB to handle efficiently. Larger chunk
   *       sizes could lead to increased memory usage and potential performance degradation, while
   *       smaller chunk sizes would increase the number of database entries, leading to higher
   *       overhead in managing them.
   * </ul>
   *
   * @see ZeebeRocksDbFactory
   * @see PersistedBatchOperationChunk
   */
  private static final long MAX_CHUNK_SIZE = 3000;

  private final Duration shutdownTimeout;
  private final BrokerCfg brokerCfg;
  private final IdentityConfiguration identityConfiguration;
  private Map<String, String> diagnosticContext;
  private final ActorScheduler scheduler;
  private final AtomixCluster cluster;
  private final BrokerClient brokerClient;
  private final MeterRegistry meterRegistry;
  private final SecurityConfiguration securityConfiguration;
  private final UserServices userServices;
  private final PasswordEncoder passwordEncoder;
  private final JwtDecoder jwtDecoder;
  private final SearchClientsProxy searchClientsProxy;
  private final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;
  private final NodeIdProvider nodeIdProvider;
  private final GlobalListenerValidator globalListenerValidator;

  public SystemContext(
      final Duration shutdownTimeout,
      final BrokerCfg brokerCfg,
      final IdentityConfiguration identityConfiguration,
      final ActorScheduler scheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient,
      final MeterRegistry meterRegistry,
      final SecurityConfiguration securityConfiguration,
      final UserServices userServices,
      final PasswordEncoder passwordEncoder,
      final JwtDecoder jwtDecoder,
      final SearchClientsProxy searchClientsProxy,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final NodeIdProvider nodeIdProvider) {
    this.shutdownTimeout = shutdownTimeout;
    this.brokerCfg = brokerCfg;
    this.identityConfiguration = identityConfiguration;
    this.scheduler = scheduler;
    this.cluster = cluster;
    this.brokerClient = brokerClient;
    this.meterRegistry = meterRegistry;
    this.securityConfiguration = securityConfiguration;
    this.userServices = userServices;
    this.passwordEncoder = passwordEncoder;
    this.jwtDecoder = jwtDecoder;
    this.searchClientsProxy = searchClientsProxy;
    this.brokerRequestAuthorizationConverter = brokerRequestAuthorizationConverter;
    this.nodeIdProvider = nodeIdProvider;
    globalListenerValidator = new GlobalListenerValidator();
    initSystemContext();
  }

  @VisibleForTesting
  public SystemContext(
      final BrokerCfg brokerCfg,
      final ActorScheduler scheduler,
      final AtomixCluster cluster,
      final BrokerClient brokerClient,
      final SecurityConfiguration securityConfiguration,
      final UserServices userServices,
      final PasswordEncoder passwordEncoder,
      final JwtDecoder jwtDecoder,
      final SearchClientsProxy searchClientsProxy,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this(
        DEFAULT_SHUTDOWN_TIMEOUT,
        brokerCfg,
        null,
        scheduler,
        cluster,
        brokerClient,
        new SimpleMeterRegistry(),
        securityConfiguration,
        userServices,
        passwordEncoder,
        jwtDecoder,
        searchClientsProxy,
        brokerRequestAuthorizationConverter,
        null);
  }

  private void initSystemContext() {
    validateConfiguration();

    final var cluster = brokerCfg.getCluster();
    final String brokerId = String.format("Broker-%d", cluster.getNodeId());

    diagnosticContext = Collections.singletonMap(BROKER_ID_LOG_PROPERTY, brokerId);
  }

  private void validateConfiguration() {
    final ClusterCfg cluster = brokerCfg.getCluster();

    validateDataConfig(brokerCfg.getData());

    validClusterConfigs(cluster);
    validateExperimentalConfigs(cluster, brokerCfg.getExperimental());

    validateExporters(brokerCfg.getExporters());

    final var security = brokerCfg.getNetwork().getSecurity();
    if (security.isEnabled()) {
      validateNetworkSecurityConfig(security);
    }

    validateInitializationConfig();
  }

  private void validClusterConfigs(final ClusterCfg cluster) {
    final var gossiper = cluster.getConfigManager().gossip();

    final var errors = new ArrayList<String>(0);

    if (cluster.getClusterSize() > 1
        && (cluster.getInitialContactPoints() == null
            || cluster.getInitialContactPoints().isEmpty())) {
      errors.add(INITIAL_CONTACT_POINTS_ERROR_MSG);
    }

    if (!gossiper.syncDelay().isPositive()) {
      errors.add(
          String.format(
              "syncDelay must be positive: configured value = %d ms",
              gossiper.syncDelay().toMillis()));
    }
    if (!gossiper.syncRequestTimeout().isPositive()) {
      errors.add(
          String.format(
              "syncRequestTimeout must be positive: configured value = %d ms",
              gossiper.syncRequestTimeout().toMillis()));
    }
    if (gossiper.gossipFanout() < 2) {
      errors.add(
          String.format(
              "gossipFanout must be greater than 1: configured value = %d",
              gossiper.gossipFanout()));
    }

    if (!errors.isEmpty()) {
      throw new InvalidConfigurationException(
          "Invalid ConfigManager configuration: " + String.join(", ", errors), null);
    }
  }

  private void validateExporters(final Map<String, ExporterCfg> exporters) {
    final Set<Entry<String, ExporterCfg>> entries = exporters.entrySet();
    final var badExportersNames =
        entries.stream()
            .filter(entry -> entry.getValue().getClassName() == null)
            .map(Entry::getKey)
            .toList();

    if (!badExportersNames.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected to find a 'className' configured for the exporter. Couldn't find a valid one for the following exporters "
              + badExportersNames);
    }
  }

  private void validateExperimentalConfigs(
      final ClusterCfg cluster, final ExperimentalCfg experimental) {
    final var maxAppendBatchSize = experimental.getMaxAppendBatchSize();
    if (maxAppendBatchSize.isNegative() || maxAppendBatchSize.toBytes() >= Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          String.format(MAX_BATCH_SIZE_ERROR_MSG, Integer.MAX_VALUE, maxAppendBatchSize));
    }

    final var partitioningConfig = experimental.getPartitioning();
    if (partitioningConfig.getScheme() == Scheme.FIXED) {
      validateFixedPartitioningScheme(cluster, experimental);
    }

    Optional.of(experimental)
        .map(ExperimentalCfg::getEngine)
        .map(EngineCfg::getBatchOperations)
        .ifPresent(this::validateBatchOperationsConfig);

    Optional.of(experimental)
        .map(ExperimentalCfg::getEngine)
        .map(EngineCfg::getGlobalListeners)
        .ifPresent(this::validateListenersConfig);

    Optional.of(experimental)
        .map(ExperimentalCfg::getRocksdb)
        .ifPresent(c -> validateRocksDbConfig(c, cluster.getPartitionsCount()));
  }

  private void validateDataConfig(final DataCfg dataCfg) {
    final var snapshotPeriod = dataCfg.getSnapshotPeriod();
    if (snapshotPeriod.isNegative() || snapshotPeriod.minus(MINIMUM_SNAPSHOT_PERIOD).isNegative()) {
      throw new IllegalArgumentException(String.format(SNAPSHOT_PERIOD_ERROR_MSG, snapshotPeriod));
    }

    if (dataCfg.getDisk().isEnableMonitoring()) {
      try {
        final FreeSpaceCfg freeSpaceCfg = dataCfg.getDisk().getFreeSpace();
        final var processingFreeSpace = freeSpaceCfg.getProcessing().toBytes();
        final var replicationFreeSpace = freeSpaceCfg.getReplication().toBytes();
        if (processingFreeSpace <= replicationFreeSpace) {
          throw new IllegalArgumentException(
              "Minimum free space for processing (%d) must be greater than minimum free space for replication (%d). Configured values are %s"
                  .formatted(processingFreeSpace, replicationFreeSpace, freeSpaceCfg));
        }
      } catch (final Exception e) {
        throw new InvalidConfigurationException("Failed to parse disk monitoring configuration", e);
      }
    }

    validateBackupCfg(dataCfg.getBackup());
  }

  private void validateBatchOperationsConfig(final BatchOperationCfg config) {
    final var errors = new ArrayList<String>(0);

    if (config.getSchedulerInterval().isNegative()) {
      errors.add(
          String.format(
              "experimental.engine.batchOperations.schedulerInterval must be positive, but was %s",
              config.getSchedulerInterval()));
    }

    if (config.getChunkSize() <= 0) {
      errors.add(
          String.format(
              "experimental.engine.batchOperations.chunkSize must be greater than 0, but was %s",
              config.getChunkSize()));
    }

    if (config.getChunkSize() > MAX_CHUNK_SIZE) {
      LOG.warn(
          "Setting experimental.engine.batchOperations.chunkSize should be lower than {}, but was {}",
          MAX_CHUNK_SIZE,
          config.getChunkSize());
    }

    if (config.getQueryPageSize() <= 0) {
      errors.add(
          String.format(
              "experimental.engine.batchOperations.queryPageSize must be greater than 0, but was %s",
              config.getQueryPageSize()));
    }

    if (config.getQueryInClauseSize() <= 0) {
      errors.add(
          String.format(
              "experimental.engine.batchOperations.queryInClauseSize must be greater than 0, but was %s",
              config.getQueryInClauseSize()));
    }

    if (config.getQueryRetryMax() < 0) {
      errors.add(
          String.format(
              "experimental.engine.batchOperations.queryRetryMax must be greater than or equal to 0, but was %s",
              config.getQueryRetryMax()));
    }

    if (config.getQueryRetryInitialDelay().isNegative()
        || config.getQueryRetryInitialDelay().isZero()) {
      errors.add(
          String.format(
              "experimental.engine.batchOperations.queryRetryInitialDelay must be positive, but was %s",
              config.getQueryRetryInitialDelay()));
    }

    if (config.getQueryRetryMaxDelay().isNegative() || config.getQueryRetryMaxDelay().isZero()) {
      errors.add(
          String.format(
              "experimental.engine.batchOperations.queryRetryMaxDelay must be positive, but was %s",
              config.getQueryRetryMaxDelay()));
    }

    if (config.getQueryRetryMaxDelay().compareTo(config.getQueryRetryInitialDelay()) < 0) {
      errors.add(
          String.format(
              "experimental.engine.batchOperations.queryRetryMaxDelay must be greater than or equal to the experimental.engine.batchOperations.queryRetryInitialDelay of %s, but was %s",
              config.getQueryRetryInitialDelay(), config.getQueryRetryMaxDelay()));
    }

    if (config.getQueryRetryBackoffFactor() < 1) {
      errors.add(
          String.format(
              "experimental.engine.batchOperations.queryRetryBackoffFactor must be greater than or equal to 1, but was %s",
              config.getQueryRetryBackoffFactor()));
    }

    if (!errors.isEmpty()) {
      throw new InvalidConfigurationException(
          "Invalid BatchOperations configuration: " + String.join(", ", errors), null);
    }
  }

  private void validateBackupCfg(final BackupCfg backup) {
    try {
      switch (backup.getStore()) {
        case NONE -> LOG.warn("No backup store is configured. Backups will not be taken");
        case S3 -> S3BackupStore.validateConfig(S3BackupStoreConfig.toStoreConfig(backup.getS3()));
        case GCS ->
            GcsBackupStore.validateConfig(GcsBackupStoreConfig.toStoreConfig(backup.getGcs()));
        case AZURE ->
            AzureBackupStore.validateConfig(
                AzureBackupStoreConfig.toStoreConfig(backup.getAzure()));
        case FILESYSTEM ->
            FilesystemBackupStore.validateConfig(
                FilesystemBackupStoreConfig.toStoreConfig(backup.getFilesystem()));
        default ->
            throw new UnsupportedOperationException(
                "Does not support validating configuration of backup store %s"
                    .formatted(backup.getStore()));
      }
    } catch (final Exception e) {
      throw new InvalidConfigurationException(
          "Failed configuring backup store %s".formatted(backup.getStore()), e);
    }

    validateBackupSchedulerConfig(backup);
  }

  private void validateFixedPartitioningScheme(
      final ClusterCfg cluster, final ExperimentalCfg experimental) {
    final var partitioning = experimental.getPartitioning();
    final var partitions = partitioning.getFixed();
    final var replicationFactor = cluster.getReplicationFactor();
    final var partitionsCount = cluster.getPartitionsCount();

    final var partitionMembers = new HashMap<Integer, Set<Integer>>();
    for (final var partition : partitions) {
      final var members =
          validateFixedPartitionMembers(
              cluster, partition, cluster.getRaft().isEnablePriorityElection());
      partitionMembers.put(partition.getPartitionId(), members);
    }

    for (int partitionId = 1; partitionId <= partitionsCount; partitionId++) {
      final var members = partitionMembers.getOrDefault(partitionId, Collections.emptySet());
      if (members.size() < replicationFactor) {
        throw new IllegalArgumentException(
            String.format(
                "Expected fixed partition scheme to define configurations for all partitions such "
                    + "that they have %d replicas, but partition %d has %d configured replicas: %s",
                replicationFactor, partitionId, members.size(), members));
      }
    }
  }

  private Set<Integer> validateFixedPartitionMembers(
      final ClusterCfg cluster,
      final FixedPartitionCfg partitionConfig,
      final boolean isPriorityElectionEnabled) {
    final var members = new HashSet<Integer>();
    final var clusterSize = cluster.getClusterSize();
    final var partitionsCount = cluster.getPartitionsCount();
    final var partitionId = partitionConfig.getPartitionId();

    if (partitionId < 1 || partitionId > partitionsCount) {
      throw new IllegalArgumentException(
          String.format(
              "Expected fixed partition scheme to define entries with a valid partitionId between 1"
                  + " and %d, but %d was given",
              partitionsCount, partitionId));
    }

    final var observedPriorities = new HashSet<Integer>();
    for (final var node : partitionConfig.getNodes()) {
      final var nodeId = node.getNodeId();
      if (nodeId < 0 || nodeId >= clusterSize) {
        throw new IllegalArgumentException(
            String.format(
                "Expected fixed partition scheme for partition %d to define nodes with a nodeId "
                    + "between 0 and %d, but it was %d",
                partitionId, clusterSize - 1, nodeId));
      }

      if (isPriorityElectionEnabled && !observedPriorities.add(node.getPriority())) {
        throw new IllegalArgumentException(
            String.format(
                "Expected each node for a partition %d to have a different priority, but at least "
                    + "two of them have the same priorities: %s",
                partitionId, partitionConfig.getNodes()));
      }

      members.add(nodeId);
    }

    return members;
  }

  // actually initializing the entities will be done in IdentitySetupInitializer.
  // Validation is done here, only to be able to stop the application on error.
  private void validateInitializationConfig() {
    final IdentifierValidator identifierValidator =
        new IdentifierValidator(
            securityConfiguration.getCompiledIdValidationPattern(),
            securityConfiguration.getCompiledGroupIdValidationPattern());
    final AuthorizationConfigurer authorizationConfigurer =
        new AuthorizationConfigurer(new AuthorizationValidator(identifierValidator));
    final TenantConfigurer tenantConfigurer =
        new TenantConfigurer(new TenantValidator(identifierValidator));
    final GroupConfigurer groupConfigurer =
        new GroupConfigurer(new GroupValidator(identifierValidator));
    final var roleConfigurer = new RoleConfigurer(new RoleValidator(identifierValidator));

    final Either<List<String>, List<AuthorizationRecord>> configuredAuthorizations =
        authorizationConfigurer.configureEntities(
            securityConfiguration.getInitialization().getAuthorizations());
    final Either<List<String>, List<TenantRecord>> configuredTenants =
        tenantConfigurer.configureEntities(securityConfiguration.getInitialization().getTenants());
    final Either<List<String>, List<GroupRecord>> configuredGroups =
        groupConfigurer.configureEntities(securityConfiguration.getInitialization().getGroups());
    final Either<List<String>, List<RoleRecord>> configuredRoles =
        roleConfigurer.configureEntities(securityConfiguration.getInitialization().getRoles());

    // TODO: after adding more entity types, change this, so it accounts for all violations all
    //   together.
    configuredAuthorizations.ifLeft(
        (violations) -> {
          throw new IdentityInitializationException(
              "Cannot initialize configured authorizations: %n- %s"
                  .formatted(String.join(System.lineSeparator() + "- ", violations)));
        });
    configuredTenants.ifLeft(
        (violations) -> {
          throw new IdentityInitializationException(
              "Cannot initialize configured tenants: %n- %s"
                  .formatted(String.join(System.lineSeparator() + "- ", violations)));
        });
    configuredRoles.ifLeft(
        (violations) -> {
          throw new IdentityInitializationException(
              "Cannot initialize configured roles: %n- %s"
                  .formatted(String.join(System.lineSeparator() + "- ", violations)));
        });

    configuredGroups.ifLeft(
        (violations) -> {
          throw new IdentityInitializationException(
              "Cannot initialize configured groups: %n- %s"
                  .formatted(String.join(System.lineSeparator() + "- ", violations)));
        });
  }

  private void validateNetworkSecurityConfig(final SecurityCfg security) {
    TlsConfigUtil.validateTlsConfig(
        security.getCertificateChainPath(),
        security.getPrivateKeyPath(),
        security.getKeyStore().getFilePath());
  }

  private void validateListenersConfig(final GlobalListenersCfg listeners) {
    final String propertyLocation = "camunda.cluster.global-listeners.user-task";
    final List<GlobalListenerCfg> taskListeners = listeners.getUserTask();

    // Validate listeners and ignore invalid ones
    final List<GlobalListenerCfg> validListeners = new ArrayList<>();
    for (int i = 0; i < taskListeners.size(); i++) {
      final GlobalListenerCfg listenerCfg = taskListeners.get(i);
      final String propertyPrefix = String.format("%s.%d", propertyLocation, i);

      // Check if retries actually contains a number
      try {
        if (Integer.parseInt(listenerCfg.getRetries()) <= 0) {
          throw new NumberFormatException();
        }
      } catch (final NumberFormatException e) {
        LOG.warn(
            "Invalid retries for global listener: '{}'; listener will be ignored [{}.retries]",
            listenerCfg.getRetries(),
            propertyPrefix);
        continue;
      }

      // Convert to record in order to use standard validation methods
      final GlobalListenerRecord listenerRecord =
          listenerCfg.createGlobalListenerConfiguration().toRecord();

      // Check if id is present
      if (globalListenerValidator.idProvided(listenerRecord).isLeft()) {
        LOG.warn(
            "Missing id for global listener; listener will be ignored [{}.type]", propertyPrefix);
        continue;
      }

      // Check if type is present
      if (globalListenerValidator.typeProvided(listenerRecord).isLeft()) {
        LOG.warn(
            "Missing job type for global listener; listener will be ignored [{}.type]",
            propertyPrefix);
        continue;
      }

      // Validate event types
      // Note: the validator is not used directly because autocorrection is attempted here
      final var eventTypes = // consider event types in lowercase for validation
          listenerRecord.getEventTypes().stream().map(String::toLowerCase).toList();
      final boolean containsAllEventsKeyword =
          eventTypes.contains(GlobalListenerRecord.ALL_EVENT_TYPES);

      final List<String> validEventTypes =
          eventTypes.stream()
              .filter( // check if provided event types have valid values
                  eventType -> {
                    if (globalListenerValidator.isValidEventType(listenerRecord, eventType)) {
                      return true;
                    } else {
                      LOG.warn(
                          "Invalid event type will be ignored: '{}' [{}.eventTypes]",
                          eventType,
                          propertyPrefix);
                      return false;
                    }
                  })
              .filter(
                  eventType -> { // check if "all" is used alongside other event types
                    if (!GlobalListenerRecord.ALL_EVENT_TYPES.equals(eventType)
                        && containsAllEventsKeyword) {
                      LOG.warn(
                          "Extra event type defined alongside '{}' will be ignored: '{}' [{}.eventTypes]",
                          GlobalListenerRecord.ALL_EVENT_TYPES,
                          eventType,
                          propertyPrefix);
                      return false;
                    }
                    return true;
                  })
              .toList();

      // Remove duplicates
      final List<String> uniqueEventTypes = new ArrayList<>();
      validEventTypes.forEach(
          eventType -> {
            if (uniqueEventTypes.contains(eventType)) {
              LOG.warn(
                  "Duplicated event type will be considered only once: '{}' [{}.eventTypes]",
                  eventType,
                  propertyPrefix);
            } else {
              uniqueEventTypes.add(eventType);
            }
          });

      // Check if valid event types have been provided
      if (uniqueEventTypes.isEmpty()) {
        LOG.warn(
            "Missing event types for global listener; listener will be ignored [{}.eventTypes]",
            propertyPrefix);
        continue;
      }

      listenerCfg.setEventTypes(uniqueEventTypes);
      validListeners.add(listenerCfg);
    }

    listeners.setUserTask(validListeners);
  }

  private void validateRocksDbConfig(final RocksdbCfg rocksdbCfg, final int partitionsCount) {
    RocksDbSharedCache.validateRocksDbMemory(rocksdbCfg, partitionsCount);
  }

  private void validateBackupSchedulerConfig(final BackupCfg backupSchedulerCfg) {
    if (!backupSchedulerCfg.isContinuous() || !backupSchedulerCfg.isRequired()) {
      return;
    }

    // will throw IllegalArgumentException if schedule is invalid
    final var schedule = backupSchedulerCfg.getSchedule();
    if (backupSchedulerCfg.isRequired() && schedule instanceof NoneSchedule) {
      throw new IllegalArgumentException("Backup schedule is mandatory, none provided.");
    }
    // will throw IllegalArgumentException if schedule is invalid
    Objects.requireNonNull(backupSchedulerCfg.getRetention().getCleanupSchedule());
    final var retentionSchedule = backupSchedulerCfg.getRetention().getCleanupSchedule();
    if (backupSchedulerCfg.isRequired() && retentionSchedule instanceof NoneSchedule) {
      throw new IllegalArgumentException("Backup retention schedule is mandatory, none provided.");
    }
  }

  public ActorScheduler getScheduler() {
    return scheduler;
  }

  public BrokerCfg getBrokerConfiguration() {
    return brokerCfg;
  }

  public IdentityConfiguration getIdentityConfiguration() {
    return identityConfiguration;
  }

  public AtomixCluster getCluster() {
    return cluster;
  }

  public Map<String, String> getDiagnosticContext() {
    return diagnosticContext;
  }

  public BrokerClient getBrokerClient() {
    return brokerClient;
  }

  public Duration getShutdownTimeout() {
    return shutdownTimeout;
  }

  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  public SecurityConfiguration getSecurityConfiguration() {
    return securityConfiguration;
  }

  public UserServices getUserServices() {
    return userServices;
  }

  public PasswordEncoder getPasswordEncoder() {
    return passwordEncoder;
  }

  public JwtDecoder getJwtDecoder() {
    return jwtDecoder;
  }

  public SearchClientsProxy getSearchClientsProxy() {
    return searchClientsProxy;
  }

  public BrokerRequestAuthorizationConverter getBrokerRequestAuthorizationConverter() {
    return brokerRequestAuthorizationConverter;
  }

  public NodeIdProvider getNodeIdProvider() {
    return nodeIdProvider;
  }
}

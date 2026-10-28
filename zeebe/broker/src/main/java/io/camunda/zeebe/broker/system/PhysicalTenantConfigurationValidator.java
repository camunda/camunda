/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import static io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector.MINIMUM_SNAPSHOT_PERIOD;

import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.validation.AuthorizationValidator;
import io.camunda.security.validation.GroupValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.IdentityInitializationException;
import io.camunda.security.validation.RoleValidator;
import io.camunda.security.validation.TenantValidator;
import io.camunda.zeebe.backup.azure.AzureBackupStore;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.backup.schedule.Schedule.NoneSchedule;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.ExperimentalCfg;
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
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Validates the effective configuration of one physical tenant against the root broker
 * configuration. The single entry point for both the boot of a node and a runtime change to the set
 * of physical tenants, so that a runtime change cannot install a configuration the boot would
 * reject.
 */
@NullMarked
public final class PhysicalTenantConfigurationValidator {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final String SNAPSHOT_PERIOD_ERROR_MSG =
      "Snapshot period %s needs to be larger then or equals to one minute.";

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

  private final BrokerCfg rootBrokerCfg;
  private final GlobalListenerValidator globalListenerValidator = new GlobalListenerValidator();

  public PhysicalTenantConfigurationValidator(final BrokerCfg rootBrokerCfg) {
    this.rootBrokerCfg = rootBrokerCfg;
  }

  /**
   * @throws RuntimeException describing the first violation found, naming the physical tenant
   */
  public void validate(final String tenantId, final PhysicalTenantContext context) {
    validateTenantMatchesRootConfig(tenantId, context.config());
    validatePerTenantConfiguration(tenantId, context.config());
    // actually initializing the entities will be done in IdentitySetupInitializer.
    // Validation is done here, only to be able to stop the application on error.
    validateInitializationConfigForTenant(tenantId, context.securityConfig());
  }

  /**
   * Validates that the properties denied for per-tenant override (see {@code
   * PhysicalTenantOverridePolicyValidation}) actually carry the same value in the tenant's
   * effective {@link BrokerCfg} as in the root config. Without this check, an operator
   * misconfiguring one of these per tenant is silently ignored (the tenant's own value is simply
   * never read) rather than rejected, defeating the purpose of denying the override.
   */
  private void validateTenantMatchesRootConfig(final String tenantId, final BrokerCfg tenantCfg) {
    final var rootDirectory = rootBrokerCfg.getData().getDirectory();
    final var tenantDirectory = tenantCfg.getData().getDirectory();
    if (!Objects.equals(rootDirectory, tenantDirectory)) {
      throw new IllegalArgumentException(
          tenantScopedMessage(
              tenantId,
              String.format(
                  "Expected data directory to be '%s' (shared across all physical tenants), but "
                      + "was overridden to '%s'",
                  rootDirectory, tenantDirectory)));
    }

    final var rootReplicationFactor = rootBrokerCfg.getCluster().getReplicationFactor();
    final var tenantReplicationFactor = tenantCfg.getCluster().getReplicationFactor();
    if (rootReplicationFactor != tenantReplicationFactor) {
      throw new IllegalArgumentException(
          tenantScopedMessage(
              tenantId,
              String.format(
                  "Expected replication factor to be %d (shared across all physical tenants), "
                      + "but was overridden to %d",
                  rootReplicationFactor, tenantReplicationFactor)));
    }
  }

  /**
   * Validates the subset of {@link BrokerCfg} that is overridable per physical tenant (see {@code
   * PhysicalTenantOverridePolicyValidation}), against the given tenant's own effective
   * configuration. The root-only pass in {@link #validateConfiguration()} covers a disjoint set of
   * properties, so for the default tenant this method is the only place its overridable settings
   * (snapshot period, backup, batch operations, listeners, fixed partition coverage) are validated.
   */
  private void validatePerTenantConfiguration(final String tenantId, final BrokerCfg tenantCfg) {
    validatePerTenantDataConfig(tenantId, tenantCfg.getData());
    validatePerTenantExperimentalConfigs(
        tenantId, tenantCfg.getCluster(), tenantCfg.getExperimental());
  }

  private static String tenantScopedMessage(final String tenantId, final String message) {
    return "%s [physical tenant '%s']".formatted(message, tenantId);
  }

  private void validatePerTenantExperimentalConfigs(
      final String tenantId, final ClusterCfg tenantCluster, final ExperimentalCfg experimental) {
    final var partitioningConfig = experimental.getPartitioning();
    if (partitioningConfig.getScheme() == Scheme.FIXED) {
      validateFixedPartitioningScheme(tenantId, tenantCluster);
    }

    Optional.of(experimental)
        .map(ExperimentalCfg::getEngine)
        .map(EngineCfg::getBatchOperations)
        .ifPresent(config -> validateBatchOperationsConfig(tenantId, config));

    Optional.of(experimental)
        .map(ExperimentalCfg::getEngine)
        .map(EngineCfg::getGlobalListeners)
        .ifPresent(listeners -> validateListenersConfig(tenantId, listeners));
  }

  private void validatePerTenantDataConfig(final String tenantId, final DataCfg dataCfg) {
    final var snapshotPeriod = dataCfg.getSnapshotPeriod();
    if (snapshotPeriod.isNegative() || snapshotPeriod.minus(MINIMUM_SNAPSHOT_PERIOD).isNegative()) {
      throw new IllegalArgumentException(
          tenantScopedMessage(tenantId, String.format(SNAPSHOT_PERIOD_ERROR_MSG, snapshotPeriod)));
    }

    validateBackupCfg(tenantId, dataCfg.getBackup());
  }

  private void validateBatchOperationsConfig(
      final String tenantId, final BatchOperationCfg config) {
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
          "[physical tenant '{}'] Setting experimental.engine.batchOperations.chunkSize should be "
              + "lower than {}, but was {}",
          tenantId,
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
          tenantScopedMessage(
              tenantId, "Invalid BatchOperations configuration: " + String.join(", ", errors)),
          null);
    }
  }

  private void validateBackupCfg(final String tenantId, final BackupCfg backup) {
    if (backup.isRequired() && backup.getStore() == BackupCfg.BackupStoreType.NONE) {
      throw new InvalidConfigurationException(
          tenantScopedMessage(
              tenantId,
              "Backup is required but no backup store is configured. "
                  + "Set zeebe.broker.data.backup.store to one of: S3, GCS, AZURE, FILESYSTEM"),
          null);
    }

    try {
      switch (backup.getStore()) {
        case NONE ->
            LOG.warn(
                "[physical tenant '{}'] No backup store is configured. Backups will not be taken",
                tenantId);
        case S3 ->
            S3BackupStore.validateConfig(
                S3BackupStoreConfig.toStoreConfig(
                    backup.getS3(), backup.getReadTimeout(), backup.getWriteTimeout()));
        case GCS ->
            GcsBackupStore.validateConfig(
                GcsBackupStoreConfig.toStoreConfig(
                    backup.getGcs(), backup.getReadTimeout(), backup.getWriteTimeout()));
        case AZURE ->
            AzureBackupStore.validateConfig(
                AzureBackupStoreConfig.toStoreConfig(
                    backup.getAzure(), backup.getReadTimeout(), backup.getWriteTimeout()));
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
          tenantScopedMessage(
              tenantId, "Failed configuring backup store %s".formatted(backup.getStore())),
          e);
    }

    validateBackupSchedulerConfig(tenantId, backup);
  }

  /**
   * Validates the tenant's fixed-partition scheme against the root {@link BrokerCfg}: {@code
   * cluster.partitioning} (the scheme + fixed member list) and {@code cluster.replication-factor}
   * are both in the physical-tenant override deny-list, so they are shared across every tenant by
   * policy and are read from the root config here; only {@code cluster.partition-count} is
   * overridable per tenant, so the tenant's own partition count is used.
   */
  private void validateFixedPartitioningScheme(
      final String tenantId, final ClusterCfg tenantCluster) {
    final var rootCluster = rootBrokerCfg.getCluster();
    final var partitioning = rootBrokerCfg.getExperimental().getPartitioning();
    final var partitions = partitioning.getFixed();
    final var replicationFactor = rootCluster.getReplicationFactor();
    final var partitionsCount = tenantCluster.getPartitionsCount();

    final var partitionMembers = new HashMap<Integer, Set<Integer>>();
    for (final var partition : partitions) {
      if (!tenantId.equals(partition.getPhysicalTenantId())) {
        continue;
      }
      final var members =
          validateFixedPartitionMembers(
              tenantId,
              rootCluster,
              partitionsCount,
              partition,
              rootCluster.getRaft().isEnablePriorityElection());
      partitionMembers.put(partition.getPartitionId(), members);
    }

    for (int partitionId = 1; partitionId <= partitionsCount; partitionId++) {
      final var members = partitionMembers.getOrDefault(partitionId, Collections.emptySet());
      if (members.size() < replicationFactor) {
        throw new IllegalArgumentException(
            tenantScopedMessage(
                tenantId,
                String.format(
                    "Expected fixed partition scheme to define configurations for all partitions such "
                        + "that they have %d replicas, but partition %d has %d configured replicas: %s",
                    replicationFactor, partitionId, members.size(), members)));
      }
    }
  }

  private Set<Integer> validateFixedPartitionMembers(
      final String tenantId,
      final ClusterCfg cluster,
      final int partitionsCount,
      final FixedPartitionCfg partitionConfig,
      final boolean isPriorityElectionEnabled) {
    final var members = new HashSet<Integer>();
    final var clusterSize = cluster.getClusterSize();
    final var partitionId = partitionConfig.getPartitionId();

    if (partitionId < 1 || partitionId > partitionsCount) {
      throw new IllegalArgumentException(
          tenantScopedMessage(
              tenantId,
              String.format(
                  "Expected fixed partition scheme to define entries with a valid partitionId between 1"
                      + " and %d, but %d was given",
                  partitionsCount, partitionId)));
    }

    final var observedPriorities = new HashSet<Integer>();
    for (final var node : partitionConfig.getNodes()) {
      final var nodeId = node.getNodeId();
      if (nodeId < 0 || nodeId >= clusterSize) {
        throw new IllegalArgumentException(
            tenantScopedMessage(
                tenantId,
                String.format(
                    "Expected fixed partition scheme for partition %d to define nodes with a nodeId "
                        + "between 0 and %d, but it was %d",
                    partitionId, clusterSize - 1, nodeId)));
      }

      if (isPriorityElectionEnabled && !observedPriorities.add(node.getPriority())) {
        throw new IllegalArgumentException(
            tenantScopedMessage(
                tenantId,
                String.format(
                    "Expected each node for a partition %d to have a different priority, but at least "
                        + "two of them have the same priorities: %s",
                    partitionId, partitionConfig.getNodes())));
      }

      members.add(nodeId);
    }

    return members;
  }

  private void validateInitializationConfigForTenant(
      final String physicalTenantId, final EngineSecurityConfig securityConfig) {
    final IdentifierValidator identifierValidator =
        new IdentifierValidator(
            securityConfig.getIdValidationPattern(), securityConfig.getGroupIdValidationPattern());
    final AuthorizationConfigurer authorizationConfigurer =
        new AuthorizationConfigurer(new AuthorizationValidator(identifierValidator));
    final TenantConfigurer tenantConfigurer =
        new TenantConfigurer(new TenantValidator(identifierValidator));
    final GroupConfigurer groupConfigurer =
        new GroupConfigurer(new GroupValidator(identifierValidator));
    final var roleConfigurer = new RoleConfigurer(new RoleValidator(identifierValidator));

    final Either<List<String>, List<AuthorizationRecord>> configuredAuthorizations =
        authorizationConfigurer.configureEntities(
            securityConfig.getInitialization().getAuthorizations());
    final Either<List<String>, List<TenantRecord>> configuredTenants =
        tenantConfigurer.configureEntities(securityConfig.getInitialization().getTenants());
    final Either<List<String>, List<GroupRecord>> configuredGroups =
        groupConfigurer.configureEntities(securityConfig.getInitialization().getGroups());
    final Either<List<String>, List<RoleRecord>> configuredRoles =
        roleConfigurer.configureEntities(securityConfig.getInitialization().getRoles());

    // TODO: after adding more entity types, change this, so it accounts for all violations all
    //   together.
    configuredAuthorizations.ifLeft(
        (violations) -> {
          throw new IdentityInitializationException(
              "Cannot initialize configured authorizations for tenant '%s': %n- %s"
                  .formatted(
                      physicalTenantId, String.join(System.lineSeparator() + "- ", violations)));
        });
    configuredTenants.ifLeft(
        (violations) -> {
          throw new IdentityInitializationException(
              "Cannot initialize configured tenants for tenant '%s': %n- %s"
                  .formatted(
                      physicalTenantId, String.join(System.lineSeparator() + "- ", violations)));
        });
    configuredRoles.ifLeft(
        (violations) -> {
          throw new IdentityInitializationException(
              "Cannot initialize configured roles for tenant '%s': %n- %s"
                  .formatted(
                      physicalTenantId, String.join(System.lineSeparator() + "- ", violations)));
        });

    configuredGroups.ifLeft(
        (violations) -> {
          throw new IdentityInitializationException(
              "Cannot initialize configured groups for tenant '%s': %n- %s"
                  .formatted(
                      physicalTenantId, String.join(System.lineSeparator() + "- ", violations)));
        });
  }

  private void validateListenersConfig(final String tenantId, final GlobalListenersCfg listeners) {
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
            "[physical tenant '{}'] Invalid retries for global listener: '{}'; listener will be "
                + "ignored [{}.retries]",
            tenantId,
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
            "[physical tenant '{}'] Missing id for global listener; listener will be ignored "
                + "[{}.type]",
            tenantId,
            propertyPrefix);
        continue;
      }

      // Check if type is present
      if (globalListenerValidator.typeProvided(listenerRecord).isLeft()) {
        LOG.warn(
            "[physical tenant '{}'] Missing job type for global listener; listener will be "
                + "ignored [{}.type]",
            tenantId,
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
                          "[physical tenant '{}'] Invalid event type will be ignored: '{}' "
                              + "[{}.eventTypes]",
                          tenantId,
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
                          "[physical tenant '{}'] Extra event type defined alongside '{}' will be "
                              + "ignored: '{}' [{}.eventTypes]",
                          tenantId,
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
                  "[physical tenant '{}'] Duplicated event type will be considered only once: "
                      + "'{}' [{}.eventTypes]",
                  tenantId,
                  eventType,
                  propertyPrefix);
            } else {
              uniqueEventTypes.add(eventType);
            }
          });

      // Check if valid event types have been provided
      if (uniqueEventTypes.isEmpty()) {
        LOG.warn(
            "[physical tenant '{}'] Missing event types for global listener; listener will be "
                + "ignored [{}.eventTypes]",
            tenantId,
            propertyPrefix);
        continue;
      }

      listenerCfg.setEventTypes(uniqueEventTypes);
      validListeners.add(listenerCfg);
    }

    listeners.setUserTask(validListeners);
  }

  private void validateBackupSchedulerConfig(
      final String tenantId, final BackupCfg backupSchedulerCfg) {
    if (!backupSchedulerCfg.isContinuous() || !backupSchedulerCfg.isRequired()) {
      return;
    }

    // will throw IllegalArgumentException if schedule is invalid
    final var schedule = backupSchedulerCfg.getSchedule();
    if (backupSchedulerCfg.isRequired() && schedule instanceof NoneSchedule) {
      throw new IllegalArgumentException(
          tenantScopedMessage(tenantId, "Backup schedule is mandatory, none provided."));
    }
    // will throw IllegalArgumentException if schedule is invalid
    Objects.requireNonNull(backupSchedulerCfg.getRetention().getCleanupSchedule());
    final var retentionSchedule = backupSchedulerCfg.getRetention().getCleanupSchedule();
    if (backupSchedulerCfg.isRequired() && retentionSchedule instanceof NoneSchedule) {
      throw new IllegalArgumentException(
          tenantScopedMessage(tenantId, "Backup retention schedule is mandatory, none provided."));
    }
  }
}

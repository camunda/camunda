/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.application.MainSupport;
import io.camunda.application.Profile;
import io.camunda.application.commons.configuration.UnifiedConfigurationModule;
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration;
import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.RestoreProperties;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.db.rdbms.write.domain.ExporterPositionModel;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.TenantRestoreArguments;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryProvider;
import io.camunda.zeebe.restore.ClusterRestore.PhysicalTenantRestoreTarget;
import io.camunda.zeebe.restore.PhysicalTenantRestoreConfigurations.PhysicalTenantBrokerConfigurations;
import io.camunda.zeebe.restore.PhysicalTenantRestoreConfigurations.PhysicalTenantRestoreEnvironments;
import io.camunda.zeebe.restore.validation.RestoreValidator;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.function.IntFunction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"io.camunda.zeebe.restore"})
@ConfigurationPropertiesScan(basePackages = {"io.camunda.zeebe.restore"})
@Import(
    value = {
      UnifiedConfigurationModule.class,
      WorkingDirectoryConfiguration.class,
      // RDBMS Configuration - conditional on secondary storage type being RDBMS.
      // When active, provides ExporterPositionMapper for RDBMS-aware restore.
      RdbmsConfiguration.class,
    })
@NullMarked
public class RestoreApp implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(RestoreApp.class);

  private final BrokerCfg configuration;
  private final PhysicalTenantBrokerConfigurations physicalTenantConfigurations;
  private final PhysicalTenantRestoreEnvironments physicalTenantRestoreEnvironments;
  private final PhysicalTenantBackupStores backupStores;
  private final RestoreArguments arguments;

  private final Map<String, ExporterPositionMapper> exporterPositionMappers;

  private final RestoreProperties restoreConfiguration;
  private final MeterRegistry meterRegistry;
  private final PostRestoreAction postRestoreAction;
  private final PreRestoreAction preRestoreAction;
  private final Camunda camunda;

  @Autowired
  public RestoreApp(
      final Camunda camunda,
      final BrokerBasedProperties configuration,
      final PhysicalTenantBrokerConfigurations physicalTenantConfigurations,
      final PhysicalTenantRestoreEnvironments physicalTenantRestoreEnvironments,
      final PhysicalTenantBackupStores backupStores,
      final RestoreArguments arguments,
      @Nullable @Autowired(required = false)
          final Map<String, RdbmsMapperBundle> rdbmsMapperBundleMap,
      final RestoreProperties restoreConfiguration,
      final MeterRegistry meterRegistry,
      final NodeIdProvider nodeIdProvider,
      // DataDirectoryProvider is not used directly here but is needed to ensure the directory is
      // set up already especially when using dynamic node ids.
      final DataDirectoryProvider dataDirectoryProvider,
      final PostRestoreAction postRestoreAction,
      final PreRestoreAction preRestoreAction) {
    this.camunda = camunda;
    this.configuration = configuration;
    this.physicalTenantConfigurations = physicalTenantConfigurations;
    this.physicalTenantRestoreEnvironments = physicalTenantRestoreEnvironments;
    this.backupStores = backupStores;
    this.arguments = arguments;
    exporterPositionMappers =
        usesRdbms()
            ? exporterPositionMappers(
                physicalTenantConfigurations.physicalTenantIds(), rdbmsMapperBundleMap)
            : Map.of();
    this.restoreConfiguration = restoreConfiguration;
    this.meterRegistry = meterRegistry;
    this.postRestoreAction = postRestoreAction;
    this.preRestoreAction = preRestoreAction;
    configuration.getCluster().setNodeId(nodeIdProvider.currentNodeInstance().id());
  }

  public static void main(final String[] args) {
    MainSupport.setDefaultGlobalConfiguration();

    final var application =
        MainSupport.createDefaultApplicationBuilder()
            .web(WebApplicationType.NONE)
            .sources(RestoreApp.class)
            .profiles(Profile.RESTORE.getId())
            .build();

    final String activeProfiles = System.getProperty("spring.profiles.active");
    final String springProfilesActive = System.getenv("SPRING_PROFILES_ACTIVE");
    if (!Objects.equals(activeProfiles, Profile.RESTORE.getId())
        || (springProfilesActive != null
            && !springProfilesActive.equals(Profile.RESTORE.getId()))) {
      LOG.warn(
          "Additional profiles besides restore are set, which is not supported: {}. "
              + "The application will run only with the restore profile.",
          activeProfiles);
      System.setProperty("spring.profiles.active", Profile.RESTORE.getId());
    }

    application.run(args);
  }

  @Override
  public void run(final ApplicationArguments args) throws Exception {
    final var selectionPerTenant =
        arguments.selectionPerPhysicalTenant(physicalTenantConfigurations.physicalTenantIds());
    validateParameters(selectionPerTenant);

    final var restoreId = getRestoreId(selectionPerTenant);
    final var preRestoreActionResult =
        preRestoreAction.beforeRestore(restoreId, configuration.getCluster().getNodeId());

    final var clusterRestore = new ClusterRestore(configuration, restoreTargets(), meterRegistry);

    final PostRestoreActionContext postRestoreActionContext;
    if (!preRestoreActionResult.skipRestore()) {
      restore(clusterRestore, selectionPerTenant);
      postRestoreActionContext =
          new PostRestoreActionContext(
              restoreId,
              configuration.getCluster().getNodeId(),
              false,
              selectionPerTenant.keySet());
    } else {
      LOG.info("Skipping restore: {}", preRestoreActionResult.message());
      postRestoreActionContext =
          new PostRestoreActionContext(
              restoreId, configuration.getCluster().getNodeId(), true, selectionPerTenant.keySet());
    }
    // We have to run post restore anyway even if post restore action decided to skip restore,
    // because in some cases, like when using dynamic node ids, we need to wait for other nodes to
    // complete restore.
    postRestoreAction.restored(postRestoreActionContext);
  }

  private static Map<String, ExporterPositionMapper> exporterPositionMappers(
      final Set<String> physicalTenantIds,
      final @Nullable Map<String, RdbmsMapperBundle> rdbmsMapperBundleMap) {
    final Map<String, ExporterPositionMapper> mappers = new LinkedHashMap<>();
    for (final var physicalTenantId : physicalTenantIds) {
      final var bundle =
          rdbmsMapperBundleMap == null ? null : rdbmsMapperBundleMap.get(physicalTenantId);
      if (bundle == null) {
        throw new IllegalStateException(
            "RDBMS-aware restore requires an ExporterPositionMapper for physical tenant '%s'"
                .formatted(physicalTenantId));
      }
      mappers.put(physicalTenantId, bundle.exporterPositionMapper());
    }
    return Map.copyOf(mappers);
  }

  private boolean usesRdbms() {
    return camunda.getData().getSecondaryStorage().getType() == SecondaryStorageType.rdbms;
  }

  /** What each physical tenant is restored from. */
  private Map<String, PhysicalTenantRestoreTarget> restoreTargets() {
    final Map<String, PhysicalTenantRestoreTarget> targets = new LinkedHashMap<>();
    physicalTenantConfigurations
        .configurations()
        .forEach(
            (physicalTenantId, tenantConfiguration) ->
                targets.put(
                    physicalTenantId,
                    new PhysicalTenantRestoreTarget(
                        tenantConfiguration,
                        () -> backupStores.forPhysicalTenant(physicalTenantId),
                        exporterPositionMappers.get(physicalTenantId))));
    return targets;
  }

  private void restore(
      final ClusterRestore clusterRestore, final Map<String, RestoreSelection> selectionPerTenant)
      throws IOException, ExecutionException, InterruptedException {
    LOG.info(
        "Starting to restore physical tenants {} with the following configuration: {}",
        selectionPerTenant,
        restoreConfiguration);
    clusterRestore.restore(
        selectionPerTenant,
        arguments.targetDataPolicy(),
        restoreConfiguration.validateConfig(),
        restoreConfiguration.ignoreFilesInTarget());
    LOG.info("Successfully restored physical tenants {}", selectionPerTenant.keySet());
  }

  /**
   * Validates the restore against every targeted physical tenant, each with its own selection,
   * database type, continuous-backup setting, partition count, backup store and exported positions.
   * All of them are validated before any data is touched: a restore that cannot succeed for one
   * tenant must not leave the others restored, since a run's data is deleted on failure and the
   * cluster would otherwise come up with a partial set of tenants.
   */
  private void validateParameters(final Map<String, RestoreSelection> selectionPerTenant) {
    selectionPerTenant.forEach(
        (physicalTenantId, selection) -> {
          final var environment =
              physicalTenantRestoreEnvironments.forPhysicalTenant(physicalTenantId);
          final var restoreRequest =
              new RestoreRequest(
                  physicalTenantId,
                  new TenantRestoreArguments(
                      selection.toRestoreParameters(),
                      environment.databaseType(),
                      environment.continuousBackups()),
                  false);
          final var validator =
              new RestoreValidator(
                  physicalTenantConfigurations
                      .forPhysicalTenant(physicalTenantId)
                      .getCluster()
                      .getPartitionsCount(),
                  backupStores.forPhysicalTenant(physicalTenantId),
                  exportedPositionSupplier(physicalTenantId));
          final var result = validator.validate(restoreRequest);
          if (result.isLeft()) {
            throw (RuntimeException) result.getLeft();
          }
        });
  }

  private @Nullable IntFunction<@Nullable Long> exportedPositionSupplier(
      final String physicalTenantId) {
    final var mapper = exporterPositionMappers.get(physicalTenantId);
    if (mapper == null) {
      return null;
    }
    return partition ->
        Optional.ofNullable(mapper.findOne(partition))
            .map(ExporterPositionModel::lastExportedPosition)
            .orElse(null);
  }

  /**
   * Identifies this restore so the pre- and post-restore actions can recognize the same request
   * across nodes — with dynamic node ids they wait for each other under this id.
   *
   * <p>Derived from the whole selection, ordered, so every node computes the same id from the same
   * arguments, and two runs that restore different tenants or different backups do not share one.
   * {@code RestoreSelection} is a record over a {@code List<Long>} and two {@code Instant}s, all of
   * whose hash codes are specified, so the value is stable across JVMs.
   */
  @VisibleForTesting
  static String getRestoreId(final Map<String, RestoreSelection> selectionPerTenant) {
    final var ordered = new TreeMap<>(selectionPerTenant);
    final var tenants = String.join("+", ordered.keySet());
    final var namesNothing =
        selectionPerTenant.values().stream()
            .noneMatch(selection -> selection.hasBackupIds() || selection.hasTimeRange());
    // The tenants are in the id even when no backup is named — an RDBMS restore, whose point comes
    // from the exported positions. Without them every such run shares one id, and the S3
    // coordination would see a completed restore of one tenant as covering a later run for a
    // different one: that run is skipped, and the directories already on disk let the post-restore
    // check pass while the requested restore never happened.
    return namesNothing ? "latest-" + tenants : tenants + "-" + ordered.hashCode();
  }

  public record PreRestoreActionResult(boolean skipRestore, String message) {}

  /**
   * @param restoredPhysicalTenantIds the physical tenants this run restored. Post-restore
   *     validation checks these and no others: a run restoring one tenant of a multi-tenant cluster
   *     leaves the rest as they were, and demanding restored data for them would fail every partial
   *     restore.
   */
  public record PostRestoreActionContext(
      String restoreId,
      int nodeId,
      boolean skippedRestore,
      Set<String> restoredPhysicalTenantIds) {}

  public interface PreRestoreAction {
    PreRestoreActionResult beforeRestore(final String restoreId, int nodeId)
        throws InterruptedException;
  }

  public interface PostRestoreAction {
    void restored(final PostRestoreActionContext context) throws InterruptedException;
  }
}

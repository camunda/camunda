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
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.RestorePropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.RestoreProperties;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
      // Unified Configuration classes
      UnifiedConfigurationHelper.class,
      UnifiedConfiguration.class,
      BrokerBasedPropertiesOverride.class,
      RestorePropertiesOverride.class,
      WorkingDirectoryConfiguration.class
    })
public class RestoreApp implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(RestoreApp.class);
  private final BrokerCfg configuration;
  private final BackupStore backupStore;

  @Value("${backupId:#{null}}")
  // Parsed from commandline Eg:-`--backupId=100` (optional, mutually exclusive with from/to)
  private long[] backupId;

  @Value("${from:#{null}}")
  // Parsed from commandline Eg:-`--from=2024-01-01T10:00:00Z` (optional, requires --to)
  private Instant from;

  @Value("${to:#{null}}")
  // Parsed from commandline Eg:-`--to=2024-01-01T12:00:00Z` (optional, requires --from)
  private Instant to;

  private final RestoreProperties restoreConfiguration;
  private final MeterRegistry meterRegistry;
  private final PostRestoreAction postRestoreAction;
  private final PreRestoreAction preRestoreAction;

  @Autowired
  public RestoreApp(
      final BrokerBasedProperties configuration,
      final BackupStore backupStore,
      final RestoreProperties restoreConfiguration,
      final MeterRegistry meterRegistry,
      final NodeIdProvider nodeIdProvider,
      // DataDirectoryProvider is not used directly here but is needed to ensure the directory is
      // set up already especially when using dynamic node ids.
      final DataDirectoryProvider dataDirectoryProvider,
      final PostRestoreAction postRestoreAction,
      final PreRestoreAction preRestoreAction) {
    this.configuration = configuration;
    this.backupStore = backupStore;
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
  public void run(final ApplicationArguments args)
      throws IOException, ExecutionException, InterruptedException {
    validateParameters();

    final var restoreManager = new RestoreManager(configuration, backupStore, meterRegistry);

    final var restoreId = getRestoreId();
    final var preRestoreActionResult =
        preRestoreAction.beforeRestore(restoreId, configuration.getCluster().getNodeId());

    if (preRestoreActionResult.proceed()) {
      if (backupId != null) {
        LOG.info(
            "Starting to restore from backup {} with the following configuration: {}",
            backupId,
            restoreConfiguration);
        restoreManager.restore(
            backupId,
            restoreConfiguration.validateConfig(),
            restoreConfiguration.ignoreFilesInTarget());
        LOG.info("Successfully restored broker from backup {}", backupId);
      } else {
        LOG.info(
            "Starting to restore from backups in time range [{}, {}] with the following configuration: {}",
            from,
            to,
            restoreConfiguration);
        restoreManager.restore(
            from,
            to,
            restoreConfiguration.validateConfig(),
            restoreConfiguration.ignoreFilesInTarget());
        LOG.info("Successfully restored broker from backups in time range [{}, {}]", from, to);
      }
    } else {
      LOG.info("Skipping restore: {}", preRestoreActionResult.message());
    }

    // We have to run post restore anyway even if post restore action decided to skip restore,
    // because in some cases, like when using dynamic node ids, we need to wait for other nodes to
    // complete restore.
    postRestoreAction.restored(restoreId, configuration.getCluster().getNodeId());
  }

  private void validateParameters() {
    final boolean hasBackupId = hasBackupId();
    final boolean hasTimeRange = hasTimeRange();

    if (!hasBackupId && !hasTimeRange) {
      throw new IllegalArgumentException(
          "Either --backupId or both --from and --to parameters must be provided");
    }

    if (hasBackupId && hasTimeRange) {
      throw new IllegalArgumentException(
          "Cannot specify both --backupId and --from/--to parameters. Choose one approach.");
    }

    if (from != null && to == null) {
      throw new IllegalArgumentException("--from parameter requires --to parameter");
    }

    if (to != null && from == null) {
      throw new IllegalArgumentException("--to parameter requires --from parameter");
    }

    if (hasTimeRange && from.isAfter(to)) {
      throw new IllegalArgumentException(
          "Invalid time range: --from (%s) must be before --to (%s)".formatted(from, to));
    }
  }

  private boolean hasTimeRange() {
    return from != null && to != null;
  }

  private boolean hasBackupId() {
    return backupId != null && backupId.length > 0;
  }

  private long getRestoreId() {
    if (hasBackupId()) {
      return Arrays.hashCode(backupId);
    } else if (hasTimeRange()) {
      return Objects.hash(from, to);
    } else {
      throw new IllegalStateException("No valid restore parameters provided");
    }
  }

  public record PreRestoreActionResult(boolean proceed, String message) {}

  public interface PreRestoreAction {
    PreRestoreActionResult beforeRestore(final long restoreId, int nodeId)
        throws InterruptedException;
  }

  public interface PostRestoreAction {
    void restored(final long restoreId, int nodeId) throws InterruptedException;
  }
}

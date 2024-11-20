/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.process.adapter.Adapter;
import io.camunda.migration.process.adapter.es.ElasticsearchAdapter;
import io.camunda.migration.process.adapter.os.OpensearchAdapter;
import io.camunda.migration.process.config.ProcessMigrationProperties;
import io.camunda.migration.process.util.MigrationUtil;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.entities.operate.ImportPositionEntity;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component("process-migrator")
@EnableConfigurationProperties(ProcessMigrationProperties.class)
public class MigrationRunner implements Migrator {

  private static final Logger LOG = LoggerFactory.getLogger(MigrationRunner.class);

  private static final String ELASTICSEARCH = "elasticsearch";
  private final Adapter adapter;
  private final ProcessMigrationProperties properties;
  private ScheduledFuture<?> countdownTask;

  public MigrationRunner(
      final ProcessMigrationProperties properties, final ConnectConfiguration connect) {
    this.properties = properties;
    adapter =
        connect.getType().equals(ELASTICSEARCH)
            ? new ElasticsearchAdapter(properties, connect)
            : new OpensearchAdapter(properties, connect);
  }

  @Override
  public void run() {
    final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    LOG.info("Process Migration started");
    try {
      String lastMigratedProcessDefinitionKey = adapter.readLastMigratedEntity();
      List<ProcessEntity> items = adapter.nextBatch(lastMigratedProcessDefinitionKey);
      while (shouldContinue(items)) {
        if (!items.isEmpty()) {
          lastMigratedProcessDefinitionKey = migrateBatch(items);
        }
        scheduleNextBatch(scheduler);
        items = adapter.nextBatch(lastMigratedProcessDefinitionKey);
      }
    } catch (final Exception e) {
      terminate(scheduler);
      throw e;
    }
    terminate(scheduler);
    LOG.info("Process Migration completed");
  }

  private boolean shouldContinue(final List<ProcessEntity> processes) {
    if (!processes.isEmpty()) {
      return true;
    }
    return countdownTask == null || !countdownTask.isDone();
  }

  private String migrateBatch(final List<ProcessEntity> processes) {
    final String lastMigratedProcessDefinitionKey =
        adapter.migrate(MigrationUtil.extractBatchData(processes));
    adapter.writeLastMigratedEntity(lastMigratedProcessDefinitionKey);
    return lastMigratedProcessDefinitionKey;
  }

  private void scheduleNextBatch(final ScheduledExecutorService scheduler) {
    try {
      if (countdownTask == null && isImporterFinished()) {
        startCountdown(scheduler);
      }
      scheduler.schedule(() -> {}, adapter.getMinDelay().toSeconds(), TimeUnit.SECONDS).get();
    } catch (final ExecutionException | InterruptedException ex) {
      Thread.currentThread().interrupt();
      LOG.error("Schedule interrupted", ex);
    }
  }

  private void startCountdown(final ScheduledExecutorService scheduler) {
    LOG.info(
        "Importer finished, migration will keep running for {}",
        properties.getPostImporterTimeout());
    countdownTask =
        scheduler.schedule(
            () -> {}, properties.getPostImporterTimeout().getSeconds(), TimeUnit.SECONDS);
  }

  private boolean isImporterFinished() {
    final Set<ImportPositionEntity> importPositions;
    try {
      importPositions = adapter.readImportPosition();
      return !importPositions.isEmpty()
          && importPositions.stream().allMatch(ImportPositionEntity::getCompleted);
    } catch (final MigrationException e) {
      LOG.error("Failed to read import position", e);
      return false;
    }
  }

  private void terminate(final ScheduledExecutorService scheduler) {
    scheduler.shutdown();
    try {
      adapter.close();
    } catch (final IOException e) {
      LOG.error("Failed to close adapter", e);
    }
  }
}

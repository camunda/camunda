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
import io.camunda.migration.process.util.XMLUtil;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("process-migration")
@EnableConfigurationProperties(ProcessMigrationProperties.class)
public class MigrationRunner implements Migrator {

  private static final String ELASTICSEARCH = "elasticsearch";
  private static final Logger LOG = LoggerFactory.getLogger(MigrationRunner.class);
  private static final Long INITIAL_BACKOFF = 1000L;
  private static final int MAX_RETRIES = 3;
  final AtomicLong backoff = new AtomicLong(INITIAL_BACKOFF);
  final AtomicInteger retries = new AtomicInteger();
  private final Adapter adapter;
  private final XMLUtil xmlParser;

  public MigrationRunner(final ProcessMigrationProperties properties) {
    adapter =
        properties.getConnect().getType().equals(ELASTICSEARCH)
            ? new ElasticsearchAdapter(
                properties, new ElasticsearchConnector(properties.getConnect()))
            : new OpensearchAdapter(properties, new OpensearchConnector(properties.getConnect()));

    try {
      xmlParser = new XMLUtil();
    } catch (final MigrationException e) {
      throw new MigrationException("Failed to initialize XML parser", e);
    }
  }

  @Override
  public void run() {
    final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    LOG.info("Process Migration started");
    String lastMigratedProcessDefinitionKey = adapter.readLastMigratedEntity();

    List<ProcessEntity> items = adapter.nextBatch(lastMigratedProcessDefinitionKey);
    while (!items.isEmpty()) {
      lastMigratedProcessDefinitionKey = migrateBatch(items);
      final boolean retry = lastMigratedProcessDefinitionKey == null;
      scheduleNextBatch(scheduler, retry);
      if (retries.get() >= MAX_RETRIES) {
        break;
      }
      items = retry ? items : adapter.nextBatch(lastMigratedProcessDefinitionKey);
    }
    terminate(scheduler);
    LOG.info("Process Migration completed");
  }

  protected String migrateBatch(final List<ProcessEntity> processes) {
    String lastMigratedProcessDefinitionKey = null;
    try {
      lastMigratedProcessDefinitionKey = adapter.migrate(extractBatchData(processes));
      resetBackoff();
      adapter.writeLastMigratedEntity(lastMigratedProcessDefinitionKey);
    } catch (final MigrationException me) {
      LOG.error(me.getMessage());
    }
    return lastMigratedProcessDefinitionKey;
  }

  private void scheduleNextBatch(final ScheduledExecutorService scheduler, final boolean retry) {
    try {
      scheduler.schedule(() -> {}, backoff.get(), TimeUnit.MILLISECONDS).get();
      if (retry) {
        backoff.updateAndGet(v -> v * 2);
        retries.getAndIncrement();
      }
    } catch (final ExecutionException | InterruptedException ex) {
      Thread.currentThread().interrupt();
      LOG.error("Schedule interrupted", ex);
    }
  }

  private void resetBackoff() {
    backoff.set(INITIAL_BACKOFF);
    retries.set(0);
  }

  private List<ProcessEntity> extractBatchData(final List<ProcessEntity> processDefinitions) {
    return processDefinitions.stream().map(this::map).toList();
  }

  private ProcessEntity map(final ProcessEntity entity) {
    final ProcessEntity processEntity = new ProcessEntity();
    processEntity.setId(entity.getId());
    processEntity.setVersion(entity.getVersion());

    final Optional<ProcessEntity> diagramData =
        xmlParser.extractDiagramData(
            entity.getBpmnXml().getBytes(StandardCharsets.UTF_8), entity.getBpmnProcessId());
    diagramData.ifPresent(
        parsedEntity ->
            processEntity
                .setFormId(parsedEntity.getFormId())
                .setIsPublic(parsedEntity.getIsPublic()));
    return processEntity;
  }

  private void terminate(final ScheduledExecutorService scheduler) {
    scheduler.shutdown();
    try {
      adapter.close();
    } catch (final IOException e) {
      LOG.error("Failed to close adapter", e);
    }

    if (retries.get() >= MAX_RETRIES) {
      throw new MigrationException("Process migration failed, retries exceeded");
    }
  }
}

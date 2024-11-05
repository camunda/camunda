/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.tasklist;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.tasklist.adapter.Adapter;
import io.camunda.migration.tasklist.adapter.es.ElasticsearchAdapter;
import io.camunda.migration.tasklist.adapter.os.OpensearchAdapter;
import io.camunda.migration.tasklist.util.XMLUtil;
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
@Profile("tasklist-migration")
@EnableConfigurationProperties(TasklistMigrationProperties.class)
public class MigrationRunner implements Migrator {

  private static final String ELASTICSEARCH = "elasticsearch";
  private static final Logger LOG = LoggerFactory.getLogger(MigrationRunner.class);
  private static final Long INITIAL_BACKOFF = 1000L;
  private static final int MAX_RETRIES = 3;
  private final Adapter adapter;
  private final XMLUtil xmlParser;

  public MigrationRunner(final TasklistMigrationProperties properties) {
    adapter =
        properties.getConnect().getType().equals(ELASTICSEARCH)
            ? new ElasticsearchAdapter(
                properties, new ElasticsearchConnector(properties.getConnect()))
            : new OpensearchAdapter(properties, new OpensearchConnector(properties.getConnect()));

    try {
      xmlParser = new XMLUtil();
    } catch (final MigrationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void run() {
    final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    final AtomicLong backoff = new AtomicLong(INITIAL_BACKOFF);
    final AtomicInteger retries = new AtomicInteger();

    LOG.info("Tasklist Migration started");
    List<io.camunda.tasklist.entities.ProcessEntity> items = adapter.nextBatch();
    while (!items.isEmpty()) {
      try {
        if (!adapter.migrate(processBatch(items))) {
          throw new MigrationException(
              "Migration contained errors, backing off for %s ms".formatted(backoff));
        } else {
          backoff.set(INITIAL_BACKOFF);
          retries.set(0);
        }
      } catch (final MigrationException e) {
        if (retries.get() >= MAX_RETRIES) {
          LOG.error("Migration failed after {} retries", retries);
          throw new MigrationException("Migration exceeded maximum retries");
        }
        LOG.error(e.getMessage());
        try {
          scheduler.schedule(() -> {}, backoff.get(), TimeUnit.MILLISECONDS).get();
          backoff.updateAndGet(v -> v * 2);
          retries.getAndIncrement();
        } catch (final Exception ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Migration interrupted", ie);
        }
      }

      // Add a delay between each batch processing
      try {
        scheduler.schedule(() -> {}, 1, TimeUnit.SECONDS).get();
      } catch (final InterruptedException | ExecutionException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Delay interrupted", e);
      }

      items = adapter.nextBatch();
    }

    scheduler.shutdown();
    try {
      adapter.close();
    } catch (final IOException e) {
      LOG.error("Failed to close adapter", e);
    }
    LOG.info("Tasklist Migration completed");
  }

  private List<ProcessEntity> processBatch(
      final List<io.camunda.tasklist.entities.ProcessEntity> tasklistProcesses) {
    return tasklistProcesses.stream().map(this::map).toList();
  }

  private ProcessEntity map(final io.camunda.tasklist.entities.ProcessEntity entity) {
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
}

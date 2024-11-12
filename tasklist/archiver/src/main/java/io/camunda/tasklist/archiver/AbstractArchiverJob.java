/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.BackoffIdleStrategy;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public abstract class AbstractArchiverJob implements Runnable {

  protected static final String NOTHING_TO_ARCHIVE = "NothingToArchive";
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractArchiverJob.class);

  @Autowired
  @Qualifier("tasklistArchiverThreadPoolExecutor")
  protected ThreadPoolTaskScheduler archiverExecutor;

  @Autowired protected ArchiverUtil archiverUtil;

  private final BackoffIdleStrategy idleStrategy;
  private final BackoffIdleStrategy errorStrategy;

  private boolean shutdown = false;
  private final List<Integer> partitionIds;

  @Autowired private TasklistProperties tasklistProperties;

  public AbstractArchiverJob(final List<Integer> partitionIds) {
    this.partitionIds = partitionIds;
    this.idleStrategy = new BackoffIdleStrategy(2_000, 1.2f, 60_000);
    this.errorStrategy = new BackoffIdleStrategy(100, 1.2f, 10_000);
  }

  protected abstract CompletableFuture<Map.Entry<String, Integer>> archiveBatch(
      final ArchiveBatch archiveBatch);

  protected abstract CompletableFuture<ArchiveBatch> getNextBatch();

  @Override
  public void run() {
    archiveNextBatch()
        .thenApply(
            (Map.Entry<String, Integer> map) -> {
              errorStrategy.reset();
              if (map.getValue() >= tasklistProperties.getArchiver().getRolloverBatchSize()) {
                idleStrategy.reset();
              } else {
                idleStrategy.idle();
              }

              final var delay =
                  Math.max(
                      tasklistProperties.getArchiver().getDelayBetweenRuns(),
                      idleStrategy.idleTime());

              return delay;
            })
        .exceptionally(
            (t) -> {
              LOGGER.error("Error occurred while archiving data. Will be retried.", t);
              errorStrategy.idle();
              return errorStrategy.idleTime();
            })
        .thenAccept(
            (delay) -> {
              if (!shutdown) {
                archiverExecutor.schedule(this, Date.from(Instant.now().plusMillis(delay)));
              }
            });
  }

  public CompletableFuture<Map.Entry<String, Integer>> archiveNextBatch() {
    return getNextBatch().thenCompose(this::archiveBatch);
  }

  public List<Integer> getPartitionIds() {
    return partitionIds;
  }

  @PreDestroy
  public void shutdown() {
    shutdown = true;
  }

  public static class ArchiveBatch {

    private String finishDate;
    private List<String> ids;

    public ArchiveBatch(final List<String> ids) {
      this.ids = ids;
    }

    public ArchiveBatch(String finishDate, List<String> ids) {
      this.finishDate = finishDate;
      this.ids = ids;
    }

    public String getFinishDate() {
      return finishDate;
    }

    public void setFinishDate(String finishDate) {
      this.finishDate = finishDate;
    }

    public List<String> getIds() {
      return ids;
    }

    public void setIds(List<String> ids) {
      this.ids = ids;
    }

    @Override
    public String toString() {
      return "AbstractArchiverJob{" + "finishDate='" + finishDate + '\'' + ", ids=" + ids + '}';
    }
  }
}

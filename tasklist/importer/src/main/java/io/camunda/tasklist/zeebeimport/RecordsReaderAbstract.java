/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.ThreadUtil.sleepFor;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.exceptions.NoSuchIndexException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.BackoffIdleStrategy;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.webapps.schema.descriptors.tasklist.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.zeebe.protocol.Protocol;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public abstract class RecordsReaderAbstract implements RecordsReader, Runnable {

  public static final String PARTITION_ID_FIELD_NAME = TasklistImportPositionIndex.PARTITION_ID;
  private static final Logger LOGGER = LoggerFactory.getLogger(RecordsReaderAbstract.class);

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired protected Metrics metrics;

  protected final int partitionId;
  protected final ImportValueType importValueType;
  protected final long maxPossibleSequence;
  protected int countEmptyRuns;
  @Autowired private ImportPositionHolder importPositionHolder;
  @Autowired private BeanFactory beanFactory;
  private BackoffIdleStrategy errorStrategy;

  @Autowired
  @Qualifier("tasklistRecordsReaderThreadPoolExecutor")
  private ThreadPoolTaskScheduler readersExecutor;

  @Autowired
  @Qualifier("tasklistImportThreadPoolExecutor")
  private ThreadPoolTaskExecutor importExecutor;

  @Autowired private RecordsReaderHolder recordsReaderHolder;

  private ImportJob pendingImportJob;
  private final ReentrantLock schedulingImportJobLock;
  private boolean ongoingRescheduling;
  private final BlockingQueue<Callable<Boolean>> importJobs;
  private Callable<Boolean> active;

  public RecordsReaderAbstract(
      final int partitionId, final ImportValueType importValueType, final int queueSize) {
    this.partitionId = partitionId;
    this.importValueType = importValueType;
    importJobs = new LinkedBlockingQueue<>(queueSize);
    schedulingImportJobLock = new ReentrantLock();
    // 1st sequence of next partition - 1
    maxPossibleSequence = Protocol.encodePartitionId(partitionId + 1, 0) - 1;
  }

  @PostConstruct
  public void postConstruct() {
    errorStrategy =
        new BackoffIdleStrategy(tasklistProperties.getImporter().getReaderBackoff(), 1.2f, 10_000);

    try {
      final var latestPosition =
          importPositionHolder.getLatestLoadedPosition(
              importValueType.getAliasTemplate(), partitionId);

      importPositionHolder.recordLatestLoadedPosition(latestPosition);
    } catch (final IOException e) {
      LOGGER.error(
          "Failed to write initial import position index document for value type [{}] and partition [{}]",
          importValueType,
          partitionId,
          e);
    }
  }

  @Override
  public void run() {
    readAndScheduleNextBatch();
  }

  @Override
  public int readAndScheduleNextBatch(final boolean autoContinue) {
    final var readerBackoff = (long) tasklistProperties.getImporter().getReaderBackoff();
    final boolean useOnlyPosition = tasklistProperties.getImporter().isUseOnlyPosition();
    try {
      final ImportBatch importBatch;
      final var latestPosition =
          importPositionHolder.getLatestScheduledPosition(
              importValueType.getAliasTemplate(), partitionId);
      if (!useOnlyPosition && latestPosition != null && latestPosition.getSequence() > 0) {
        LOGGER.debug("Use import for {} ( {} ) by sequence", importValueType.name(), partitionId);
        importBatch = readNextBatchBySequence(latestPosition.getSequence());
      } else {
        LOGGER.debug("Use import for {} ( {} ) by position", importValueType.name(), partitionId);
        importBatch = readNextBatchByPositionAndPartition(latestPosition.getPosition(), null);
      }
      Long nextRunDelay = null;
      if (importBatch.getHits().size() == 0) {
        markRecordReaderCompletedIfMinimumEmptyBatchesReceived();
        nextRunDelay = readerBackoff;
      } else {
        final var importJob = createImportJob(latestPosition, importBatch);
        if (!scheduleImportJob(importJob, !autoContinue)) {
          // didn't succeed to schedule import job ->
          // reader gets scheduled once the queue has capacity
          // if autoContinue == false, the reader is controlled
          // outside the readers thread pool, in that case, the
          // one who is controlling the reader will/must trigger
          // another round to read the next batch.
          return 0;
        }
      }
      errorStrategy.reset();
      if (autoContinue) {
        rescheduleReader(nextRunDelay);
      }
      return importBatch.getHits().size();
    } catch (final NoSuchIndexException ex) {
      markRecordReaderCompletedIfMinimumEmptyBatchesReceived();
      // if no index found, we back off current reader
      if (autoContinue) {
        rescheduleReader(readerBackoff);
      }
    } catch (final Exception ex) {
      LOGGER.error(ex.getMessage(), ex);
      if (autoContinue) {
        errorStrategy.idle();
        rescheduleReader(errorStrategy.idleTime());
      }
    }

    return 0;
  }

  @Override
  public int readAndScheduleNextBatch() {
    return readAndScheduleNextBatch(true);
  }

  @Override
  public ImportBatch readNextBatchBySequence(final Long sequence) throws NoSuchIndexException {
    return readNextBatchBySequence(sequence, null);
  }

  @Override
  public boolean tryToScheduleImportJob(final ImportJob importJob, final boolean skipPendingJob) {
    return withReschedulingImportJobLock(
        () -> {
          var scheduled = false;
          var retries = 3;

          while (!scheduled && retries > 0) {
            scheduled = importJobs.offer(executeJob(importJob));
            retries = retries - 1;
          }

          pendingImportJob = skipPendingJob || scheduled ? null : importJob;
          if (scheduled && active == null) {
            executeNext();
          }

          return scheduled;
        });
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public ImportValueType getImportValueType() {
    return importValueType;
  }

  @Override
  public BlockingQueue<Callable<Boolean>> getImportJobs() {
    return importJobs;
  }

  private ImportJob createImportJob(
      final ImportPositionEntity latestPosition, final ImportBatch importBatch) {
    return beanFactory.getBean(ImportJob.class, importBatch, latestPosition);
  }

  private void rescheduleReader(final Long readerDelay) {
    if (readerDelay != null) {
      readersExecutor.schedule(
          this, OffsetDateTime.now().plus(readerDelay, ChronoUnit.MILLIS).toInstant());
    } else {
      readersExecutor.submit(this);
    }
  }

  private boolean scheduleImportJob(final ImportJob job, final boolean skipPendingJob) {
    if (tryToScheduleImportJob(job, skipPendingJob)) {
      importJobScheduledSucceeded(job);
      return true;
    }
    return false;
  }

  private void importJobScheduledSucceeded(final ImportJob job) {
    metrics
        .getTimer(
            Metrics.TIMER_NAME_IMPORT_JOB_SCHEDULED_TIME,
            Metrics.TAG_KEY_TYPE,
            importValueType.name(),
            Metrics.TAG_KEY_PARTITION,
            String.valueOf(partitionId))
        .record(Duration.between(job.getCreationTime(), OffsetDateTime.now()));
    job.recordLatestScheduledPosition();
  }

  private Callable<Boolean> executeJob(final ImportJob job) {
    return () -> {
      try {
        final var imported = job.call();
        if (imported) {
          executeNext();
          rescheduleRecordsReaderIfNecessary();
        } else {
          // retry the same job
          sleepFor(2000L);
          execute(active);
        }
        return imported;
      } catch (final Exception ex) {
        LOGGER.error("Exception occurred when importing data: " + ex.getMessage(), ex);
        // retry the same job
        sleepFor(2000L);
        execute(active);
        return false;
      }
    };
  }

  private void executeNext() {
    active = importJobs.poll();
    if (active != null) {
      final Future<Boolean> result = importExecutor.submit(active);
      // TODO what to do with failing jobs
      LOGGER.debug("Submitted next job");
    }
  }

  private void execute(final Callable<Boolean> job) {
    final Future<Boolean> result = importExecutor.submit(job);
    // TODO what to do with failing jobs
    LOGGER.debug("Submitted the same job");
  }

  private void rescheduleRecordsReaderIfNecessary() {
    withReschedulingImportJobLock(
        () -> {
          if (hasPendingImportJobToReschedule() && shouldReschedulePendingImportJob()) {
            startRescheduling();
            readersExecutor.submit(this::reschedulePendingImportJob);
          }
        });
  }

  private void reschedulePendingImportJob() {
    try {
      scheduleImportJob(pendingImportJob, false);
    } finally {
      // whatever happened (exception or not),
      // reset state and schedule reader so that
      // the reader starts from the last loaded
      // position again
      withReschedulingImportJobLock(
          () -> {
            pendingImportJob = null;
            completeRescheduling();
            rescheduleReader(null);
          });
    }
  }

  private void markRecordReaderCompletedIfMinimumEmptyBatchesReceived() {
    if (recordsReaderHolder.hasPartitionCompletedImporting(partitionId)) {
      recordsReaderHolder.incrementEmptyBatches(partitionId, importValueType);
    }

    if (recordsReaderHolder.isRecordReaderCompletedImporting(partitionId, importValueType)) {
      try {
        recordsReaderHolder.recordLatestLoadedPositionAsCompleted(
            importPositionHolder, importValueType.getAliasTemplate(), partitionId);
      } catch (final IOException e) {
        LOGGER.error(
            "Failed when trying to mark record reader [{}-{}] as completed",
            importValueType.getAliasTemplate(),
            partitionId,
            e);
      }
    }
  }

  private boolean hasPendingImportJobToReschedule() {
    return pendingImportJob != null;
  }

  private boolean shouldReschedulePendingImportJob() {
    return !ongoingRescheduling;
  }

  private void startRescheduling() {
    ongoingRescheduling = true;
  }

  private void completeRescheduling() {
    ongoingRescheduling = false;
  }

  private void withReschedulingImportJobLock(final Runnable action) {
    withReschedulingImportJobLock(
        () -> {
          action.run();
          return null;
        });
  }

  private <T> T withReschedulingImportJobLock(final Callable<T> action) {
    try {
      schedulingImportJobLock.lock();
      return action.call();
    } catch (final Exception e) {
      throw new TasklistRuntimeException(e);
    } finally {
      schedulingImportJobLock.unlock();
    }
  }
}

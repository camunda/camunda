/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.ThreadUtil.sleepFor;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.exceptions.NoSuchIndexException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.ImportPositionIndex;
import io.camunda.tasklist.zeebe.ImportValueType;
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

  public static final String PARTITION_ID_FIELD_NAME = ImportPositionIndex.PARTITION_ID;
  private static final Logger LOGGER = LoggerFactory.getLogger(RecordsReaderAbstract.class);

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired protected Metrics metrics;

  protected final int partitionId;
  protected final ImportValueType importValueType;

  @Autowired private ImportPositionHolder importPositionHolder;

  @Autowired private BeanFactory beanFactory;

  @Autowired
  @Qualifier("recordsReaderThreadPoolExecutor")
  private ThreadPoolTaskScheduler readersExecutor;

  @Autowired
  @Qualifier("importThreadPoolExecutor")
  private ThreadPoolTaskExecutor importExecutor;

  private ImportJob pendingImportJob;
  private ReentrantLock schedulingImportJobLock;
  private boolean ongoingRescheduling;
  private final BlockingQueue<Callable<Boolean>> importJobs;
  private Callable<Boolean> active;

  public RecordsReaderAbstract(int partitionId, ImportValueType importValueType, int queueSize) {
    this.partitionId = partitionId;
    this.importValueType = importValueType;
    this.importJobs = new LinkedBlockingQueue<>(queueSize);
    this.schedulingImportJobLock = new ReentrantLock();
  }

  @Override
  public void run() {
    readAndScheduleNextBatch();
  }

  @Override
  public int readAndScheduleNextBatch() {
    return readAndScheduleNextBatch(true);
  }

  public int readAndScheduleNextBatch(boolean autoContinue) {
    final var readerBackoff = tasklistProperties.getImporter().getReaderBackoff();
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
      Integer nextRunDelay = null;
      if (importBatch.getHits().size() == 0) {
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
      if (autoContinue) {
        rescheduleReader(nextRunDelay);
      }
      return importBatch.getHits().size();
    } catch (NoSuchIndexException ex) {
      // if no index found, we back off current reader
      if (autoContinue) {
        rescheduleReader(readerBackoff);
      }
    } catch (Exception ex) {
      LOGGER.error(ex.getMessage(), ex);
      if (autoContinue) {
        rescheduleReader(null);
      }
    }

    return 0;
  }

  private ImportJob createImportJob(
      final ImportPositionEntity latestPosition, final ImportBatch importBatch) {
    return beanFactory.getBean(ImportJob.class, importBatch, latestPosition);
  }

  public ImportBatch readNextBatchBySequence(final Long sequence) throws NoSuchIndexException {
    return readNextBatchBySequence(sequence, null);
  }

  private void rescheduleReader(final Integer readerDelay) {
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
    this.active = importJobs.poll();
    if (this.active != null) {
      final Future<Boolean> result = importExecutor.submit(this.active);
      // TODO what to do with failing jobs
      LOGGER.debug("Submitted next job");
    }
  }

  private void execute(Callable<Boolean> job) {
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

  public int getPartitionId() {
    return partitionId;
  }

  public ImportValueType getImportValueType() {
    return importValueType;
  }

  public BlockingQueue<Callable<Boolean>> getImportJobs() {
    return importJobs;
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
    } catch (Exception e) {
      throw new TasklistRuntimeException(e);
    } finally {
      schedulingImportJobLock.unlock();
    }
  }
}

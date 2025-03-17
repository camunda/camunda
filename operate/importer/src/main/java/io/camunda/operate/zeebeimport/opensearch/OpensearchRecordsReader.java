/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.opensearch;

import static io.camunda.operate.Metrics.GAUGE_IMPORT_QUEUE_SIZE;
import static io.camunda.operate.Metrics.TAG_KEY_PARTITION;
import static io.camunda.operate.Metrics.TAG_KEY_TYPE;
import static io.camunda.operate.store.opensearch.client.OpenSearchOperation.QUERY_MAX_SIZE;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.gt;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.gtLte;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sortOptions;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.Metrics;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.HitEntity;
import io.camunda.operate.exceptions.NoSuchIndexException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.store.opensearch.client.sync.ZeebeRichOpenSearchClient;
import io.camunda.operate.util.BackoffIdleStrategy;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.NumberThrottleable;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.ImportJob;
import io.camunda.operate.zeebeimport.ImportListener;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.operate.zeebeimport.RecordsReader;
import io.camunda.operate.zeebeimport.RecordsReaderHolder;
import io.camunda.webapps.schema.descriptors.operate.index.ImportPositionIndex;
import io.camunda.webapps.schema.entities.operate.ImportPositionEntity;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
@Scope(SCOPE_PROTOTYPE)
public class OpensearchRecordsReader implements RecordsReader {

  private static final String READ_BATCH_ERROR_MESSAGE =
      "Exception occurred for alias [%s], while obtaining next Zeebe records batch: %s";
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchRecordsReader.class);

  /** Partition id. */
  private final int partitionId;

  /** Value type. */
  private final ImportValueType importValueType;

  /** The queue of executed tasks for execution. */
  private final BlockingQueue<Callable<Boolean>> importJobs;

  private final ReentrantLock schedulingImportJobLock;
  private NumberThrottleable batchSizeThrottle;

  /** The job that we are currently busy with. */
  private Callable<Boolean> active;

  private ImportJob pendingImportJob;
  private boolean ongoingRescheduling;

  private long maxPossibleSequence;

  private int countEmptyRuns;

  private BackoffIdleStrategy errorStrategy;

  @Autowired
  @Qualifier("importThreadPoolExecutor")
  private ThreadPoolTaskExecutor importExecutor;

  @Autowired
  @Qualifier("recordsReaderThreadPoolExecutor")
  private ThreadPoolTaskScheduler readersExecutor;

  @Autowired private ImportPositionHolder importPositionHolder;

  @Autowired private OperateProperties operateProperties;

  @Autowired private ZeebeRichOpenSearchClient zeebeRichOpenSearchClient;

  @Autowired private BeanFactory beanFactory;

  @Autowired private Metrics metrics;

  @Autowired(required = false)
  private List<ImportListener> importListeners;

  @Autowired private RecordsReaderHolder recordsReaderHolder;

  public OpensearchRecordsReader(
      final int partitionId, final ImportValueType importValueType, final int queueSize) {
    this.partitionId = partitionId;
    this.importValueType = importValueType;
    importJobs = new LinkedBlockingQueue<>(queueSize);
    schedulingImportJobLock = new ReentrantLock();
  }

  @PostConstruct
  public void postConstruct() {
    batchSizeThrottle =
        new NumberThrottleable.DivideNumberThrottle(
            operateProperties.getZeebeOpensearch().getBatchSize());
    // 1st sequence of next partition - 1
    maxPossibleSequence = sequence(partitionId + 1, 0) - 1;
    countEmptyRuns = 0;
    errorStrategy =
        new BackoffIdleStrategy(operateProperties.getImporter().getReaderBackoff(), 1.2f, 10_000);

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

  private void readAndScheduleNextBatch() {
    readAndScheduleNextBatch(true);
  }

  @Override
  public void readAndScheduleNextBatch(final boolean autoContinue) {
    final int readerBackoff = operateProperties.getImporter().getReaderBackoff();
    final boolean useOnlyPosition = operateProperties.getImporter().isUseOnlyPosition();
    try {
      metrics.registerGaugeQueueSize(
          GAUGE_IMPORT_QUEUE_SIZE,
          importJobs,
          TAG_KEY_PARTITION,
          String.valueOf(partitionId),
          TAG_KEY_TYPE,
          importValueType.name());
      final ImportBatch importBatch;
      final ImportPositionEntity latestPosition =
          importPositionHolder.getLatestScheduledPosition(
              importValueType.getAliasTemplate(), partitionId);
      if (!useOnlyPosition && latestPosition != null && latestPosition.getSequence() > 0) {
        LOGGER.debug("Use import for {} ( {} ) by sequence", importValueType.name(), partitionId);
        importBatch = readNextBatchBySequence(latestPosition.getSequence());
      } else if (latestPosition != null) {
        LOGGER.debug("Use import for {} ( {} ) by position", importValueType.name(), partitionId);
        importBatch = readNextBatchByPositionAndPartition(latestPosition.getPosition(), null);
      } else {
        LOGGER.debug("latestPosition is null, importBatch was not initialized");
        importBatch = null;
      }
      Integer nextRunDelay = null;
      if (importBatch == null
          || importBatch.getHits() == null
          || importBatch.getHits().size() == 0) {
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
          return;
        }
      }
      errorStrategy.reset();
      if (autoContinue) {
        rescheduleReader(nextRunDelay);
      }
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
        rescheduleReader((int) errorStrategy.idleTime());
      }
    }
  }

  @Override
  public ImportBatch readNextBatchBySequence(final Long sequence, final Long lastSequence)
      throws NoSuchIndexException {
    final String aliasName =
        importValueType.getAliasName(operateProperties.getZeebeOpensearch().getPrefix());
    final int batchSize = batchSizeThrottle.get();
    if (batchSize != batchSizeThrottle.getOriginal()) {
      LOGGER.warn(
          "Use new batch size {} (original {})", batchSize, batchSizeThrottle.getOriginal());
    }
    final long lessThanEqualsSequence;
    final int maxNumberOfHits;

    if (lastSequence != null && lastSequence > 0) {
      // in worst case all the records are duplicated
      maxNumberOfHits = (int) ((lastSequence - sequence) * 2);
      lessThanEqualsSequence = lastSequence;
      LOGGER.debug(
          "Import batch reread was called. Data type {}, partitionId {}, sequence {}, lastSequence {}, maxNumberOfHits {}.",
          importValueType,
          partitionId,
          sequence,
          lessThanEqualsSequence,
          maxNumberOfHits);
    } else {
      maxNumberOfHits = batchSize;
      if (countEmptyRuns == operateProperties.getImporter().getMaxEmptyRuns()) {
        lessThanEqualsSequence = maxPossibleSequence;
        countEmptyRuns = 0;
        LOGGER.debug(
            "Max empty runs reached. Data type {}, partitionId {}, sequence {}, lastSequence {}, maxNumberOfHits {}.",
            importValueType,
            partitionId,
            sequence,
            lessThanEqualsSequence,
            maxNumberOfHits);
      } else {
        lessThanEqualsSequence = sequence + batchSize;
      }
    }

    final var searchRequestBuilder =
        searchRequestBuilder(aliasName)
            .routing(String.valueOf(partitionId))
            .requestCache(false)
            .size(Math.min(maxNumberOfHits, QUERY_MAX_SIZE))
            .sort(sortOptions(ImportPositionIndex.SEQUENCE, SortOrder.Asc))
            .query(gtLte(ImportPositionIndex.SEQUENCE, sequence, lessThanEqualsSequence));
    final boolean scrollNeeded = maxNumberOfHits >= ElasticsearchUtil.QUERY_MAX_SIZE;
    try {
      final HitEntity[] hits = withTimerSearchHits(() -> read(searchRequestBuilder, scrollNeeded));
      if (hits.length == 0) {
        countEmptyRuns++;
      } else {
        countEmptyRuns = 0;
      }
      return createImportBatch(hits);
    } catch (final OpenSearchException ex) {
      if (ex.getMessage().contains("no such index")) {
        throw new NoSuchIndexException();
      } else {
        throw new OperateRuntimeException(
            String.format(READ_BATCH_ERROR_MESSAGE, aliasName, ex.getMessage()), ex);
      }
    } catch (final Exception e) {
      if (e.getMessage().contains("entity content is too long")) {
        LOGGER.info(
            "{}. Will decrease batch size for {}-{}",
            e.getMessage(),
            importValueType.name(),
            partitionId);
        batchSizeThrottle.throttle();
        return readNextBatchBySequence(sequence, lastSequence);
      } else {
        throw new OperateRuntimeException(
            String.format(READ_BATCH_ERROR_MESSAGE, aliasName, e.getMessage()), e);
      }
    }
  }

  @Override
  public ImportBatch readNextBatchByPositionAndPartition(
      final long positionFrom, final Long positionTo) throws NoSuchIndexException {
    final String aliasName =
        importValueType.getAliasName(operateProperties.getZeebeOpensearch().getPrefix());
    int size = batchSizeThrottle.get();
    final Query rangeQuery;

    if (positionTo != null) {
      LOGGER.debug(
          "Import batch reread was called. Data type {}, partitionId {}, positionFrom {}, positionTo {}.",
          importValueType,
          partitionId,
          positionFrom,
          positionTo);
      size = (int) (positionTo - positionFrom);
      size = size <= 0 || size > QUERY_MAX_SIZE ? QUERY_MAX_SIZE : size;

      rangeQuery = gtLte(ImportPositionIndex.POSITION, positionFrom, positionTo);
    } else {
      rangeQuery = gt(ImportPositionIndex.POSITION, positionFrom);
    }

    final Query query = and(term(PARTITION_ID_FIELD_NAME, partitionId), rangeQuery);

    final var searchRequestBuilder =
        searchRequestBuilder(aliasName)
            .query(query)
            .sort(sortOptions(ImportPositionIndex.POSITION, SortOrder.Asc))
            .size(size)
            .routing(String.valueOf(partitionId))
            .requestCache(false);

    try {
      final HitEntity[] hits =
          withTimerSearchHits(
              () ->
                  zeebeRichOpenSearchClient
                      .doc()
                      .search(searchRequestBuilder, Object.class)
                      .hits()
                      .hits()
                      .stream()
                      .map(this::searchHitToOperateHit)
                      .toArray(HitEntity[]::new));

      return createImportBatch(hits);
    } catch (final OpenSearchException ex) {
      if (ex.getMessage().contains("no such index")) {
        throw new NoSuchIndexException();
      } else {
        throw new OperateRuntimeException(
            String.format(READ_BATCH_ERROR_MESSAGE, aliasName, ex.getMessage()), ex);
      }
    } catch (final Exception e) {
      if (e.getMessage().contains("entity content is too long")) {
        batchSizeThrottle.throttle();
        return readNextBatchByPositionAndPartition(positionFrom, positionTo);
      } else {
        throw new OperateRuntimeException(
            String.format(READ_BATCH_ERROR_MESSAGE, aliasName, e.getMessage()), e);
      }
    }
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

  private ImportBatch readNextBatchBySequence(final Long sequence) throws NoSuchIndexException {
    return readNextBatchBySequence(sequence, null);
  }

  private HitEntity[] read(
      final SearchRequest.Builder searchRequestBuilder, final boolean scrollNeeded)
      throws IOException {
    final List<Hit<Object>> hits =
        scrollNeeded
            ? zeebeRichOpenSearchClient
                .doc()
                .scrollHits(searchRequestBuilder, Object.class)
                .values()
            : zeebeRichOpenSearchClient
                .doc()
                .search(searchRequestBuilder, Object.class)
                .hits()
                .hits();

    return hits.stream().map(this::searchHitToOperateHit).toArray(HitEntity[]::new);
  }

  private void rescheduleReader(final Integer readerDelay) {
    if (readerDelay != null) {
      readersExecutor.schedule(
          this, Date.from(OffsetDateTime.now().plus(readerDelay, ChronoUnit.MILLIS).toInstant()));
    } else {
      readersExecutor.submit(this);
    }
  }

  private HitEntity searchHitToOperateHit(final Hit<?> searchHit) {
    if (searchHit.source() == null) {
      return null;
    }
    final var stringWriter = new StringWriter();
    try {
      new ObjectMapper().writeValue(stringWriter, searchHit.source());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    final var jsonString = stringWriter.toString();
    return new HitEntity().setIndex(searchHit.index()).setSourceAsString(jsonString);
  }

  private ImportJob createImportJob(
      final ImportPositionEntity latestPosition, final ImportBatch importBatch) {
    return beanFactory.getBean(ImportJob.class, importBatch, latestPosition);
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

    final var batch = job.getImportBatch();
    batch.setScheduledTime(OffsetDateTime.now());

    notifyImportListenersAsScheduled(batch);
    job.recordLatestScheduledPosition();
  }

  private void notifyImportListenersAsScheduled(final ImportBatch importBatch) {
    if (importListeners != null) {
      importListeners.forEach(listener -> listener.scheduled(importBatch));
    }
  }

  private ImportBatch createImportBatch(final HitEntity[] hits) {
    String indexName = null;
    if (hits.length > 0) {
      indexName = hits[hits.length - 1].getIndex();
    }
    return new ImportBatch(partitionId, importValueType, Arrays.asList(hits), indexName);
  }

  private SearchResponse withTimer(final Callable<SearchResponse> callable) throws Exception {
    return metrics
        .getTimer(
            Metrics.TIMER_NAME_IMPORT_QUERY,
            Metrics.TAG_KEY_TYPE,
            importValueType.name(),
            Metrics.TAG_KEY_PARTITION,
            String.valueOf(partitionId))
        .recordCallable(callable);
  }

  private HitEntity[] withTimerSearchHits(final Callable<HitEntity[]> callable) throws Exception {
    return metrics
        .getTimer(
            Metrics.TIMER_NAME_IMPORT_QUERY,
            Metrics.TAG_KEY_TYPE,
            importValueType.name(),
            Metrics.TAG_KEY_PARTITION,
            String.valueOf(partitionId))
        .recordCallable(callable);
  }

  private boolean tryToScheduleImportJob(final ImportJob importJob, final boolean skipPendingJob) {
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

  private void executeNext() {
    active = importJobs.poll();
    if (active != null) {
      importExecutor.submit(active);
      // TODO what to do with failing jobs
      LOGGER.debug("Submitted next job");
    }
  }

  private void execute(final Callable<Boolean> job) {
    importExecutor.submit(job);
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
            readersExecutor.submit(this);
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
      throw new OperateRuntimeException(e);
    } finally {
      schedulingImportJobLock.unlock();
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.Metrics;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.HitEntity;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.exceptions.NoSuchIndexException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.store.opensearch.client.sync.ZeebeRichOpenSearchClient;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.NumberThrottleable;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.ImportJob;
import io.camunda.operate.zeebeimport.ImportListener;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.operate.zeebeimport.RecordsReader;
import jakarta.annotation.PostConstruct;
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
import static io.camunda.operate.util.ExceptionHelper.withIOException;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Conditional(OpensearchCondition.class)
@Component
@Scope(SCOPE_PROTOTYPE)
public class OpensearchRecordsReader implements RecordsReader {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchRecordsReader.class);
  /**
   * Partition id.
   */
  private final int partitionId;

  /**
   * Value type.
   */
  private final ImportValueType importValueType;

  /**
   * The queue of executed tasks for execution.
   */
  private final BlockingQueue<Callable<Boolean>> importJobs;
  private final ReentrantLock schedulingImportJobLock;
  private NumberThrottleable batchSizeThrottle;
  /**
   * The job that we are currently busy with.
   */
  private Callable<Boolean> active;
  private ImportJob pendingImportJob;
  private boolean ongoingRescheduling;

  private long maxPossibleSequence;

  private int countEmptyRuns;

  @Autowired
  @Qualifier("importThreadPoolExecutor")
  private ThreadPoolTaskExecutor importExecutor;

  @Autowired
  @Qualifier("recordsReaderThreadPoolExecutor")
  private ThreadPoolTaskScheduler readersExecutor;

  @Autowired
  private ImportPositionHolder importPositionHolder;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ZeebeRichOpenSearchClient zeebeRichOpenSearchClient;

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private Metrics metrics;

  @Autowired(required = false)
  private List<ImportListener> importListeners;

  public OpensearchRecordsReader(int partitionId, ImportValueType importValueType, int queueSize) {
    this.partitionId = partitionId;
    this.importValueType = importValueType;
    this.importJobs = new LinkedBlockingQueue<>(queueSize);
    this.schedulingImportJobLock = new ReentrantLock();
  }

  @PostConstruct
  private void postConstruct() {
    this.batchSizeThrottle = new NumberThrottleable.DivideNumberThrottle(operateProperties.getZeebeOpensearch().getBatchSize());
    //1st sequence of next partition - 1
    this.maxPossibleSequence = sequence(partitionId + 1, 0) - 1;
    this.countEmptyRuns = 0;
  }

  @Override
  public void run() {
    readAndScheduleNextBatch();
  }

  private void readAndScheduleNextBatch() {
    readAndScheduleNextBatch(true);
  }

  @Override
  public void readAndScheduleNextBatch(boolean autoContinue) {
    final int readerBackoff = operateProperties.getImporter().getReaderBackoff();
    try {
      metrics.registerGaugeQueueSize(GAUGE_IMPORT_QUEUE_SIZE, importJobs, TAG_KEY_PARTITION,
          String.valueOf(partitionId), TAG_KEY_TYPE, importValueType.name());
      ImportBatch importBatch;
      final ImportPositionEntity latestPosition = importPositionHolder.getLatestScheduledPosition(importValueType.getAliasTemplate(), partitionId);
      if (latestPosition != null && latestPosition.getSequence() > 0) {
        importBatch = readNextBatchBySequence(latestPosition.getSequence());
      } else {
        importBatch = readNextBatchByPositionAndPartition(latestPosition.getPosition(), null);
      }
      Integer nextRunDelay = null;
      if (importBatch == null || importBatch.getHits() == null || importBatch.getHits().size() == 0) {
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
      if (autoContinue) {
        rescheduleReader(nextRunDelay);
      }
    } catch (NoSuchIndexException ex) {
      //if no index found, we back off current reader
      if (autoContinue) {
        rescheduleReader(readerBackoff);
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      if (autoContinue) {
        rescheduleReader(null);
      }
    }
  }

  private ImportBatch readNextBatchBySequence(final Long sequence) throws NoSuchIndexException {
    return readNextBatchBySequence(sequence, null);
  }

  @Override
  public ImportBatch readNextBatchBySequence(final Long sequence, final Long lastSequence) throws NoSuchIndexException {
    final String aliasName = importValueType.getAliasName(operateProperties.getZeebeOpensearch().getPrefix());
    int batchSize = batchSizeThrottle.get();
    if (batchSize != batchSizeThrottle.getOriginal()) {
      logger.warn("Use new batch size {} (original {})", batchSize, batchSizeThrottle.getOriginal());
    }
    final long lessThanEqualsSequence;
    final int maxNumberOfHits;

    if (lastSequence != null && lastSequence > 0) {
      //in worst case all the records are duplicated
      maxNumberOfHits = (int) ((lastSequence - sequence) * 2);
      lessThanEqualsSequence = lastSequence;
      logger.debug(
          "Import batch reread was called. Data type {}, partitionId {}, sequence {}, lastSequence {}, maxNumberOfHits {}.",
          importValueType, partitionId, sequence, lessThanEqualsSequence, maxNumberOfHits);
    } else {
      maxNumberOfHits = batchSize;
      if (countEmptyRuns == operateProperties.getImporter().getMaxEmptyRuns()) {
        lessThanEqualsSequence = maxPossibleSequence;
        countEmptyRuns = 0;
        logger.debug("Max empty runs reached. Data type {}, partitionId {}, sequence {}, lastSequence {}, maxNumberOfHits {}.",
            importValueType, partitionId, sequence, lessThanEqualsSequence, maxNumberOfHits);
      } else {
        lessThanEqualsSequence = sequence + batchSize;
      }
    }

    var searchRequestBuilder = searchRequestBuilder(aliasName)
        .routing(String.valueOf(partitionId))
        .requestCache(false)
        .size(Math.min(maxNumberOfHits, QUERY_MAX_SIZE))
        .sort(sortOptions(ImportPositionIndex.SEQUENCE, SortOrder.Asc))
        .query(gtLte(ImportPositionIndex.SEQUENCE, sequence, lessThanEqualsSequence));
    boolean scrollNeeded = maxNumberOfHits >= ElasticsearchUtil.QUERY_MAX_SIZE;
    try {
      final HitEntity[] hits = withTimerSearchHits(() -> read(searchRequestBuilder, scrollNeeded));
      if (hits.length == 0) {
        countEmptyRuns++;
      } else {
        countEmptyRuns = 0;
      }
      return createImportBatch(hits);
    } catch (OpenSearchException ex) {
      if (ex.getMessage().contains("no such index")) {
        throw new NoSuchIndexException();
      } else {
        final String message = String.format("Exception occurred for alias [%s], while obtaining next Zeebe records batch: %s", aliasName, ex.getMessage());
        throw new OperateRuntimeException(message, ex);
      }
    } catch (Exception e) {
      if (e.getMessage().contains("entity content is too long")) {
        logger.info("{}. Will decrease batch size for {}-{}", e.getMessage(), importValueType.name(), partitionId);
        batchSizeThrottle.throttle();
        return readNextBatchBySequence(sequence, lastSequence);
      } else {
        final String message = String.format(
            "Exception occurred for alias [%s], while obtaining next Zeebe records batch: %s", aliasName, e.getMessage());
        throw new OperateRuntimeException(message, e);
      }
    }
  }

  private HitEntity[] read(SearchRequest.Builder searchRequestBuilder, boolean scrollNeeded) throws IOException {
    List<Hit<Object>> hits = scrollNeeded ?
      zeebeRichOpenSearchClient.doc().scrollHits(searchRequestBuilder, Object.class).values() :
      zeebeRichOpenSearchClient.doc().search(searchRequestBuilder, Object.class).hits().hits();

    return hits.stream().map(this::searchHitToOperateHit).toArray(HitEntity[]::new);
  }

  @Override
  public ImportBatch readNextBatchByPositionAndPartition(long positionFrom, Long positionTo) throws NoSuchIndexException {
    String aliasName = importValueType.getAliasName(operateProperties.getZeebeOpensearch().getPrefix());
    int size = batchSizeThrottle.get();
    Query rangeQuery;

    if (positionTo != null) {
      logger.debug("Import batch reread was called. Data type {}, partitionId {}, positionFrom {}, positionTo {}.", importValueType, partitionId, positionFrom, positionTo);
      size = (int)(positionTo - positionFrom);
      size = size <= 0 || size > QUERY_MAX_SIZE ? QUERY_MAX_SIZE : size;

      rangeQuery = gtLte(ImportPositionIndex.POSITION, positionFrom, positionTo);
    } else {
      rangeQuery = gt(ImportPositionIndex.POSITION, positionFrom);
    }

    Query query = and(
      term(PARTITION_ID_FIELD_NAME,partitionId),
      rangeQuery
    );

    var searchRequestBuilder = searchRequestBuilder(aliasName)
      .query(query)
      .sort(sortOptions(ImportPositionIndex.POSITION, SortOrder.Asc))
      .size(size)
      .routing(String.valueOf(partitionId))
      .requestCache(false);

    try {
      final HitEntity[] hits = withTimerSearchHits(() ->
        zeebeRichOpenSearchClient.doc().search(searchRequestBuilder, Object.class)
          .hits()
          .hits()
          .stream()
          .map(this::searchHitToOperateHit)
          .toArray(HitEntity[]::new)
      );

      return createImportBatch(hits);
    } catch (OpenSearchException ex) {
      if (ex.getMessage().contains("no such index")) {
        throw new NoSuchIndexException();
      } else {
        final String message = String.format("Exception occurred for alias [%s], while obtaining next Zeebe records batch: %s", aliasName, ex.getMessage());
        throw new OperateRuntimeException(message, ex);
      }
    } catch (Exception e) {
      if( e.getMessage().contains("entity content is too long")) {
        batchSizeThrottle.throttle();
        return readNextBatchByPositionAndPartition(positionFrom, positionTo);
      } else {
        final String message = String.format(
            "Exception occurred for alias [%s], while obtaining next Zeebe records batch: %s", aliasName, e.getMessage());
        throw new OperateRuntimeException(message, e);
      }
    }
  }

  private void rescheduleReader(Integer readerDelay) {
    if (readerDelay != null) {
      readersExecutor.schedule(this,
          Date.from(OffsetDateTime.now().plus(readerDelay, ChronoUnit.MILLIS).toInstant()));
    } else {
      readersExecutor.submit(this);
    }
  }

  private HitEntity searchHitToOperateHit(Hit<?> searchHit) {
    if( searchHit.source() == null) return null;
    var stringWriter = new StringWriter();
    try {
      new ObjectMapper().writeValue(stringWriter, searchHit.source());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    var jsonString = stringWriter.toString();
    return new HitEntity().setIndex(searchHit.index()).setSourceAsString(jsonString);
  }

  private ImportJob createImportJob(final ImportPositionEntity latestPosition, final ImportBatch importBatch) {
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
    metrics.getTimer(Metrics.TIMER_NAME_IMPORT_JOB_SCHEDULED_TIME,
            Metrics.TAG_KEY_TYPE, importValueType.name(),
            Metrics.TAG_KEY_PARTITION, String.valueOf(partitionId))
        .record(Duration.between(job.getCreationTime(), OffsetDateTime.now()));

    final var batch = job.getImportBatch();
    batch.setScheduledTime(OffsetDateTime.now());

    notifyImportListenersAsScheduled(batch);
    job.recordLatestScheduledPosition();
  }

  private void notifyImportListenersAsScheduled(ImportBatch importBatch) {
    if (importListeners != null) {
      importListeners.forEach(listener -> listener.scheduled(importBatch));
    }
  }

  private ImportBatch createImportBatch(HitEntity[] hits) {
    String indexName = null;
    if (hits.length > 0) {
      indexName = hits[hits.length - 1].getIndex();
    }
    return new ImportBatch(partitionId, importValueType, Arrays.asList(hits), indexName);
  }

  private SearchResponse withTimer(Callable<SearchResponse> callable) throws Exception {
    return metrics.getTimer(Metrics.TIMER_NAME_IMPORT_QUERY,
            Metrics.TAG_KEY_TYPE, importValueType.name(),
            Metrics.TAG_KEY_PARTITION, String.valueOf(partitionId))
        .recordCallable(callable);
  }

  private HitEntity[] withTimerSearchHits(Callable<HitEntity[]> callable) throws Exception {
    return metrics.getTimer(Metrics.TIMER_NAME_IMPORT_QUERY,
            Metrics.TAG_KEY_TYPE, importValueType.name(),
            Metrics.TAG_KEY_PARTITION, String.valueOf(partitionId))
        .recordCallable(callable);
  }

  private boolean tryToScheduleImportJob(final ImportJob importJob, final boolean skipPendingJob) {
    return withReschedulingImportJobLock(() -> {
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
          //retry the same job
          sleepFor(2000L);
          execute(active);
        }
        return imported;
      } catch (final Exception ex) {
        logger.error("Exception occurred when importing data: " + ex.getMessage(), ex);
        //retry the same job
        sleepFor(2000L);
        execute(active);
        return false;
      }
    };
  }

  private void executeNext() {
    if ((active = importJobs.poll()) != null) {
      importExecutor.submit(active);
      //TODO what to do with failing jobs
      logger.debug("Submitted next job");
    }
  }

  private void execute(Callable<Boolean> job) {
    importExecutor.submit(job);
    //TODO what to do with failing jobs
    logger.debug("Submitted the same job");
  }

  private void rescheduleRecordsReaderIfNecessary() {
    withReschedulingImportJobLock(() -> {
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
      withReschedulingImportJobLock(() -> {
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
    withReschedulingImportJobLock(() -> {
      action.run();
      return null;
    });
  }

  private <T> T withReschedulingImportJobLock(final Callable<T> action) {
    try {
      schedulingImportJobLock.lock();
      return action.call();
    } catch (Exception e) {
      throw new OperateRuntimeException(e);
    } finally {
      schedulingImportJobLock.unlock();
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

}

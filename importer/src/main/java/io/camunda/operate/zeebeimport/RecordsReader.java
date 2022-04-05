/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.Metrics.GAUGE_IMPORT_QUEUE_SIZE;
import static io.camunda.operate.Metrics.TAG_KEY_PARTITION;
import static io.camunda.operate.Metrics.TAG_KEY_TYPE;
import static io.camunda.operate.util.ElasticsearchUtil.QUERY_MAX_SIZE;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.Metrics;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.exceptions.NoSuchIndexException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.zeebe.ImportValueType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;


/**
 * Represents Zeebe data reader for one partition and one value type. After reading the data is also schedules the jobs
 * for import execution. Each reader can have it's own backoff, so that we make a pause in case there is no data currently
 * for given partition and value type.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class RecordsReader implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(RecordsReader.class);

  public static final String PARTITION_ID_FIELD_NAME = ImportPositionIndex.PARTITION_ID;

  /**
   * Partition id.
   */
  private int partitionId;

  /**
   * Value type.
   */
  private ImportValueType importValueType;

  /**
   * The queue of executed tasks for execution.
   */
  private final BlockingQueue<Callable<Boolean>> importJobs;

  /**
   * The job that we are currently busy with.
   */
  private Callable<Boolean> active;

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
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private Metrics metrics;

  @Autowired(required = false)
  private List<ImportListener> importListeners;

  public RecordsReader(int partitionId, ImportValueType importValueType, int queueSize) {
    this.partitionId = partitionId;
    this.importValueType = importValueType;
    this.importJobs = new LinkedBlockingQueue(queueSize);
  }

  @Override
  public void run() {
    readAndScheduleNextBatch();
  }

  public void readAndScheduleNextBatch() {
    readAndScheduleNextBatch(true);
  }

  public void readAndScheduleNextBatch(boolean autoContinue) {
    final int readerBackoff = operateProperties.getImporter().getReaderBackoff();
    try {
      metrics.registerGaugeQueueSize(GAUGE_IMPORT_QUEUE_SIZE, importJobs, TAG_KEY_PARTITION,
          String.valueOf(partitionId), TAG_KEY_TYPE, importValueType.name());
      ImportPositionEntity latestPosition = importPositionHolder.getLatestScheduledPosition(importValueType.getAliasTemplate(), partitionId);
      ImportBatch importBatch = readNextBatch(latestPosition.getPosition(), null);
      Integer nextRunDelay = null;
      if (importBatch.getHits().size() == 0) {
        nextRunDelay = readerBackoff;
      } else {
        scheduleImport(latestPosition, importBatch);
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

  private void rescheduleReader(Integer readerDelay) {
    if (readerDelay != null) {
      readersExecutor.schedule(this,
          Date.from(OffsetDateTime.now().plus(readerDelay, ChronoUnit.MILLIS).toInstant()));
    } else {
      readersExecutor.submit(this);
    }
  }

  private void scheduleImport(ImportPositionEntity latestPosition, ImportBatch importBatch) {
    //create new instance of import job
    ImportJob importJob = beanFactory.getBean(ImportJob.class, importBatch, latestPosition);
    scheduleImport(importJob);
    importBatch.setScheduledTime(OffsetDateTime.now());
    notifyImportListenersAsScheduled(importBatch);
    importJob.recordLatestScheduledPosition();
  }

  private void notifyImportListenersAsScheduled(ImportBatch importBatch) {
    if (importListeners != null) {
      importListeners.forEach(listener -> listener.scheduled(importBatch));
    }
  }

  public ImportBatch readNextBatch(long positionFrom, Long positionTo) throws NoSuchIndexException {
    String aliasName = importValueType.getAliasName(operateProperties.getZeebeElasticsearch().getPrefix());
    try {

      final SearchRequest searchRequest = createSearchQuery(aliasName, positionFrom, positionTo);

      final SearchResponse searchResponse =
          withTimer(() -> zeebeEsClient.search(searchRequest, RequestOptions.DEFAULT));

      return createImportBatch(searchResponse);

    } catch (ElasticsearchStatusException ex) {
      if (ex.getMessage().contains("no such index")) {
        throw new NoSuchIndexException();
      } else {
        final String message = String.format("Exception occurred, while obtaining next Zeebe records batch: %s", ex.getMessage());
        throw new OperateRuntimeException(message, ex);
      }
    } catch (Exception e) {
      final String message = String.format("Exception occurred, while obtaining next Zeebe records batch: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private ImportBatch createImportBatch(SearchResponse searchResponse) {
    SearchHit[] hits = searchResponse.getHits().getHits();
    String indexName = null;
    if (hits.length > 0) {
      indexName = hits[hits.length - 1].getIndex();
    }
    return new ImportBatch(partitionId, importValueType, Arrays.asList(hits), indexName);
  }


  private SearchRequest createSearchQuery(String aliasName, long positionFrom, Long positionTo) {
    RangeQueryBuilder positionQ = rangeQuery(ImportPositionIndex.POSITION).gt(positionFrom);
    if (positionTo != null) {
      positionQ = positionQ.lte(positionTo);
    }
    final QueryBuilder queryBuilder = joinWithAnd(positionQ,
        termQuery(PARTITION_ID_FIELD_NAME, partitionId));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(queryBuilder).sort(ImportPositionIndex.POSITION, SortOrder.ASC);
    if (positionTo == null) {
      searchSourceBuilder = searchSourceBuilder.size(operateProperties.getZeebeElasticsearch().getBatchSize());
    } else {
      logger.debug("Import batch reread was called. Data type {}, partitionId {}, positionFrom {}, positionTo {}.", importValueType, partitionId, positionFrom, positionTo);
      int size = (int)(positionTo - positionFrom);
      searchSourceBuilder = searchSourceBuilder.size(size <= 0 || size > QUERY_MAX_SIZE ? QUERY_MAX_SIZE : size); //this size will be bigger than needed
    }
    return new SearchRequest(aliasName)
        .source(searchSourceBuilder)
        .routing(String.valueOf(partitionId))
        .requestCache(false);
  }

  private SearchResponse withTimer(Callable<SearchResponse> callable) throws Exception {
    return metrics.getTimer(Metrics.TIMER_NAME_IMPORT_QUERY).recordCallable(callable);
  }

  public void scheduleImport(ImportJob importJob) {
    boolean scheduled = false;
    while (!scheduled) {
      scheduled = importJobs.offer(() -> {
        try {
          Boolean imported = importJob.call();
          if (imported) {
            executeNext();
          } else {
            //retry the same job
            sleepFor(2000L);
            execute(active);
          }
          return imported;
        } catch (Exception ex) {
          logger.error("Exception occurred when importing data: " + ex.getMessage(), ex);
          //retry the same job
          sleepFor(2000L);
          execute(active);
          return false;
        }
      });
      if (!scheduled) {
        doBackoffForScheduler();
      }
    }
    if (active == null) {
      executeNext();
    }
  }

  /**
   * Freeze the scheduler (usually when queue is full).
   */
  private void doBackoffForScheduler() {
    int schedulerBackoff = operateProperties.getImporter().getSchedulerBackoff();
    if (schedulerBackoff > 0) {
      sleepFor(schedulerBackoff);
    }
  }

  private void executeNext() {
    if ((active = importJobs.poll()) != null) {
      Future<Boolean> result = importExecutor.submit(active);
      //TODO what to do with failing jobs
      logger.debug("Submitted next job");
    }
  }

  private void execute(Callable<Boolean> job) {
    Future<Boolean> result = importExecutor.submit(job);
    //TODO what to do with failing jobs
    logger.debug("Submitted the same job");
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

}


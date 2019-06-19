/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.camunda.operate.es.schema.indices.ImportPositionIndex;
import org.camunda.operate.exceptions.NoSuchIndexException;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.record.RecordImpl;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

/**
 * Represents Zeebe data reader for one partition and one value type. After reading the data is also schedules the jobs
 * for import execution. Each reader can have it's own backoff, so that we make a pause in case there is no data currently
 * for given partition and value type.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class RecordsReader {

  private static final Logger logger = LoggerFactory.getLogger(RecordsReader.class);

  public static final String PARTITION_ID_FIELD_NAME = "metadata." + ImportPositionIndex.PARTITION_ID;

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

  /**
   * Time, when the reader must be activated again after backoff.
   */
  private OffsetDateTime activateDateTime = OffsetDateTime.now().minusMinutes(1L);

  @Autowired
  @Qualifier("importThreadPoolExecutor")
  private ThreadPoolTaskExecutor importExecutor;

  @Autowired
  private ImportPositionHolder importPositionHolder;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private BeanFactory beanFactory;

  private ImportListener importListener;
  
  public RecordsReader(int partitionId, ImportValueType importValueType, int queueSize) {
    this.partitionId = partitionId;
    this.importValueType = importValueType;
    this.importJobs = new LinkedBlockingQueue<>(queueSize);
  }

  public int readAndScheduleNextBatch() {
    try {
      ImportBatch importBatch = readNextBatch();
      if (importBatch.getRecords().size() == 0) {
        doBackoff();
      } else {
        scheduleImport(importBatch);
      }
      return importBatch.getRecords().size();
    } catch (NoSuchIndexException ex) {
      //if no index found, we back off current reader
      doBackoff();
      return 0;
    }
  }

  private void scheduleImport(ImportBatch importBatch) {
    //create new instance of import job
    ImportJob importJob = beanFactory.getBean(ImportJob.class, importBatch);
    scheduleImport(importJob);
    recordLatestScheduledPosition(importBatch);
  }

  private void recordLatestScheduledPosition(ImportBatch importBatch) {
    final long lastScheduledPosition = importBatch.getRecords().get(importBatch.getRecordsCount() - 1).getPosition();
    importPositionHolder.recordLatestScheduledPosition(importBatch.getImportValueType().getAliasTemplate(), importBatch.getPartitionId(),
        lastScheduledPosition);
  }

  private ImportBatch readNextBatch() throws NoSuchIndexException {
    String aliasName = importValueType.getAliasName(operateProperties.getZeebeElasticsearch().getPrefix());
    try {

      long positionAfter = importPositionHolder.getLatestScheduledPosition(importValueType.getAliasTemplate(), partitionId);

      final QueryBuilder queryBuilder = joinWithAnd(
          rangeQuery(ImportPositionIndex.POSITION).gt(positionAfter),
          termQuery(PARTITION_ID_FIELD_NAME, partitionId));

      final SearchRequest searchRequest = new SearchRequest(aliasName)
          .source(new SearchSourceBuilder()
              .query(queryBuilder)
              .sort(ImportPositionIndex.POSITION, SortOrder.ASC)
              .size(operateProperties.getZeebeElasticsearch().getBatchSize()));

      final SearchResponse searchResponse =
          zeebeEsClient.search(searchRequest, RequestOptions.DEFAULT);

      JavaType valueType = objectMapper.getTypeFactory().constructParametricType(RecordImpl.class, importValueType.getRecordValueClass());
      final List<Record> result = ElasticsearchUtil.mapSearchHits(searchResponse.getHits().getHits(), objectMapper, valueType);

      return new ImportBatch(partitionId, importValueType, result, importListener);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining next Zeebe records batch: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    } catch (ElasticsearchStatusException ex) {
      if (ex.getMessage().contains("no such index")) {
        logger.debug("No index found for alias {}", aliasName);
        throw new NoSuchIndexException();
      } else {
        final String message = String.format("Exception occurred, while obtaining next Zeebe records batch: %s", ex.getMessage());
        logger.error(message, ex);
        throw new OperateRuntimeException(message, ex);
      }
    }
  }

  public boolean isActive() {
    return activateDateTime.isBefore(OffsetDateTime.now());
  }

  /**
   * Backoff for this specific reader.
   */
  public void doBackoff() {
    int readerBackoff = operateProperties.getImportProperties().getReaderBackoff();
    if (readerBackoff > 0) {
      this.activateDateTime = OffsetDateTime.now().plus(readerBackoff, ChronoUnit.MILLIS);
    }
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
            execute(active);
          }
          return imported;
        } catch (Exception ex) {
          //retry the same job
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
    int schedulerBackoff = operateProperties.getImportProperties().getSchedulerBackoff();
    if (schedulerBackoff > 0) {
      try {
        Thread.sleep(schedulerBackoff);
      } catch (InterruptedException e) {
        Thread.interrupted();
      }
    }
  }

  private void executeNext() {
    if ((active = importJobs.poll()) != null) {
      Future<Boolean> result = importExecutor.submit(active);
      //TODO what to do with failing jobs
      logger.debug("Submitted active Job as Future {}",result);
    }
  }

  private void execute(Callable<Boolean> job) {
    Future<Boolean> result = importExecutor.submit(job);
    //TODO what to do with failing jobs
    logger.debug("Submitted Job as Future {}",result);
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

  public void setImportListener(ImportListener importListener) {
    this.importListener = importListener;
  }
}


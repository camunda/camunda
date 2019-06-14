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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
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
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.exporter.api.record.Record;
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
   * The job that we are currently busy with.
   */
  private Callable<Boolean> active;

  /**
   * Time, when the reader must be activated again after backoff.
   */
  private OffsetDateTime activateDateTime = OffsetDateTime.now().minusMinutes(1L);

  @Autowired
  @Qualifier("importExecutorService")
  private ExecutorService importExecutorService;

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
    try {
      importExecutorService.submit(importBatch);
      recordLatestScheduledPosition(importBatch);
    } catch (IllegalStateException ex) {
      //this can happen when the queue for this reader is full
      //log and ignore, will be retried next time
      logger.debug(String.format("Queue is full for import batch [%s, %s]. Will be retried with the next run.", importBatch.getPartitionId(),
          importBatch.getImportValueType()));
    }

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

      return beanFactory.getBean(ImportBatch.class, partitionId, importValueType, result, importListener);
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

  public int getPartitionId() {
    return partitionId;
  }

  public ImportValueType getImportValueType() {
    return importValueType;
  }

  public void setImportListener(ImportListener importListener) {
    this.importListener = importListener;
  }
}


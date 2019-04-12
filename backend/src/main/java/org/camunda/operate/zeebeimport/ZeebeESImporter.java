/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.operate.entities.meta.ImportPositionEntity;
import org.camunda.operate.es.schema.indices.ImportPositionIndex;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.record.RecordImpl;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Topology;
import io.zeebe.exporter.api.record.RecordValue;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
@DependsOn({"esClient", "zeebeEsClient"})
public class ZeebeESImporter extends Thread {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeESImporter.class);

  private static final ImportValueType[] IMPORT_VALUE_TYPES = new ImportValueType[]{
    ImportValueType.DEPLOYMENT,
    ImportValueType.WORKFLOW_INSTANCE,
    ImportValueType.JOB,
    ImportValueType.INCIDENT,
    ImportValueType.VARIABLE};

  public static final String PARTITION_ID_FIELD_NAME = "metadata." + ImportPositionIndex.PARTITION_ID;

  private Set<Integer> partitionIds = new HashSet<>();

  private boolean shutdown = false;

  /**
   * Lock object, that can be used to be informed about finished import.
   */
  private final Object importFinished = new Object();

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ZeebeClient zeebeClient;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ImportPositionIndex importPositionType;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;
  
  private Map<String,Long> lastLoadedPositions = new HashMap<>();

  @PreDestroy
  public void shutdown() {
    shutdown = true;
    synchronized (importFinished) {
      importFinished.notifyAll();
    }
  }

  public long getLatestLoadedPosition(String aliasName, int partitionId) throws IOException {
    String lastloadedPositionKey = aliasName + "-" + partitionId;
    if (lastLoadedPositions.containsKey(lastloadedPositionKey)) {
      long lastPosition = lastLoadedPositions.get(lastloadedPositionKey);
      logger.debug("Latest loaded position (from cache) for alias [{}] and partitionId [{}]: {}", aliasName, partitionId, lastPosition);
      return lastPosition;
    }
    final QueryBuilder queryBuilder = joinWithAnd(termQuery(ImportPositionIndex.ALIAS_NAME, aliasName),
      termQuery(ImportPositionIndex.PARTITION_ID, partitionId));

    final SearchRequest searchRequest = new SearchRequest(importPositionType.getAlias())
      .source(new SearchSourceBuilder()
      .query(queryBuilder)
      .size(10)
      .fetchSource(ImportPositionIndex.POSITION, null));

    final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final Iterator<SearchHit> hitIterator = searchResponse.getHits().iterator();

    long position = 0;

    if (hitIterator.hasNext()) {
      position = (Long)hitIterator.next().getSourceAsMap().get(ImportPositionIndex.POSITION);
    }
    logger.debug("Latest loaded position for alias [{}] and partitionId [{}]: {}", aliasName, partitionId, position);
    
    return position;
  }

  public void recordLatestLoadedPosition(String aliasName, int partitionId, long position) {
    ImportPositionEntity entity = new ImportPositionEntity(aliasName, partitionId, position);
    Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(ImportPositionIndex.POSITION, entity.getPosition());
    try {
      final UpdateRequest request = new UpdateRequest(importPositionType.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(updateFields)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      esClient.update(request, RequestOptions.DEFAULT);
      lastLoadedPositions.put(aliasName + "-" + partitionId, position);
    } catch (Exception e) {
      logger.error(String.format("Error occurred while persisting latest loaded position for %s",aliasName), e);
      throw new OperateRuntimeException(e);
    }
  }

  public List<RecordImpl> getNextBatch(String aliasName, int partitionId, long positionAfter, Class<? extends RecordValue> recordValueClass) {
    return getNextBatch(aliasName, partitionId, positionAfter, null, recordValueClass);
  }

  public List<RecordImpl> getNextBatch(String aliasName, int partitionId, long positionAfter, Long positionBefore, Class<? extends RecordValue> recordValueClass) {

    QueryBuilder positionBeforeQ = null;
    if(positionBefore != null) {
      positionBeforeQ = rangeQuery(ImportPositionIndex.POSITION).lt(positionBefore);
    }

    final QueryBuilder queryBuilder = joinWithAnd(
      rangeQuery(ImportPositionIndex.POSITION).gt(positionAfter),
      positionBeforeQ,
      termQuery(PARTITION_ID_FIELD_NAME, partitionId));

    final SearchRequest searchRequest = new SearchRequest(aliasName)
      .source(new SearchSourceBuilder()
        .query(queryBuilder)
        .sort(ImportPositionIndex.POSITION, SortOrder.ASC)
       .size(operateProperties.getZeebeElasticsearch().getBatchSize()));

    try {
      final SearchResponse searchResponse =
        zeebeEsClient.search(searchRequest, RequestOptions.DEFAULT);

      JavaType valueType = objectMapper.getTypeFactory().constructParametricType(RecordImpl.class, recordValueClass);
      final List<RecordImpl> result = ElasticsearchUtil.mapSearchHits(searchResponse.getHits().getHits(), objectMapper, valueType);

      return result;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining next Zeebe records batch: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  public void startImportingData() {
    if (operateProperties.isStartLoadingDataOnStartup()) {
      start();
    }
  }

  private void initPartitionListFromZeebe() {
    try {
      final Topology topology = zeebeClient.newTopologyRequest().send().join();
      final int partitionsCount = topology.getPartitionsCount();
      //generate list of partition ids
      for (int i = 0; i< partitionsCount; i++) {
        partitionIds.add(i);
      }
      if (partitionIds.size() == 0) {
        logger.warn("Partitions are not found. Import from Zeebe won't load any data.");
      } else {
        logger.debug("Following partition ids were found: {}", partitionIds);
      }
    } catch (Exception ex) { //TODO check exception class
      logger.warn("Error occurred when requesting partition ids from Zeebe: " + ex.getMessage(), ex);
      //ignore, if Zeebe is not available
    }
  }

  public Set<Integer> getPartitionIds() {
    if (partitionIds.size() == 0) {
      initPartitionListFromZeebe();
    }
    //if still not initialized, try to read from Elasticsearch, but not cache, as it can change with the time
    if (partitionIds.size() == 0) {
      return getPartitionsFromElasticsearch();
    }
    return partitionIds;
  }

  private Set<Integer> getPartitionsFromElasticsearch() {
    logger.debug("Requesting partition ids from elasticsearch");
    final String aggName = "partitions";
    SearchRequest searchRequest = new SearchRequest(ImportValueType.DEPLOYMENT.getAliasName(operateProperties.getZeebeElasticsearch().getPrefix()))
      .source(new SearchSourceBuilder()
        .aggregation(terms(aggName)
          .field(PARTITION_ID_FIELD_NAME)
          .size(ElasticsearchUtil.TERMS_AGG_SIZE)));
    try {
      final SearchResponse searchResponse = zeebeEsClient.search(searchRequest, RequestOptions.DEFAULT);
      final HashSet<Integer> partitionIds = ((Terms) searchResponse.getAggregations().get(aggName)).getBuckets().stream()
        .collect(HashSet::new, (set, bucket) -> set.add(Integer.valueOf(bucket.getKeyAsString())), (set1, set2) -> set1.addAll(set2));
      logger.debug("Following partition ids were found: {}", partitionIds);
      return partitionIds;
    } catch (Exception ex) {
      logger.warn("Error occurred when requesting partition ids from Elasticsearch: " + ex.getMessage(), ex);
      return new HashSet<>();
    }
  }

  @Override
  public void run() {
    logger.debug("Start importing data");
    while (!shutdown) {
      synchronized (importFinished) {
        try {
          if (processNextEntitiesBatch() == 0) {
            importFinished.notifyAll();
            doBackoff();
          }
        } catch (Exception ex) {
          //retry
          logger.error("Error occurred while importing Zeebe data. Will be retried.", ex);
          doBackoff();
        }
      }
    }
  }

  public Object getImportFinished() {
    return importFinished;
  }

  private void doBackoff() {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public int processNextEntitiesBatch() throws PersistenceException, IOException {

    Integer processedEntities = 0;

    for (ImportValueType importValueType : IMPORT_VALUE_TYPES) {
      processedEntities = processNextEntitiesBatch(processedEntities, importValueType);
    }

    return processedEntities;
  }

  public Integer processNextEntitiesBatch(Integer processedEntities, ImportValueType importValueType) throws PersistenceException, IOException {
    String aliasName = importValueType.getAliasName(operateProperties.getZeebeElasticsearch().getPrefix());
    try {

      for (Integer partitionId : getPartitionIds()) {
        final long latestLoadedPosition = getLatestLoadedPosition(aliasName, partitionId);
        List<RecordImpl> nextBatch = getNextBatch(aliasName, partitionId, latestLoadedPosition, importValueType.getRecordValueClass());
        if (nextBatch.size() > 0) {

          elasticsearchBulkProcessor.persistZeebeRecords(nextBatch);

          final long lastProcessedPosition = nextBatch.get(nextBatch.size() - 1).getPosition();
          recordLatestLoadedPosition(aliasName, partitionId, lastProcessedPosition);
          processedEntities += nextBatch.size();
        }
      }
    } catch (ElasticsearchStatusException ex) {
      if (ex.status().equals(RestStatus.NOT_FOUND)) {
        logger.info("Elasticsearch index for ValueType {} was not found, alias {}. Skipping.", importValueType.getValueType(), aliasName);
      } else {
        throw ex;
      }
    } catch (SearchPhaseExecutionException ex) {
      logger.error(ex.getMessage(), ex);
    }
    return processedEntities;
  }

}

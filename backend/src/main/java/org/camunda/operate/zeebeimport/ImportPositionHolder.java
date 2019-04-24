/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.camunda.operate.entities.meta.ImportPositionEntity;
import org.camunda.operate.es.schema.indices.ImportPositionIndex;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ImportPositionHolder {

  private static final Logger logger = LoggerFactory.getLogger(ImportPositionHolder.class);

  //this is the in-memory only storage
  private Map<String,Long> lastScheduledPositions = new HashMap<>();

  @Autowired
  private ImportPositionIndex importPositionType;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  public long getLatestScheduledPosition(String aliasTemplate, int partitionId) throws IOException {
    String key = getKey(aliasTemplate, partitionId);
    if (lastScheduledPositions.containsKey(key)) {
      return lastScheduledPositions.get(key);
    } else {
      long latestLoadedPosition = getLatestLoadedPosition(aliasTemplate, partitionId);
      lastScheduledPositions.put(key, latestLoadedPosition);
      return latestLoadedPosition;
    }
  }

  public void recordLatestScheduledPosition(String aliasTemplate, int partitionId, long position) {
    lastScheduledPositions.put(getKey(aliasTemplate, partitionId), position);
  }

  public long getLatestLoadedPosition(String aliasTemplate, int partitionId) throws IOException {
    final QueryBuilder queryBuilder = joinWithAnd(termQuery(ImportPositionIndex.ALIAS_NAME, aliasTemplate),
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
    logger.debug("Latest loaded position for alias [{}] and partitionId [{}]: {}", aliasTemplate, partitionId, position);

    return position;
  }

  public void recordLatestLoadedPosition(String aliasTemplate, int partitionId, long position) {
    ImportPositionEntity entity = new ImportPositionEntity(aliasTemplate, partitionId, position);
    Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(ImportPositionIndex.POSITION, entity.getPosition());
    try {
      final UpdateRequest request = new UpdateRequest(importPositionType.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
          .doc(updateFields)
          .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      esClient.update(request, RequestOptions.DEFAULT);
    } catch (Exception e) {
      logger.error(String.format("Error occurred while persisting latest loaded position for %s", aliasTemplate), e);
      throw new OperateRuntimeException(e);
    }
  }

  public void clearCache() {
    lastScheduledPositions.clear();
  }

  private String getKey(String aliasTemplate, int partitionId) {
    return String.format("%s-%d", aliasTemplate, partitionId);
  }

}

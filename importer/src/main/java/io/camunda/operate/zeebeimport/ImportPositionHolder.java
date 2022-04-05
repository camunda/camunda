/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ImportPositionHolder {

  private static final Logger logger = LoggerFactory.getLogger(ImportPositionHolder.class);

  //this is the in-memory only storage
  private Map<String, ImportPositionEntity> lastScheduledPositions = new HashMap<>();

  @Autowired
  private ImportPositionIndex importPositionType;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  public ImportPositionEntity getLatestScheduledPosition(String aliasTemplate, int partitionId) throws IOException {
    String key = getKey(aliasTemplate, partitionId);
    if (lastScheduledPositions.containsKey(key)) {
      return lastScheduledPositions.get(key);
    } else {
      ImportPositionEntity latestLoadedPosition = getLatestLoadedPosition(aliasTemplate, partitionId);
      lastScheduledPositions.put(key, latestLoadedPosition);
      return latestLoadedPosition;
    }
  }

  public void recordLatestScheduledPosition(String aliasName, int partitionId, ImportPositionEntity importPositionEntity) {
    lastScheduledPositions.put(getKey(aliasName, partitionId), importPositionEntity);
  }

  public ImportPositionEntity getLatestLoadedPosition(String aliasTemplate, int partitionId) throws IOException {
    final QueryBuilder queryBuilder = joinWithAnd(termQuery(ImportPositionIndex.ALIAS_NAME, aliasTemplate),
        termQuery(ImportPositionIndex.PARTITION_ID, partitionId));

    final SearchRequest searchRequest = new SearchRequest(importPositionType.getAlias())
        .source(new SearchSourceBuilder()
            .query(queryBuilder)
            .size(10));

    final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final Iterator<SearchHit> hitIterator = searchResponse.getHits().iterator();

    ImportPositionEntity position = new ImportPositionEntity(aliasTemplate, partitionId, 0);

    if (hitIterator.hasNext()) {
      position = ElasticsearchUtil.fromSearchHit(hitIterator.next().getSourceAsString(), objectMapper, ImportPositionEntity.class);
    }
    logger.debug("Latest loaded position for alias [{}] and partitionId [{}]: {}", aliasTemplate, partitionId, position);

    return position;
  }
/**
 * @param lastProcessedPosition
 */
  public void recordLatestLoadedPosition(ImportPositionEntity lastProcessedPosition) {
    Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(ImportPositionIndex.POSITION, lastProcessedPosition.getPosition());
    updateFields.put(ImportPositionIndex.FIELD_INDEX_NAME, lastProcessedPosition.getIndexName());
    try {
      final UpdateRequest request = new UpdateRequest().index(importPositionType.getFullQualifiedName()).id(lastProcessedPosition.getId())
          .upsert(objectMapper.writeValueAsString(lastProcessedPosition), XContentType.JSON)
          .doc(updateFields)
          .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      ElasticsearchUtil.executeUpdate(esClient, request);
    } catch (Exception e) {
      logger.error(String.format("Error occurred while persisting latest loaded position for %s", lastProcessedPosition.getAliasName()), e);
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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.es;

import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.zeebeimport.ImportPositionHolder;
import io.camunda.tasklist.zeebeimport.ImportPositionHolderAbstract;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("tasklistSchemaStartup")
@Conditional(ElasticSearchCondition.class)
public class ImportPositionHolderElasticSearch extends ImportPositionHolderAbstract
    implements ImportPositionHolder {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ImportPositionHolderElasticSearch.class);

  // this is the in-memory only storage

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Override
  public ImportPositionEntity getLatestLoadedPosition(
      final String aliasTemplate, final int partitionId) throws IOException {
    final QueryBuilder queryBuilder =
        joinWithAnd(
            termQuery(TasklistImportPositionIndex.ALIAS_NAME, aliasTemplate),
            termQuery(TasklistImportPositionIndex.PARTITION_ID, partitionId));

    final SearchRequest searchRequest =
        new SearchRequest(importPositionType.getAlias())
            .source(new SearchSourceBuilder().query(queryBuilder).size(10));

    final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final Iterator<SearchHit> hitIterator = searchResponse.getHits().iterator();

    ImportPositionEntity position =
        new ImportPositionEntity().setAliasName(aliasTemplate).setPartitionId(partitionId);

    if (hitIterator.hasNext()) {
      position =
          ElasticsearchUtil.fromSearchHit(
              hitIterator.next().getSourceAsString(), objectMapper, ImportPositionEntity.class);
    }
    LOGGER.debug(
        "Latest loaded position for alias [{}] and partitionId [{}]: {}",
        aliasTemplate,
        partitionId,
        position);

    return position;
  }

  @Override
  public Either<Throwable, Boolean> updateImportPositions(
      final Map<String, ImportPositionEntity> positions) {
    final var preparedBulkRequest = prepareBulkRequest(positions);

    if (preparedBulkRequest.isLeft()) {
      final var e = preparedBulkRequest.getLeft();
      return Either.left(e);
    }

    try {
      final var bulkRequest = preparedBulkRequest.get();

      withImportPositionTimer(
          () -> {
            ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);
            return null;
          });

      return Either.right(true);
    } catch (final Throwable e) {
      LOGGER.error("Error occurred while persisting latest loaded position", e);
      return Either.left(e);
    }
  }

  private Either<Exception, UpdateRequest> prepareUpdateRequest(
      final ImportPositionEntity position) {
    try {
      final var index = importPositionType.getFullQualifiedName();
      final var source = objectMapper.writeValueAsString(position);
      final var updateFields = new HashMap<String, Object>();

      updateFields.put(TasklistImportPositionIndex.POSITION, position.getPosition());
      updateFields.put(TasklistImportPositionIndex.FIELD_INDEX_NAME, position.getIndexName());
      updateFields.put(TasklistImportPositionIndex.SEQUENCE, position.getSequence());
      updateFields.put(TasklistImportPositionIndex.COMPLETED, position.getCompleted());

      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(index)
              .id(position.getId())
              .upsert(source, XContentType.JSON)
              .doc(updateFields);

      return Either.right(updateRequest);

    } catch (final Exception e) {
      LOGGER.error(
          String.format(
              "Error occurred while preparing request to update processed position for %s",
              position.getAliasName()),
          e);
      return Either.left(e);
    }
  }

  private Either<Exception, org.elasticsearch.action.bulk.BulkRequest> prepareBulkRequest(
      final Map<String, ImportPositionEntity> positions) {
    final var bulkRequest = new BulkRequest();

    if (positions.size() > 0) {
      final var preparedUpdateRequests =
          positions.values().stream()
              .map(this::prepareUpdateRequest)
              .collect(Either.collectorFoldingLeft());

      if (preparedUpdateRequests.isLeft()) {
        final var e = preparedUpdateRequests.getLeft();
        return Either.left(e);
      }

      preparedUpdateRequests.get().forEach(bulkRequest::add);
    }

    return Either.right(bulkRequest);
  }
}

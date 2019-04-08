/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.service.es.schema.type.CollectionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;


@Component
public class CollectionWriter {

  public static final String DEFAULT_COLLECTION_NAME = "New Collection";
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;

  @Autowired
  public CollectionWriter(RestHighLevelClient esClient,
                          ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public IdDto createNewCollectionAndReturnId(String userId) {
    logger.debug("Writing new collection to Elasticsearch");

    String id = IdGenerator.getNextId();

    SimpleCollectionDefinitionDto collection = new SimpleCollectionDefinitionDto();
    collection.setId(id);
    collection.setCreated(LocalDateUtil.getCurrentDateTime());
    collection.setLastModified(LocalDateUtil.getCurrentDateTime());
    collection.setOwner(userId);
    collection.setLastModifier(userId);
    collection.setName(DEFAULT_COLLECTION_NAME);

    try {
      IndexRequest request = new IndexRequest(getOptimizeIndexAliasForType(COLLECTION_TYPE), COLLECTION_TYPE, id)
        .source(objectMapper.writeValueAsString(collection), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write collection to Elasticsearch. ";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create collection.";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    logger.debug("Collection with id [{}] has successfully been created.", id);
    IdDto idDto = new IdDto();
    idDto.setId(id);
    return idDto;
  }

  public void updateCollection(CollectionDefinitionUpdateDto collection, String id) {
    logger.debug("Updating collection with id [{}] in Elasticsearch", id);

    ensureThatAllProvidedEntityIdsExist(collection.getData());
    try {
      UpdateRequest request =
        new UpdateRequest(getOptimizeIndexAliasForType(COLLECTION_TYPE), COLLECTION_TYPE, id)
        .doc(objectMapper.writeValueAsString(collection), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      UpdateResponse updateResponse = esClient.update(request, RequestOptions.DEFAULT);

      if (updateResponse.getShardInfo().getFailed() > 0) {
        logger.error(
          "Was not able to update collection with id [{}] and name [{}].",
          id,
          collection.getName()
        );
        throw new OptimizeRuntimeException("Was not able to update collection!");
      }
    } catch (IOException e) {
      String errorMessage = String.format(
        "Was not able to update collection with id [%s] and name [%s].",
        id,
        collection.getName()
      );
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update collection with id [%s] and name [%s]. Collection does not exist!",
        id,
        collection.getName()
      );
      logger.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  private void ensureThatAllProvidedEntityIdsExist(CollectionDataDto<String> collectionData) {
    boolean entityIdsAreProvided =
      collectionData != null && collectionData.getEntities() != null && !collectionData.getEntities()
        .isEmpty();
    if (entityIdsAreProvided) {
      List<String> entityIds = collectionData.getEntities();
      logger.debug("Checking that the given entity ids [{}] for a collection exist", entityIds);

      SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        .query(QueryBuilders.idsQuery().addIds(entityIds.toArray(new String[0])))
        .size(0);
      SearchRequest searchRequest =
        new SearchRequest()
          .indices(
            getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE),
            getOptimizeIndexAliasForType(SINGLE_DECISION_REPORT_TYPE),
            getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE),
            getOptimizeIndexAliasForType(DASHBOARD_TYPE)
          )
          .source(searchSourceBuilder);

      SearchResponse searchResponse;
      try {
        searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      } catch (IOException e) {
        String reason = "Was not able to fetch collections.";
        logger.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }

      if (searchResponse.getHits().getTotalHits() != entityIds.size()) {
        String errorMessage = "Could not update collection, since the update contains entity ids that " +
          "do not exist in Optimize any longer.";
        logger.error(errorMessage);
        throw new OptimizeRuntimeException(errorMessage);
      }
    }
  }

  public void removeEntityFromCollections(String entityId) {
    logger.debug("Removing entity [{}] from all collections.", entityId);
    Script removeEntityFromCollectionScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.data.entities.removeIf(id -> id.equals(params.idToRemove))",
      Collections.singletonMap("idToRemove", entityId)
    );

    NestedQueryBuilder query =
      QueryBuilders.nestedQuery(
        CollectionType.DATA,
        QueryBuilders.termQuery(CollectionType.DATA + "." + CollectionType.ENTITIES, entityId),
        ScoreMode.None
      );

    UpdateByQueryRequest request = new UpdateByQueryRequest(getOptimizeIndexAliasForType(COLLECTION_TYPE))
      .setAbortOnVersionConflict(false)
      .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
      .setQuery(query)
      .setScript(removeEntityFromCollectionScript)
      .setRefresh(true);

    BulkByScrollResponse bulkByScrollResponse;
    try {
      bulkByScrollResponse = esClient.updateByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not remove entity with id [%s] from collections.", entityId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!bulkByScrollResponse.getBulkFailures().isEmpty()) {
      String errorMessage =
        String.format(
          "Could not remove entity id [%s] from collection! Error response: %s",
          entityId,
          bulkByScrollResponse.getBulkFailures()
        );
      logger.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void deleteCollection(String collectionId) {
    logger.debug("Deleting collection with id [{}]", collectionId);
    DeleteRequest request =
      new DeleteRequest(getOptimizeIndexAliasForType(COLLECTION_TYPE), COLLECTION_TYPE, collectionId)
      .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete collection with id [%s]. ", collectionId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
      String message = String.format("Could not delete collection with id [%s]. Collection does not exist." +
                                       "Maybe it was already deleted by someone else?", collectionId);
      logger.error(message);
      throw new NotFoundException(message);
    }
  }

}

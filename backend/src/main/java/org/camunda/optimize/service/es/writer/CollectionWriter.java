/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
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
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;


@AllArgsConstructor
@Component
@Slf4j
public class CollectionWriter {
  public static final String DEFAULT_COLLECTION_NAME = "New Collection";

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter formatter;


  public IdDto createNewCollectionAndReturnId(String userId) {
    log.debug("Writing new collection to Elasticsearch");

    String id = IdGenerator.getNextId();

    SimpleCollectionDefinitionDto collection = new SimpleCollectionDefinitionDto();
    collection.setId(id);
    collection.setCreated(LocalDateUtil.getCurrentDateTime());
    collection.setLastModified(LocalDateUtil.getCurrentDateTime());
    collection.setOwner(userId);
    collection.setLastModifier(userId);
    collection.setName(DEFAULT_COLLECTION_NAME);

    try {
      IndexRequest request = new IndexRequest(COLLECTION_TYPE, COLLECTION_TYPE, id)
        .source(objectMapper.writeValueAsString(collection), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write collection to Elasticsearch. ";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create collection.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    log.debug("Collection with id [{}] has successfully been created.", id);
    IdDto idDto = new IdDto();
    idDto.setId(id);
    return idDto;
  }

  public void addEntityToCollection(String collectionId, String entityId, String userId) {
    log.debug("Adding entity with id [{}] to collection with id [{}] in Elasticsearch", collectionId, entityId);

    ensureThatAllProvidedEntityIdsExist(Collections.singletonList(entityId));


    try {
      final Map<String, Object> params = new HashMap<>();
      params.put("entity", entityId);
      params.put("lastModifier", userId);
      params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));


      String script = "if(!ctx._source.data.entities.contains(params.entity)){ " +
        "ctx._source.data.entities.add(params.entity); " +
        "ctx._source.lastModifier = params.lastModifier; " +
        "ctx._source.lastModified = params.lastModified; " +
        "}";
      final Script addEntityScript = ElasticsearchWriterUtil.createDefaultScript(script, params);

      UpdateRequest request = new UpdateRequest(COLLECTION_TYPE, COLLECTION_TYPE, collectionId)
        .script(addEntityScript)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      UpdateResponse updateResponse = esClient.update(request, RequestOptions.DEFAULT);

      if (updateResponse.getShardInfo().getFailed() > 0) {
        log.error("Was not able to update collection with id [{}].", collectionId);
        throw new OptimizeRuntimeException("Was not able to update collection!");
      }
    } catch (IOException e) {
      String errorMessage = String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update collection with id [%s]. Collection does not exist!",
        collectionId
      );
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }

  }


  public void updateCollection(CollectionDefinitionUpdateDto collection, String id) {
    log.debug("Updating collection with id [{}] in Elasticsearch", id);

    ensureThatAllProvidedEntityIdsExist(collection.getData());
    try {

      UpdateRequest request =
        new UpdateRequest(COLLECTION_TYPE, COLLECTION_TYPE, id)
          .doc(objectMapper.writeValueAsString(collection), XContentType.JSON)
          .setRefreshPolicy(IMMEDIATE)
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      UpdateResponse updateResponse = esClient.update(request, RequestOptions.DEFAULT);

      if (updateResponse.getShardInfo().getFailed() > 0) {
        log.error(
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
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update collection with id [%s] and name [%s]. Collection does not exist!",
        id,
        collection.getName()
      );
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  private void ensureThatAllProvidedEntityIdsExist(PartialCollectionDataDto collectionData) {
    boolean entityIdsAreProvided =
      collectionData != null && collectionData.getEntities() != null && !collectionData.getEntities()
        .isEmpty();
    if (entityIdsAreProvided) {
      ensureThatAllProvidedEntityIdsExist(collectionData.getEntities());
    }
  }

  private void ensureThatAllProvidedEntityIdsExist(final List<String> entityIds) {
    log.debug("Checking that the given entity ids [{}] for a collection exist", entityIds);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.idsQuery().addIds(entityIds.toArray(new String[0])))
      .size(0);
    SearchRequest searchRequest =
      new SearchRequest()
        .indices(SINGLE_PROCESS_REPORT_TYPE, SINGLE_DECISION_REPORT_TYPE, COMBINED_REPORT_TYPE, DASHBOARD_TYPE)
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = "Was not able to fetch collections.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getTotalHits() != entityIds.size()) {
      String errorMessage = "Could not update collection, since the update contains entity ids that " +
        "do not exist in Optimize any longer.";
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void removeEntityFromCollection(String collectionId, String entityId, String userId) {
    log.debug("Removing entity [{}] from collection [{}].", entityId, collectionId);

    Script removeEntityFromCollectionScript = getRemoveEntityFromCollectionScript(entityId);

    final UpdateRequest request = new UpdateRequest(COLLECTION_TYPE, COLLECTION_TYPE, collectionId)
      .script(removeEntityFromCollectionScript)
      .setRefreshPolicy(IMMEDIATE);


    try {
      UpdateResponse updateResponse = esClient.update(request, RequestOptions.DEFAULT);

      if (updateResponse.getShardInfo().getFailed() > 0) {
        log.error("Was not able to update collection with id [{}].", collectionId);
        throw new OptimizeRuntimeException("Was not able to update collection!");
      }
    } catch (IOException e) {
      String errorMessage = String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update collection with id [%s]. Collection does not exist!",
        collectionId
      );
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  public void removeEntityFromAllCollections(String entityId) {
    log.debug("Removing entity [{}] from all collections.", entityId);
    Script removeEntityFromCollectionScript = getRemoveEntityFromCollectionScript(entityId);

    NestedQueryBuilder query =
      QueryBuilders.nestedQuery(
        CollectionType.DATA,
        QueryBuilders.termQuery(CollectionType.DATA + "." + CollectionType.ENTITIES, entityId),
        ScoreMode.None
      );

    UpdateByQueryRequest request = new UpdateByQueryRequest(COLLECTION_TYPE)
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
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!bulkByScrollResponse.getBulkFailures().isEmpty()) {
      String errorMessage =
        String.format(
          "Could not remove entity id [%s] from collection! Error response: %s",
          entityId,
          bulkByScrollResponse.getBulkFailures()
        );
      log.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  private Script getRemoveEntityFromCollectionScript(final String entityId) {
    return ElasticsearchWriterUtil.createDefaultScript(
      "ctx._source.data.entities.removeIf(id -> id.equals(params.idToRemove))",
      Collections.singletonMap("idToRemove", entityId)
    );
  }

  public void deleteCollection(String collectionId) {
    log.debug("Deleting collection with id [{}]", collectionId);
    DeleteRequest request = new DeleteRequest(COLLECTION_TYPE, COLLECTION_TYPE, collectionId)
      .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete collection with id [%s]. ", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
      String message = String.format("Could not delete collection with id [%s]. Collection does not exist." +
                                       "Maybe it was already deleted by someone else?", collectionId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import static org.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.service.db.schema.index.CollectionIndex.DATA;
import static org.camunda.optimize.service.db.schema.index.CollectionIndex.SCOPE;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class CollectionWriterES implements CollectionWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter formatter;

  @Override
  public void persistCollection(
      final String id, final CollectionDefinitionDto collectionDefinitionDto) {
    try {
      final IndexRequest request =
          new IndexRequest(COLLECTION_INDEX_NAME)
              .id(id)
              .source(objectMapper.writeValueAsString(collectionDefinitionDto), XContentType.JSON)
              .setRefreshPolicy(IMMEDIATE);

      final IndexResponse indexResponse = esClient.index(request);

      if (!indexResponse.getResult().equals(DocWriteResponse.Result.CREATED)) {
        final String message = "Could not write collection to Elasticsearch. ";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (final IOException e) {
      final String errorMessage = "Could not create collection.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    log.debug("Collection with id [{}] has successfully been created.", id);
  }

  @Override
  public void updateCollection(final CollectionDefinitionUpdateDto collection, final String id) {
    log.debug("Updating collection with id [{}] in Elasticsearch", id);

    try {
      final UpdateRequest request =
          new UpdateRequest()
              .index(COLLECTION_INDEX_NAME)
              .id(id)
              .doc(objectMapper.writeValueAsString(collection), XContentType.JSON)
              .setRefreshPolicy(IMMEDIATE)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      final UpdateResponse updateResponse = esClient.update(request);

      if (updateResponse.getShardInfo().getFailed() > 0) {
        log.error(
            "Was not able to update collection with id [{}] and name [{}].",
            id,
            collection.getName());
        throw new OptimizeRuntimeException("Was not able to update collection!");
      }
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Was not able to update collection with id [%s] and name [%s].",
              id, collection.getName());
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final ElasticsearchStatusException e) {
      final String errorMessage =
          String.format(
              "Was not able to update collection with id [%s] and name [%s]. Collection does not exist!",
              id, collection.getName());
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  @Override
  public void deleteCollection(final String collectionId) {
    log.debug("Deleting collection with id [{}]", collectionId);
    final DeleteRequest request =
        new DeleteRequest(COLLECTION_INDEX_NAME).id(collectionId).setRefreshPolicy(IMMEDIATE);

    final DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request);
    } catch (final IOException e) {
      final String reason =
          String.format("Could not delete collection with id [%s]. ", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DocWriteResponse.Result.DELETED)) {
      final String message =
          String.format(
              "Could not delete collection with id [%s]. Collection does not exist. "
                  + "Maybe it was already deleted by someone else?",
              collectionId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

  @Override
  public void addScopeEntriesToCollection(
      final String userId,
      final String collectionId,
      final List<CollectionScopeEntryDto> scopeUpdates) {
    try {
      final Map<String, Object> params = new HashMap<>();
      params.put("scopeEntriesToUpdate", scopeUpdates);
      params.put("lastModifier", userId);
      params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));
      final Script updateEntityScript =
          createDefaultScriptWithSpecificDtoParams(UPDATE_ENTITY_SCRIPT_CODE, params, objectMapper);

      final UpdateResponse updateResponse;
      updateResponse =
          executeUpdateRequest(
              collectionId, updateEntityScript, "Was not able to update collection with id [%s].");

      if (updateResponse.getResult().equals(DocWriteResponse.Result.NOT_FOUND)) {
        final String message =
            String.format(
                "Was not able to add scope entries to collection with id [%s]. Collection does not exist!",
                collectionId);
        log.error(message);
        throw new NotFoundException(message);
      }
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Wasn't able to add scope entries to collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void deleteScopeEntryFromAllCollections(final String scopeEntryId) {
    final String updateItem = String.format("collection scope entry with ID [%s].", scopeEntryId);
    log.info("Removing {} from all collections.", updateItem);

    final Script removeScopeEntryFromCollectionsScript =
        new Script(
            ScriptType.INLINE,
            Script.DEFAULT_SCRIPT_LANG,
            REMOVE_SCOPE_ENTRY_FROM_COLLECTION_SCRIPT_CODE,
            Collections.singletonMap("scopeEntryIdToRemove", scopeEntryId));

    final NestedQueryBuilder query =
        nestedQuery(
            DATA,
            nestedQuery(
                String.join(".", DATA, SCOPE),
                termQuery(
                    String.join(".", DATA, SCOPE, CollectionScopeEntryDto.Fields.id.name()),
                    scopeEntryId),
                ScoreMode.None),
            ScoreMode.None);

    ElasticsearchWriterUtil.tryUpdateByQueryRequest(
        esClient, updateItem, removeScopeEntryFromCollectionsScript, query, COLLECTION_INDEX_NAME);
  }

  @Override
  public void removeScopeEntry(
      final String collectionId, final String scopeEntryId, final String userId)
      throws NotFoundException {
    try {
      final Map<String, Object> params = new HashMap<>();
      params.put("id", scopeEntryId);
      params.put("lastModifier", userId);
      params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));

      final Script updateEntityScript =
          ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
              REMOVE_SCOPE_ENTRY_SCRIPT_CODE, params);

      final UpdateResponse updateResponse =
          executeUpdateRequest(
              collectionId, updateEntityScript, "Was not able to update collection with id [%s].");

      if (updateResponse.getResult().equals(DocWriteResponse.Result.NOOP)) {
        final String message =
            String.format("Scope entry for id [%s] doesn't exist.", scopeEntryId);
        log.warn(message);
        throw new NotFoundException(message);
      }

    } catch (final IOException e) {
      final String errorMessage =
          String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void removeScopeEntries(
      final String collectionId, final List<String> scopeEntryIds, final String userId)
      throws NotFoundException {
    final Map<String, Object> params = new HashMap<>();
    params.put("ids", scopeEntryIds);
    params.put("lastModifier", userId);
    params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));

    final Script updateEntityScript =
        ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
            REMOVE_SCOPE_ENTRIES_SCRIPT_CODE, params);
    try {
      executeUpdateRequest(
          collectionId, updateEntityScript, "Was not able to update collection with id [%s].");
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "The scope with ids %s could not be removed from the collection %s.",
              scopeEntryIds, collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void updateScopeEntity(
      final String collectionId,
      final CollectionScopeEntryUpdateDto scopeEntry,
      final String userId,
      final String scopeEntryId) {
    try {
      final Map<String, Object> params = new HashMap<>();
      params.put("entryDto", scopeEntry);
      params.put("entryId", scopeEntryId);
      params.put("lastModifier", userId);
      params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));

      final Script updateEntityScript =
          createDefaultScriptWithSpecificDtoParams(
              UPDATE_SCOPE_ENTITY_SCRIPT_CODE, params, objectMapper);

      executeUpdateRequest(
          collectionId, updateEntityScript, "Was not able to update collection with id [%s].");

    } catch (final IOException e) {
      final String errorMessage =
          String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final ElasticsearchStatusException e) {
      final String errorMessage =
          String.format(
              "Was not able to update scope entry with id [%s] on collection with id [%s]."
                  + " Collection or scope Entry does not exist!",
              scopeEntryId, collectionId);
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  private UpdateResponse executeUpdateRequest(
      final String collectionId, final Script updateEntityScript, final String errorMessage)
      throws IOException {
    final UpdateRequest request =
        new UpdateRequest()
            .index(COLLECTION_INDEX_NAME)
            .id(collectionId)
            .script(updateEntityScript)
            .setRefreshPolicy(IMMEDIATE)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    final UpdateResponse updateResponse = esClient.update(request);

    if (updateResponse.getShardInfo().getFailed() > 0) {
      final String message = String.format(errorMessage, collectionId);
      log.error(message, collectionId);
      throw new OptimizeRuntimeException(message);
    }
    return updateResponse;
  }

  @Override
  public void addRoleToCollection(
      final String collectionId,
      final List<CollectionRoleRequestDto> rolesToAdd,
      final String userId) {
    log.debug(
        "Adding roles {} to collection with id [{}] in Elasticsearch.", rolesToAdd, collectionId);

    try {
      final Map<String, Object> params = new HashMap<>();
      params.put("rolesToAdd", rolesToAdd);
      params.put("lastModifier", userId);
      params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));

      final Script addEntityScript =
          createDefaultScriptWithSpecificDtoParams(
              ADD_ROLE_TO_COLLECTION_SCRIPT_CODE, params, objectMapper);

      final UpdateResponse updateResponse =
          executeUpdateRequest(
              collectionId, addEntityScript, "Was not able to update collection with id [%s].");

      if (updateResponse.getResult().equals(DocWriteResponse.Result.NOOP)) {
        final String message =
            String.format(
                "One of the roles %s already exists in collection [%s].", rolesToAdd, collectionId);
        log.warn(message);
        throw new OptimizeCollectionConflictException(message);
      }
    } catch (final IOException e) {
      final String errorMessage =
          String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final ElasticsearchStatusException e) {
      final String errorMessage =
          String.format(
              "Was not able to update collection with id [%s]. Collection does not exist!",
              collectionId);
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  @Override
  public void updateRoleInCollection(
      final String collectionId,
      final String roleEntryId,
      final CollectionRoleUpdateRequestDto roleUpdateDto,
      final String userId)
      throws OptimizeConflictException {
    log.debug(
        "Updating the role [{}] in collection with id [{}] in Elasticsearch.",
        roleEntryId,
        collectionId);

    try {
      final Map<String, Object> params = constructParamsForRoleUpdateScript(roleEntryId, userId);
      params.put("role", roleUpdateDto.getRole().toString());

      final Script addEntityScript =
          ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
              UPDATE_ROLE_IN_COLLECTION_SCRIPT_CODE, params);

      final UpdateResponse updateResponse =
          executeUpdateRequest(
              collectionId, addEntityScript, "Was not able to update collection with id [%s].");

      if (updateResponse.getResult().equals(DocWriteResponse.Result.NOOP)) {
        final String message =
            String.format(
                "Cannot assign lower privileged role to last [%s] of collection [%s].",
                RoleType.MANAGER, collectionId);
        log.warn(message);
        throw new OptimizeCollectionConflictException(message);
      }
    } catch (final IOException e) {
      final String errorMessage =
          String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final ElasticsearchStatusException e) {
      final String errorMessage =
          String.format(
              "Was not able to update role with id [%s] on collection with id [%s]. Collection or role does not exist!",
              roleEntryId, collectionId);
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  @Override
  public void removeRoleFromCollectionUnlessIsLastManager(
      final String collectionId, final String roleEntryId, final String userId)
      throws OptimizeConflictException {
    final Map<String, Object> params = constructParamsForRoleUpdateScript(roleEntryId, userId);
    removeRoleFromCollectionUnlessIsLastManager(collectionId, roleEntryId, params);
  }

  @Override
  public void removeRoleFromCollection(final String collectionId, final String roleEntryId) {
    final Map<String, Object> params = constructParamsForRoleUpdateScript(roleEntryId, null);
    removeRoleFromCollection(collectionId, roleEntryId, params);
  }

  private void removeRoleFromCollection(
      final String collectionId, final String roleEntryId, final Map<String, Object> params) {
    log.debug(
        "Deleting the role [{}] in collection with id [{}] in Elasticsearch.",
        roleEntryId,
        collectionId);
    try {
      final Script addEntityScript =
          ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
              REMOVE_ROLE_FROM_COLLECTION_SCRIPT_CODE, params);

      executeUpdateRequest(
          collectionId,
          addEntityScript,
          "Was not able to delete role from collection with id [%s].");

    } catch (final IOException e) {
      final String errorMessage =
          String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final ElasticsearchStatusException e) {
      final String errorMessage =
          String.format(
              "Was not able to update role with id [%s] on collection with id [%s]. Collection or role does not exist!",
              roleEntryId, collectionId);
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  private void removeRoleFromCollectionUnlessIsLastManager(
      final String collectionId, final String roleEntryId, final Map<String, Object> params)
      throws OptimizeConflictException {
    log.debug(
        "Deleting the role [{}] in collection with id [{}] in Elasticsearch.",
        roleEntryId,
        collectionId);
    try {
      final Script addEntityScript =
          ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
              REMOVE_ROLE_FROM_COLLECTION_UNLESS_IS_LAST_MANAGER, params);

      final UpdateResponse updateResponse =
          executeUpdateRequest(
              collectionId,
              addEntityScript,
              "Was not able to delete role from collection with id [%s].");

      if (updateResponse.getResult() == DocWriteResponse.Result.NOOP) {
        final String message =
            String.format(
                "Cannot delete last [%s] of collection [%s].", RoleType.MANAGER, collectionId);
        log.warn(message);
        throw new OptimizeCollectionConflictException(message);
      }
    } catch (final IOException e) {
      final String errorMessage =
          String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final ElasticsearchStatusException e) {
      final String errorMessage =
          String.format(
              "Was not able to update role with id [%s] on collection with id [%s]. Collection or role does not exist!",
              roleEntryId, collectionId);
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  private Map<String, Object> constructParamsForRoleUpdateScript(
      final String roleEntryId, final String userId) {
    final Map<String, Object> params = new HashMap<>();
    params.put("roleEntryId", roleEntryId);
    params.put("managerRole", RoleType.MANAGER.toString());
    if (userId != null) {
      params.put("lastModifier", userId);
      params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));
    }
    return params;
  }
}

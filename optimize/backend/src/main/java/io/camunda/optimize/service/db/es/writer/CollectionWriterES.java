/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static co.elastic.clients.elasticsearch._types.Result.Created;
import static co.elastic.clients.elasticsearch._types.Result.Deleted;
import static co.elastic.clients.elasticsearch._types.Result.NoOp;
import static co.elastic.clients.elasticsearch._types.Result.NotFound;
import static io.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static io.camunda.optimize.service.db.schema.index.CollectionIndex.DATA;
import static io.camunda.optimize.service.db.schema.index.CollectionIndex.SCOPE;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.db.writer.CollectionWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  private final TaskRepositoryES taskRepositoryES;

  @Override
  public void updateCollection(final CollectionDefinitionUpdateDto collection, final String id) {
    log.debug("Updating collection with id [{}] in Elasticsearch", id);

    try {
      final UpdateResponse<CollectionDefinitionUpdateDto> updateResponse =
          esClient.update(
              new OptimizeUpdateRequestBuilderES<
                      CollectionDefinitionUpdateDto, CollectionDefinitionUpdateDto>()
                  .optimizeIndex(esClient, COLLECTION_INDEX_NAME)
                  .id(id)
                  .doc(collection)
                  .refresh(Refresh.True)
                  .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                  .build(),
              CollectionDefinitionUpdateDto.class);

      if (!updateResponse.shards().failures().isEmpty()) {
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
    } catch (final ElasticsearchException e) {
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
    final DeleteResponse deleteResponse;
    try {
      deleteResponse =
          esClient.delete(
              OptimizeDeleteRequestBuilderES.of(
                  d ->
                      d.optimizeIndex(esClient, COLLECTION_INDEX_NAME)
                          .id(collectionId)
                          .refresh(Refresh.True)));
    } catch (final IOException e) {
      final String reason =
          String.format("Could not delete collection with id [%s]. ", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.result().equals(Deleted)) {
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
          createDefaultScriptWithSpecificDtoParams(UPDATE_ENTITY_SCRIPT_CODE, params);

      final UpdateResponse<?> updateResponse;
      updateResponse =
          executeUpdateRequest(
              collectionId, updateEntityScript, "Was not able to update collection with id [%s].");

      if (updateResponse.result().equals(NotFound)) {
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
        Script.of(
            i ->
                i.lang(ScriptLanguage.Painless)
                    .params(Map.of("scopeEntryIdToRemove", JsonData.of(scopeEntryId)))
                    .source(REMOVE_SCOPE_ENTRY_FROM_COLLECTION_SCRIPT_CODE));

    final Query query =
        Query.of(
            q ->
                q.nested(
                    n ->
                        n.path(DATA)
                            .scoreMode(ChildScoreMode.None)
                            .query(
                                Query.of(
                                    qq ->
                                        qq.nested(
                                            nn ->
                                                nn.path(String.join(".", DATA, SCOPE))
                                                    .scoreMode(ChildScoreMode.None)
                                                    .query(
                                                        qqq ->
                                                            qqq.term(
                                                                t ->
                                                                    t.field(
                                                                            String.join(
                                                                                ".",
                                                                                DATA,
                                                                                SCOPE,
                                                                                CollectionScopeEntryDto
                                                                                    .Fields.id
                                                                                    .name()))
                                                                        .value(scopeEntryId))))))));

    taskRepositoryES.tryUpdateByQueryRequest(
        updateItem, removeScopeEntryFromCollectionsScript, query, COLLECTION_INDEX_NAME);
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
          createDefaultScriptWithSpecificDtoParams(UPDATE_SCOPE_ENTITY_SCRIPT_CODE, params);

      executeUpdateRequest(
          collectionId, updateEntityScript, "Was not able to update collection with id [%s].");

    } catch (final IOException e) {
      final String errorMessage =
          String.format("Was not able to update collection with id [%s].", collectionId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final ElasticsearchException e) {
      final String errorMessage =
          String.format(
              "Was not able to update scope entry with id [%s] on collection with id [%s]."
                  + " Collection or scope Entry does not exist!",
              scopeEntryId, collectionId);
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
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

      if (updateResponse.result().equals(NoOp)) {
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
          createDefaultScriptWithSpecificDtoParams(ADD_ROLE_TO_COLLECTION_SCRIPT_CODE, params);

      final UpdateResponse<?> updateResponse =
          executeUpdateRequest(
              collectionId, addEntityScript, "Was not able to update collection with id [%s].");

      if (updateResponse.result().equals(NoOp)) {
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
    } catch (final ElasticsearchException e) {
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
      final Map<String, String> params = constructParamsForRoleUpdateScript(roleEntryId, userId);
      params.put("role", roleUpdateDto.getRole().toString());

      final Script addEntityScript =
          ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
              UPDATE_ROLE_IN_COLLECTION_SCRIPT_CODE, params);

      final UpdateResponse<?> updateResponse =
          executeUpdateRequest(
              collectionId, addEntityScript, "Was not able to update collection with id [%s].");

      if (updateResponse.result().equals(NoOp)) {
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
    } catch (final ElasticsearchException e) {
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
    final Map<String, String> params = constructParamsForRoleUpdateScript(roleEntryId, userId);
    removeRoleFromCollectionUnlessIsLastManager(collectionId, roleEntryId, params);
  }

  @Override
  public void persistCollection(
      final String id, final CollectionDefinitionDto collectionDefinitionDto) {
    try {
      final IndexResponse indexResponse =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  i ->
                      i.optimizeIndex(esClient, COLLECTION_INDEX_NAME)
                          .id(id)
                          .document(collectionDefinitionDto)
                          .refresh(Refresh.True)));

      if (!indexResponse.result().equals(Created)) {
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

  private UpdateResponse<?> executeUpdateRequest(
      final String collectionId, final Script updateEntityScript, final String errorMessage)
      throws IOException {

    final UpdateResponse<?> updateResponse =
        esClient.update(
            new OptimizeUpdateRequestBuilderES<>()
                .optimizeIndex(esClient, COLLECTION_INDEX_NAME)
                .id(collectionId)
                .script(updateEntityScript)
                .refresh(Refresh.True)
                .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                .build(),
            Object.class);

    if (!updateResponse.shards().failures().isEmpty()) {
      final String message = String.format(errorMessage, collectionId);
      log.error(message, collectionId);
      throw new OptimizeRuntimeException(message);
    }
    return updateResponse;
  }

  private void removeRoleFromCollectionUnlessIsLastManager(
      final String collectionId, final String roleEntryId, final Map<String, String> params)
      throws OptimizeConflictException {
    log.debug(
        "Deleting the role [{}] in collection with id [{}] in Elasticsearch.",
        roleEntryId,
        collectionId);
    try {
      final Script addEntityScript =
          ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
              REMOVE_ROLE_FROM_COLLECTION_UNLESS_IS_LAST_MANAGER, params);

      final UpdateResponse<?> updateResponse =
          executeUpdateRequest(
              collectionId,
              addEntityScript,
              "Was not able to delete role from collection with id [%s].");

      if (updateResponse.result() == NoOp) {
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
    } catch (final ElasticsearchException e) {
      final String errorMessage =
          String.format(
              "Was not able to update role with id [%s] on collection with id [%s]. Collection or role does not exist!",
              roleEntryId, collectionId);
      log.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  private Map<String, String> constructParamsForRoleUpdateScript(
      final String roleEntryId, final String userId) {
    final Map<String, String> params = new HashMap<>();
    params.put("roleEntryId", roleEntryId);
    params.put("managerRole", RoleType.MANAGER.toString());
    if (userId != null) {
      params.put("lastModifier", userId);
      params.put("lastModified", formatter.format(LocalDateUtil.getCurrentDateTime()));
    }
    return params;
  }
}

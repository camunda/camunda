/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.schema.index.CollectionIndex.DATA;
import static io.camunda.optimize.service.db.schema.index.CollectionIndex.SCOPE;

import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.writer.CollectionWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import jakarta.ws.rs.NotFoundException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class CollectionWriterOS implements CollectionWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(CollectionWriterOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final DateTimeFormatter formatter;

  public CollectionWriterOS(
      final OptimizeOpenSearchClient osClient, final DateTimeFormatter formatter) {
    this.osClient = osClient;
    this.formatter = formatter;
  }

  @Override
  public void updateCollection(final CollectionDefinitionUpdateDto collection, final String id) {
    LOG.debug("Updating collection with id [{}] in OpenSearch", id);

    final UpdateRequest.Builder request =
        new UpdateRequest.Builder<>()
            .index(COLLECTION_INDEX_NAME)
            .id(id)
            .doc(collection)
            .refresh(Refresh.True)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    final String errorMessage =
        String.format(
            "Was not able to update collection with id [%s] and name [%s].",
            id, collection.getName());
    final UpdateResponse updateResponse = osClient.update(request, errorMessage);

    if (updateResponse.shards().failed().intValue() > 0) {
      LOG.error(
          "Was not able to update collection with id [{}] and name [{}].",
          id,
          collection.getName());
      throw new OptimizeRuntimeException("Was not able to update collection!");
    }
  }

  @Override
  public void deleteCollection(final String collectionId) {
    LOG.debug("Deleting collection with id [{}]", collectionId);
    final DeleteRequest.Builder request =
        new DeleteRequest.Builder()
            .index(COLLECTION_INDEX_NAME)
            .id(collectionId)
            .refresh(Refresh.True);

    final String errorMessage =
        String.format("Could not delete collection with id [%s]. ", collectionId);
    final DeleteResponse deleteResponse = osClient.delete(request, errorMessage);

    if (!deleteResponse.result().equals(Result.Deleted)) {
      final String message =
          String.format(
              "Could not delete collection with id [%s]. Collection does not exist. "
                  + "Maybe it was already deleted by someone else?",
              collectionId);
      LOG.error(message);
      throw new NotFoundException(message);
    }
  }

  @Override
  public void addScopeEntriesToCollection(
      final String userId,
      final String collectionId,
      final List<CollectionScopeEntryDto> scopeUpdates) {
    final Map<String, JsonData> params = new HashMap<>();
    params.put("scopeEntriesToUpdate", JsonData.of(scopeUpdates));
    params.put("lastModifier", JsonData.of(userId));
    params.put("lastModified", JsonData.of(formatter.format(LocalDateUtil.getCurrentDateTime())));
    final Script updateEntityScript =
        OpenSearchWriterUtil.createDefaultScriptWithSpecificDtoParams(
            UPDATE_ENTITY_SCRIPT_CODE, params);

    final UpdateResponse updateResponse;
    updateResponse =
        executeUpdateRequest(
            collectionId, updateEntityScript, "Was not able to update collection with id [%s].");

    if (updateResponse.result().equals(Result.NotFound)) {
      final String message =
          String.format(
              "Was not able to add scope entries to collection with id [%s]. Collection does not exist!",
              collectionId);
      LOG.error(message);
      throw new NotFoundException(message);
    }
  }

  @Override
  public void deleteScopeEntryFromAllCollections(final String scopeEntryId) {
    final String updateItem = String.format("collection scope entry with ID [%s].", scopeEntryId);
    LOG.info("Removing {} from all collections.", updateItem);

    final Script removeScopeEntryFromCollectionsScript =
        OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
            REMOVE_SCOPE_ENTRY_FROM_COLLECTION_SCRIPT_CODE,
            Collections.singletonMap("scopeEntryIdToRemove", JsonData.of(scopeEntryId)));

    final Query query =
        new NestedQuery.Builder()
            .path(DATA)
            .query(
                new NestedQuery.Builder()
                    .path(String.join(".", DATA, SCOPE))
                    .query(
                        QueryDSL.term(
                            String.join(".", DATA, SCOPE, CollectionScopeEntryDto.Fields.id.name()),
                            scopeEntryId))
                    .scoreMode(ChildScoreMode.None)
                    .build()
                    .toQuery())
            .scoreMode(ChildScoreMode.None)
            .build()
            .toQuery();

    osClient.updateByQuery(COLLECTION_INDEX_NAME, query, removeScopeEntryFromCollectionsScript);
  }

  @Override
  public void updateScopeEntity(
      final String collectionId,
      final CollectionScopeEntryUpdateDto scopeEntry,
      final String userId,
      final String scopeEntryId) {
    final Map<String, JsonData> params = new HashMap<>();
    params.put("entryDto", JsonData.of(scopeEntry));
    params.put("entryId", JsonData.of(scopeEntryId));
    params.put("lastModifier", JsonData.of(userId));
    params.put("lastModified", JsonData.of(formatter.format(LocalDateUtil.getCurrentDateTime())));

    final Script updateEntityScript =
        OpenSearchWriterUtil.createDefaultScriptWithSpecificDtoParams(
            UPDATE_SCOPE_ENTITY_SCRIPT_CODE, params);

    executeUpdateRequest(
        collectionId, updateEntityScript, "Was not able to update collection with id [%s].");
  }

  @Override
  public void removeScopeEntries(
      final String collectionId, final List<String> scopeEntryIds, final String userId)
      throws NotFoundException {
    final Map<String, JsonData> params = new HashMap<>();
    params.put("ids", JsonData.of(scopeEntryIds));
    params.put("lastModifier", JsonData.of(userId));
    params.put("lastModified", JsonData.of(formatter.format(LocalDateUtil.getCurrentDateTime())));

    final Script updateEntityScript =
        OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
            REMOVE_SCOPE_ENTRIES_SCRIPT_CODE, params);
    executeUpdateRequest(
        collectionId, updateEntityScript, "Was not able to update collection with id [%s].");
  }

  @Override
  public void removeScopeEntry(
      final String collectionId, final String scopeEntryId, final String userId)
      throws NotFoundException {
    final Map<String, JsonData> params = new HashMap<>();
    params.put("id", JsonData.of(scopeEntryId));
    params.put("lastModifier", JsonData.of(userId));
    params.put("lastModified", JsonData.of(formatter.format(LocalDateUtil.getCurrentDateTime())));

    final Script updateEntityScript =
        OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
            REMOVE_SCOPE_ENTRY_SCRIPT_CODE, params);

    final UpdateResponse updateResponse =
        executeUpdateRequest(
            collectionId, updateEntityScript, "Was not able to update collection with id [%s].");

    if (updateResponse.result().equals(Result.NoOp)) {
      final String message = String.format("Scope entry for id [%s] doesn't exist.", scopeEntryId);
      LOG.warn(message);
      throw new NotFoundException(message);
    }
  }

  @Override
  public void addRoleToCollection(
      final String collectionId,
      final List<CollectionRoleRequestDto> rolesToAdd,
      final String userId) {
    LOG.debug(
        "Adding roles {} to collection with id [{}] in OpenSearch.", rolesToAdd, collectionId);

    final Map<String, JsonData> params = new HashMap<>();
    params.put("rolesToAdd", JsonData.of(rolesToAdd));
    params.put("lastModifier", JsonData.of(userId));
    params.put("lastModified", JsonData.of(formatter.format(LocalDateUtil.getCurrentDateTime())));

    final Script addEntityScript =
        OpenSearchWriterUtil.createDefaultScriptWithSpecificDtoParams(
            ADD_ROLE_TO_COLLECTION_SCRIPT_CODE, params);

    final UpdateResponse updateResponse =
        executeUpdateRequest(
            collectionId, addEntityScript, "Was not able to update collection with id [%s].");

    if (updateResponse.result().equals(Result.NoOp)) {
      final String message =
          String.format(
              "One of the roles %s already exists in collection [%s].", rolesToAdd, collectionId);
      LOG.warn(message);
      throw new OptimizeCollectionConflictException(message);
    }
  }

  @Override
  public void updateRoleInCollection(
      final String collectionId,
      final String roleEntryId,
      final CollectionRoleUpdateRequestDto roleUpdateDto,
      final String userId)
      throws OptimizeConflictException {
    LOG.debug(
        "Updating the role [{}] in collection with id [{}] in OpenSearch.",
        roleEntryId,
        collectionId);

    final Map<String, JsonData> params = constructParamsForRoleUpdateScript(roleEntryId, userId);
    params.put("role", JsonData.of(roleUpdateDto.getRole().toString()));
    final Script addEntityScript =
        OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
            UPDATE_ROLE_IN_COLLECTION_SCRIPT_CODE, params);

    final UpdateResponse updateResponse;
    try {
      updateResponse =
          executeUpdateRequest(
              collectionId, addEntityScript, "Was not able to update collection with id [%s].");
    } catch (final OpenSearchException e) {
      final String errorMessage =
          String.format(
              "Was not able to update role with id [%s] on collection with id [%s]. Collection or role does not exist!",
              roleEntryId, collectionId);
      LOG.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }

    if (updateResponse.result().equals(Result.NoOp)) {
      final String message =
          String.format(
              "Cannot assign lower privileged role to last [%s] of collection [%s].",
              RoleType.MANAGER, collectionId);
      LOG.warn(message);
      throw new OptimizeCollectionConflictException(message);
    }
  }

  @Override
  public void removeRoleFromCollectionUnlessIsLastManager(
      final String collectionId, final String roleEntryId, final String userId)
      throws OptimizeConflictException {
    final Map<String, JsonData> params = constructParamsForRoleUpdateScript(roleEntryId, userId);
    removeRoleFromCollectionUnlessIsLastManager(collectionId, roleEntryId, params);
  }

  @Override
  public void persistCollection(
      final String id, final CollectionDefinitionDto collectionDefinitionDto) {
    final IndexRequest.Builder<CollectionDefinitionDto> request =
        new IndexRequest.Builder<CollectionDefinitionDto>()
            .index(COLLECTION_INDEX_NAME)
            .id(id)
            .document(collectionDefinitionDto)
            .refresh(Refresh.True);

    final IndexResponse indexResponse = osClient.index(request);

    if (!indexResponse.result().equals(Result.Created)) {
      final String message = "Could not write collection to Opensearch. ";
      LOG.error(message);
      throw new OptimizeRuntimeException(message);
    }
    LOG.debug("Collection with id [{}] has successfully been created.", id);
  }

  private UpdateResponse executeUpdateRequest(
      final String collectionId, final Script updateEntityScript, final String errorMessage) {
    final UpdateRequest.Builder request =
        new UpdateRequest.Builder<>()
            .index(COLLECTION_INDEX_NAME)
            .id(collectionId)
            .script(updateEntityScript)
            .refresh(Refresh.True)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    final UpdateResponse updateResponse = osClient.update(request, errorMessage);

    if (updateResponse.shards().failed().intValue() > 0) {
      final String message = String.format(errorMessage, collectionId);
      LOG.error(message, collectionId);
      throw new OptimizeRuntimeException(message);
    }
    return updateResponse;
  }

  private void removeRoleFromCollectionUnlessIsLastManager(
      final String collectionId, final String roleEntryId, final Map<String, JsonData> params)
      throws OptimizeConflictException {
    LOG.debug(
        "Deleting the role [{}] in collection with id [{}] in OpenSearch.",
        roleEntryId,
        collectionId);
    final Script addEntityScript =
        OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
            REMOVE_ROLE_FROM_COLLECTION_UNLESS_IS_LAST_MANAGER, params);

    final UpdateResponse updateResponse;
    try {
      updateResponse =
          executeUpdateRequest(
              collectionId,
              addEntityScript,
              "Was not able to delete role from collection with id [%s].");
    } catch (final OpenSearchException e) {
      final String errorMessage =
          String.format(
              "Was not able to update role with id [%s] on collection with id [%s]. Collection or role does not exist!",
              roleEntryId, collectionId);
      LOG.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }

    if (updateResponse.result() == Result.NoOp) {
      final String message =
          String.format(
              "Cannot delete last [%s] of collection [%s].", RoleType.MANAGER, collectionId);
      LOG.warn(message);
      throw new OptimizeCollectionConflictException(message);
    }
  }

  private Map<String, JsonData> constructParamsForRoleUpdateScript(
      final String roleEntryId, final String userId) {
    final Map<String, JsonData> params = new HashMap<>();
    params.put("roleEntryId", JsonData.of(roleEntryId));
    params.put("managerRole", JsonData.of(RoleType.MANAGER.toString()));
    if (userId != null) {
      params.put("lastModifier", JsonData.of(userId));
      params.put("lastModified", JsonData.of(formatter.format(LocalDateUtil.getCurrentDateTime())));
    }
    return params;
  }
}

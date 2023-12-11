/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import jakarta.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import org.camunda.optimize.service.db.writer.CollectionWriter;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class CollectionWriterOS implements CollectionWriter {

  public static final String DEFAULT_COLLECTION_NAME = "New Collection";

  private final OptimizeOpenSearchClient osClient;

  private final DateTimeFormatter formatter;

  @Override
  public IdResponseDto createNewCollectionAndReturnId(@NonNull String userId,
                                                      @NonNull PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto) {
//        return createNewCollectionAndReturnId(userId, partialCollectionDefinitionDto, IdGenerator.getNextId(), false);
    //todo will be handled in the OPT-7376
    return null;
  }

  @Override
  public IdResponseDto createNewCollectionAndReturnId(@NonNull String userId,
                                                      @NonNull PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto,
                                                      @NonNull String id,
                                                      boolean automaticallyCreated) {
//        log.debug("Writing new collection to OpenSearch");
//        CollectionDefinitionDto collectionDefinitionDto = new CollectionDefinitionDto();
//        collectionDefinitionDto.setId(id);
//        collectionDefinitionDto.setCreated(LocalDateUtil.getCurrentDateTime());
//        collectionDefinitionDto.setLastModified(LocalDateUtil.getCurrentDateTime());
//        collectionDefinitionDto.setOwner(userId);
//        collectionDefinitionDto.setLastModifier(userId);
//        collectionDefinitionDto.setAutomaticallyCreated(automaticallyCreated);
//        collectionDefinitionDto.setName(Optional.ofNullable(partialCollectionDefinitionDto.getName())
//                .orElse(DEFAULT_COLLECTION_NAME));
//
//        final CollectionDataDto newCollectionDataDto = new CollectionDataDto();
//        newCollectionDataDto.getRoles()
//                .add(new CollectionRoleRequestDto(new IdentityDto(userId, IdentityType.USER), RoleType.MANAGER));
//        if (partialCollectionDefinitionDto.getData() != null) {
//            newCollectionDataDto.setConfiguration(partialCollectionDefinitionDto.getData().getConfiguration());
//        }
//        collectionDefinitionDto.setData(newCollectionDataDto);
//
//        persistCollection(id, collectionDefinitionDto);
//        return new IdResponseDto(id);
    //todo will be handled in the OPT-7376
    return null;
  }

  @Override
  public void createNewCollection(@NonNull CollectionDefinitionDto collectionDefinitionDto) {
//        persistCollection(collectionDefinitionDto.getId(), collectionDefinitionDto);
    //todo will be handled in the OPT-7376
  }

  @Override
  public void updateCollection(CollectionDefinitionUpdateDto collection, String id) {
//        log.debug("Updating collection with id [{}] in Elasticsearch", id);
//
//        UpdateRequest.Builder<Void, CollectionDefinitionUpdateDto> request =
//                new UpdateRequest.Builder<Void, CollectionDefinitionUpdateDto>()
//                        .index(COLLECTION_INDEX_NAME)
//                        .id(id)
//                        .doc(collection)
//                        .refresh(Refresh.True)
//                        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
//
//        UpdateResponse<Void> updateResponse = osClient.update(request, e -> {
//            final String errorMessage = "There were errors while updating collection with ID {} to OS";
//            log.error(errorMessage, id, e);
//            return "There were errors while writing settings to OS." + e.getMessage();
//        });
//
//        if (updateResponse.shards().failed().intValue() > 0) {
//            log.error(
//                    "Was not able to update collection with id [{}] and name [{}].",
//                    id,
//                    collection.getName()
//            );
//            throw new OptimizeRuntimeException("Was not able to update collection!");
//        }
    //todo will be handled in the OPT-7376
  }

  @Override
  public void deleteCollection(String collectionId) {
//        log.debug("Deleting collection with id [{}]", collectionId);
//        DeleteRequest.Builder request = new DeleteRequest.Builder()
//                .index(COLLECTION_INDEX_NAME)
//                .id(collectionId)
//                .refresh(Refresh.True);
//
//
//        DeleteResponse deleteResponse = osClient.delete(request, e -> {
//            String errorMessage =
//                    String.format("Could not delete collection with id [%s]. ", collectionId);
//            log.error(errorMessage, e);
//            throw new OptimizeRuntimeException(errorMessage, e);
//        });
//
//
//        if (!deleteResponse.result().equals(Result.Deleted)) {
//            String message = String.format("Could not delete collection with id [%s]. Collection does not exist." +
//                    "Maybe it was already deleted by someone else?", collectionId);
//            log.error(message);
//            throw new NotFoundException(message);
//        }
    //todo will be handled in the OPT-7376
  }

  @Override
  public void addScopeEntriesToCollection(final String userId,
                                          final String collectionId,
                                          final List<CollectionScopeEntryDto> scopeUpdates) {
//        // try {
//        final Map<String, JsonData> params = new HashMap<>();
//        params.put("scopeEntriesToUpdate", JsonData.of(scopeUpdates));
//        params.put("lastModifier", JsonData.of(userId));
//        params.put("lastModified", JsonData.of(formatter.format(LocalDateUtil.getCurrentDateTime())));
//
//        final Script updateEntityScript = createDefaultScriptWithSpecificDtoParams(
//                "Map newScopes = ctx._source.data.scope.stream()" +
//                        "  .collect(Collectors.toMap(s -> s.id, Function.identity()));\n" +
//                        "params.scopeEntriesToUpdate" +
//                        "  .forEach(newScope -> {" +
//                        "     newScopes.computeIfPresent(newScope.id, (key, oldScope) -> {" +
//                        "       newScope.tenants = Stream.concat(oldScope.tenants.stream(), newScope.tenants.stream())" +
//                        "        .distinct()" +
//                        "        .collect(Collectors.toList());\n" +
//                        "       return newScope;\n" +
//                        "     });" +
//                        "     newScopes.putIfAbsent(newScope.id, newScope);\n" +
//                        "  });\n" +
//                        "ctx._source.data.scope = newScopes.values();" +
//                        "ctx._source.lastModifier = params.lastModifier;" +
//                        "ctx._source.lastModified = params.lastModified;",
//                params
//        );
//
//        final UpdateResponse<Void> updateResponse
//                = executeUpdateRequest(
//                collectionId,
//                updateEntityScript,
//                "Was not able to update collection with id [%s]."
//        );
//
//        if (updateResponse.result().equals(Result.NotFound)) {
//            final String message = String.format(
//                    "Was not able to add scope entries to collection with id [%s]. Collection does not exist!",
//                    collectionId
//            );
//            log.error(message);
//            throw new NotFoundException(message);
//        }
////        } catch (IOException e) {
////            String errorMessage = String.format("Wasn't able to add scope entries to collection with id [%s].", collectionId);
////            log.error(errorMessage, e);
////            throw new OptimizeRuntimeException(errorMessage, e);
////        }
    //todo will be handled in the OPT-7376
  }


  @Override
  public void deleteScopeEntryFromAllCollections(final String scopeEntryId) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void removeScopeEntry(String collectionId, String scopeEntryId, String userId) throws NotFoundException {
//        final Map<String, JsonData> params = new HashMap<>();
//        params.put("id", JsonData.of(scopeEntryId));
//        params.put("lastModifier", JsonData.of(userId));
//        params.put("lastModified", JsonData.of(formatter.format(LocalDateUtil.getCurrentDateTime())));
//
//        final Script updateEntityScript = OpenSearchWriterUtil.createDefaultScriptWithSpecificDtoParams(
//                "boolean removed = ctx._source.data.scope.removeIf(scope -> scope.id.equals(params.id));" +
//                        "if (removed) { " +
//                        "  ctx._source.lastModifier = params.lastModifier;" +
//                        "  ctx._source.lastModified = params.lastModified;" +
//                        "} else {" +
//                        "  ctx.op = \"none\";" +
//                        "}",
//                params
//        );
//
//        UpdateResponse<Void> updateResponse = executeUpdateRequest(
//                collectionId,
//                updateEntityScript,
//                "Was not able to update collection with id [%s]."
//        );
//
//        if (updateResponse.result().equals(Result.NoOp)) {
//            final String message = String.format("Scope entry for id [%s] doesn't exist.", scopeEntryId);
//            log.warn(message);
//            throw new NotFoundException(message);
//        }
    throw new NotImplementedException();
  }

  @Override
  public void removeScopeEntries(String collectionId, List<String> scopeEntryIds, String userId) throws NotFoundException {
    final Map<String, JsonData> params = new HashMap<>();
    params.put("ids", JsonData.of(scopeEntryIds));
    params.put("lastModifier", JsonData.of(userId));
    params.put("lastModified", JsonData.of(formatter.format(LocalDateUtil.getCurrentDateTime())));

    final Script updateEntityScript = createDefaultScriptWithPrimitiveParams(
      // @formatter:off
                "for (id in params.ids) {" +
                        "  ctx._source.data.scope.removeIf(scope -> scope.id.equals(id));" +
                        "}" +
                        "ctx._source.lastModifier = params.lastModifier;" +
                        "ctx._source.lastModified = params.lastModified;",
                // @formatter:on
      params
    );
    executeUpdateRequest(
      collectionId,
      updateEntityScript,
      "Was not able to update collection with id [%s]."
    );
    //todo will be handled in the OPT-7376
  }

  @Override
  public void updateScopeEntity(String collectionId,
                                CollectionScopeEntryUpdateDto scopeEntry,
                                String userId,
                                String scopeEntryId) {
    //todo will be handled in the OPT-7376
  }

  private UpdateResponse<Void> executeUpdateRequest(String collectionId, Script updateEntityScript, String errorMessage) {
//        final UpdateRequest.Builder<Void, Void> request = new UpdateRequest.Builder<Void, Void>()
//                .index(COLLECTION_INDEX_NAME)
//                .id(collectionId)
//                .script(updateEntityScript)
//                .refresh(Refresh.True)
//                .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
//
//        final UpdateResponse<Void> updateResponse = osClient.update(request, e -> {
//            String reason =
//                    String.format("Could not execute update of collection with id [%s]. ", collectionId);
//            log.error(reason, e);
//            throw new OptimizeRuntimeException(reason, e);
//        });
//
//
//        //todo test this appach of handlig errors cause osClient has callback handling of error
//        if (updateResponse.shards().failed().intValue() > 0) {
//            final String message = String.format(errorMessage, collectionId);
//            log.error(message, collectionId);
//            throw new OptimizeRuntimeException(message);
//        }
//        return updateResponse;
    //todo will be handled in the OPT-7376
    return null;
  }

  @Override
  public void addRoleToCollection(String collectionId, List<CollectionRoleRequestDto> rolesToAdd, String userId) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void updateRoleInCollection(final String collectionId,
                                     final String roleEntryId,
                                     final CollectionRoleUpdateRequestDto roleUpdateDto,
                                     final String userId) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void removeRoleFromCollectionUnlessIsLastManager(final String collectionId, final String roleEntryId,
                                                          final String userId) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void removeRoleFromCollection(final String collectionId, final String roleEntryId) {
    //todo will be handled in the OPT-7376
  }

}

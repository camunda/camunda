/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import jakarta.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import org.camunda.optimize.service.db.writer.CollectionWriter;
import org.camunda.optimize.service.os.OptimizeOpenSearchClient;
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

import static org.camunda.optimize.service.os.writer.OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams;

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
    throw new NotImplementedException();
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
    throw new NotImplementedException();
  }

  private void persistCollection(String id, CollectionDefinitionDto collectionDefinitionDto) {

//        IndexRequest.Builder<CollectionDefinitionDto> request = new IndexRequest.Builder<CollectionDefinitionDto>()
//                .index(COLLECTION_INDEX_NAME)
//                .id(id)
//                .document(collectionDefinitionDto)
//                .refresh(Refresh.True);
//
//        IndexResponse indexResponse = osClient.index(request);
//
//        if (!indexResponse.result().equals(Result.Created)) {
//            String message = "Could not write collection to OpenSearch. ";
//            log.error(message);
//            throw new OptimizeRuntimeException(message);
//        }
//
//        log.debug("Collection with id [{}] has successfully been created.", id);
    throw new NotImplementedException();
  }

  @Override
  public void createNewCollection(@NonNull CollectionDefinitionDto collectionDefinitionDto) {
//        persistCollection(collectionDefinitionDto.getId(), collectionDefinitionDto);
    throw new NotImplementedException();
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
    throw new NotImplementedException();
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
    throw new NotImplementedException();
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
    throw new NotImplementedException();
  }


  @Override
  public void deleteScopeEntryFromAllCollections(final String scopeEntryId) {
    //todo implement it with nested queries
//        final String updateItem = String.format("collection scope entry with ID [%s].", scopeEntryId);
//        log.info("Removing {} from all collections.", updateItem);
//
//
//        final Script removeScopeEntryFromCollectionsScript = createDefaultScriptWithSpecificDtoParams(
//                "def scopes = ctx._source.data.scope;" +
//                        "if(scopes != null) {" +
//                        "  scopes.removeIf(scope -> scope.id.equals(params.scopeEntryIdToRemove));" +
//                        "}",
//                Collections.singletonMap("scopeEntryIdToRemove", JsonData.of(scopeEntryId))
//        );
//
//        NestedQuery.Builder query = new NestedQuery.Builder()
//                .query()
//                .
//                DATA,
//                nestedQuery(
//                        String.join(".", DATA, SCOPE),
//                        termQuery(String.join(".", DATA, SCOPE, CollectionScopeEntryDto.Fields.id.name()), scopeEntryId),
//                        ScoreMode.None
//                ),
//                ScoreMode.None
//        );
//
//        ElasticsearchWriterUtil.tryUpdateByQueryRequest(
//                esClient,
//                updateItem,
//                removeScopeEntryFromCollectionsScript,
//                query,
//                COLLECTION_INDEX_NAME
//        );

    throw new NotImplementedException();
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
    //try {
    executeUpdateRequest(
      collectionId,
      updateEntityScript,
      "Was not able to update collection with id [%s]."
    );
//        } catch (IOException e) {
//            String errorMessage = String.format(
//                    "The scope with ids %s could not be removed from the collection %s.",
//                    scopeEntryIds,
//                    collectionId
//            );
//            log.error(errorMessage, e);
//            throw new OptimizeRuntimeException(errorMessage, e);
//        }
    throw new NotImplementedException();
  }

  @Override
  public void updateScopeEntity(String collectionId,
                                CollectionScopeEntryUpdateDto scopeEntry,
                                String userId,
                                String scopeEntryId) {

//        final Map<String, JsonData> params = new HashMap<>();
//        params.put("entryDto", JsonData.of(scopeEntry));
//        params.put("entryId", JsonData.of(scopeEntryId));
//        params.put("lastModifier", JsonData.of(userId));
//        params.put("lastModified", JsonData.of(formatter.format(LocalDateUtil.getCurrentDateTime())));
//
//        final Script updateEntityScript = createDefaultScriptWithSpecificDtoParams(
//                "def optionalEntry = ctx._source.data.scope.stream()" +
//                        "  .filter(s -> s.id.equals(params.entryId))" +
//                        "  .findFirst();" +
//                        "if (optionalEntry.isPresent()) {" +
//                        "  def entry = optionalEntry.get();" +
//                        "  entry.tenants = params.entryDto.tenants;" +
//                        "  ctx._source.lastModifier = params.lastModifier;" +
//                        "  ctx._source.lastModified = params.lastModified;" +
//                        "} else { " +
//                        "  throw new Exception('Cannot find scope entry.');" +
//                        "}",
//                params
//        );
//
//        executeUpdateRequest(
//                collectionId, updateEntityScript, "Was not able to update collection with id [%s]."
//        );

    throw new NotImplementedException();

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
    throw new NotImplementedException();
  }

  @Override
  public void addRoleToCollection(String collectionId, List<CollectionRoleRequestDto> rolesToAdd, String userId) {
//        log.debug("Adding roles {} to collection with id [{}] in OpenSearch.", rolesToAdd, collectionId);
//
//        final Map<String, JsonData> params = new HashMap<>();
//        params.put("rolesToAdd", JsonData.of(rolesToAdd));
//        params.put("lastModifier", JsonData.of(userId));
//        params.put("lastModified", JsonData.of(formatter.format(LocalDateUtil.getCurrentDateTime())));
//
//        final Script addEntityScript = createDefaultScriptWithSpecificDtoParams(
//                // @formatter:off
//                "def newRoles = new ArrayList();" +
//                        "for (roleToAdd in params.rolesToAdd) {" +
//                        "boolean exists = ctx._source.data.roles.stream()" +
//                        ".anyMatch(existingRole -> existingRole.id.equals(roleToAdd.id));" +
//                        "if (!exists){ " +
//                        "newRoles.add(roleToAdd); " +
//                        "}" +
//                        "}" +
//                        "if (newRoles.size() == params.rolesToAdd.size()) {" +
//                        "ctx._source.data.roles.addAll(newRoles); " +
//                        "ctx._source.lastModifier = params.lastModifier; " +
//                        "ctx._source.lastModified = params.lastModified; " +
//                        "} else {" +
//                        // ES is inconsistent on the op value, for update queries it's 'none'
//                        // see https://github.com/elastic/elasticsearch/issues/30356
//                        "ctx.op = \"none\";" +
//                        "}",
//                // @formatter:on
//                params
//        );
//
//        final UpdateResponse<Void> updateResponse = executeUpdateRequest(
//                collectionId,
//                addEntityScript,
//                "Was not able to update collection with id [%s]."
//        );
//
//        if (updateResponse.result().equals(Result.NoOp)) {
//            final String message = String.format(
//                    "One of the roles %s already exists in collection [%s].",
//                    rolesToAdd, collectionId
//            );
//            log.warn(message);
//            throw new OptimizeCollectionConflictException(message);
//        }
    throw new NotImplementedException();
  }

  @Override
  public void updateRoleInCollection(final String collectionId,
                                     final String roleEntryId,
                                     final CollectionRoleUpdateRequestDto roleUpdateDto,
                                     final String userId) {
//        log.debug("Updating the role [{}] in collection with id [{}] in OpenSearch.", roleEntryId, collectionId);
//
//        //  try {
//        final Map<String, JsonData> params = constructParamsForRoleUpdateScript(roleEntryId, userId);
//        params.put("role", JsonData.of(roleUpdateDto.getRole().toString()));
//
//        final Script addEntityScript = createDefaultScriptWithPrimitiveParams(
//                // @formatter:off
//                "def optionalExistingEntry = ctx._source.data.roles.stream()" +
//                        ".filter(dto -> dto.id.equals(params.roleEntryId))" +
//                        ".findFirst();" +
//                        "if(optionalExistingEntry.isPresent()){ " +
//                        "def existingEntry = optionalExistingEntry.get();" +
//                        "def moreThanOneManagerPresent = ctx._source.data.roles.stream()" +
//                        ".filter(dto -> params.managerRole.equals(dto.role))" +
//                        ".limit(2)" +
//                        ".count()" +
//                        "== 2;" +
//                        "if (!moreThanOneManagerPresent && params.managerRole.equals(existingEntry.role)) {" +
//                        // updating of last manager is not allowed
//                        "ctx.op = \"none\";" +
//                        "} else {" +
//                        "existingEntry.role = params.role;" +
//                        "ctx._source.lastModifier = params.lastModifier; " +
//                        "ctx._source.lastModified = params.lastModified; " +
//                        "}" +
//                        "} else {" +
//                        "throw new Exception('Cannot find role.');" +
//                        "}",
//                // @formatter:on
//                params
//        );
//
//        final UpdateResponse<Void> updateResponse = executeUpdateRequest(
//                collectionId,
//                addEntityScript,
//                "Was not able to update collection with id [%s]."
//        );
//
//        if (updateResponse.result().equals(Result.NoOp)) {
//            final String message = String.format(
//                    "Cannot assign lower privileged role to last [%s] of collection [%s].",
//                    RoleType.MANAGER,
//                    collectionId
//            );
//            log.warn(message);
//            throw new OptimizeCollectionConflictException(message);
//        }
//        } catch (IOException e) {
//            String errorMessage = String.format("Was not able to update collection with id [%s].", collectionId);
//            log.error(errorMessage, e);
//            throw new OptimizeRuntimeException(errorMessage, e);
//        } catch (ElasticsearchStatusException e) {
//            String errorMessage = String.format(
//                    "Was not able to update role with id [%s] on collection with id [%s]. Collection or role does not exist!",
//                    roleEntryId,
//                    collectionId
//            );
//            log.error(errorMessage, e);
//            throw new NotFoundException(errorMessage, e);
//        }

    throw new NotImplementedException();
  }

  @Override
  public void removeRoleFromCollectionUnlessIsLastManager(final String collectionId, final String roleEntryId,
                                                          final String userId) {
//        final Map<String, JsonData> params = constructParamsForRoleUpdateScript(roleEntryId, userId);
//        removeRoleFromCollectionUnlessIsLastManager(collectionId, roleEntryId, params);
    throw new NotImplementedException();
  }

  @Override
  public void removeRoleFromCollection(final String collectionId, final String roleEntryId) {
//        final Map<String, JsonData> params = constructParamsForRoleUpdateScript(roleEntryId, null);
//        removeRoleFromCollection(collectionId, roleEntryId, params);
    throw new NotImplementedException();
  }

  private void removeRoleFromCollection(final String collectionId, final String roleEntryId,
                                        final Map<String, JsonData> params) {
//        log.debug("Deleting the role [{}] in collection with id [{}] in OpenSearch.", roleEntryId, collectionId);
//
//        final Script addEntityScript = createDefaultScriptWithSpecificDtoParams(
//                // @formatter:off
//                "def optionalExistingEntry = ctx._source.data.roles.stream()" +
//                        ".filter(dto -> dto.id.equals(params.roleEntryId))" +
//                        ".findFirst();" +
//                        "if(optionalExistingEntry.isPresent()){ " +
//                        "def existingEntry = optionalExistingEntry.get();" +
//                        "ctx._source.data.roles.removeIf(entry -> entry.id.equals(params.roleEntryId));" +
//                        "if (params.containsKey(\"lastModifier\")) {" +
//                        "ctx._source.lastModifier = params.lastModifier;" +
//                        "}" +
//                        "if (params.containsKey(\"lastModified\")) {" +
//                        "ctx._source.lastModified = params.lastModified;" +
//                        "}" +
//                        "} else {" +
//                        "throw new Exception('Cannot find role.');" +
//                        "}",
//                // @formatter:on
//                params
//        );
//
//        executeUpdateRequest(
//                collectionId,
//                addEntityScript,
//                "Was not able to delete role from collection with id [%s]."
//        );
    throw new NotImplementedException();
  }

  private void removeRoleFromCollectionUnlessIsLastManager(final String collectionId, final String roleEntryId,
                                                           final Map<String, JsonData> params) {
//        log.debug("Deleting the role [{}] in collection with id [{}] in OpenSearch.", roleEntryId, collectionId);
//        //try {
//        final Script addEntityScript = createDefaultScriptWithPrimitiveParams(
//                // @formatter:off
//                "def optionalExistingEntry = ctx._source.data.roles.stream()" +
//                        ".filter(dto -> dto.id.equals(params.roleEntryId))" +
//                        ".findFirst();" +
//                        "if(optionalExistingEntry.isPresent()){ " +
//                        "def existingEntry = optionalExistingEntry.get();" +
//                        "def moreThanOneManagerPresent = ctx._source.data.roles.stream()" +
//                        ".filter(dto -> params.managerRole.equals(dto.role))" +
//                        ".limit(2)" +
//                        ".count()" +
//                        "== 2;" +
//                        "if (!moreThanOneManagerPresent && params.managerRole.equals(existingEntry.role)) {" +
//                        // deletion of last manager is not allowed
//                        "ctx.op = \"none\";" +
//                        "} else {" +
//                        "ctx._source.data.roles.removeIf(entry -> entry.id.equals(params.roleEntryId));" +
//                        "if (params.containsKey(\"lastModifier\")) {" +
//                        "ctx._source.lastModifier = params.lastModifier;" +
//                        "}" +
//                        "if (params.containsKey(\"lastModified\")) {" +
//                        "ctx._source.lastModified = params.lastModified;" +
//                        "}" +
//                        "}" +
//                        "} else {" +
//                        "throw new Exception('Cannot find role.');" +
//                        "}",
//                // @formatter:on
//                params
//        );
//
//        final UpdateResponse<Void> updateResponse = executeUpdateRequest(
//                collectionId,
//                addEntityScript,
//                "Was not able to delete role from collection with id [%s]."
//        );
//
//        if (updateResponse.result() == Result.NoOp) {
//            final String message = String.format(
//                    "Cannot delete last [%s] of collection [%s].", RoleType.MANAGER, collectionId
//            );
//            log.warn(message);
//            throw new OptimizeCollectionConflictException(message);
//        }
    throw new NotImplementedException();
  }

  private Map<String, JsonData> constructParamsForRoleUpdateScript(String roleEntryId, String userId) {
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

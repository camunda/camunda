/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryRestDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Collections.singletonList;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
@Builder
public class CollectionClient {

  public static final String DEFAULT_DEFINITION_KEY = "defaultScopeDefinitionKey";
  public static final String DEFAULT_TENANT = null;
  public static final List<String> DEFAULT_TENANTS = singletonList(DEFAULT_TENANT);
  public static final String PRIVATE_COLLECTION_ID = null;

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public String createNewCollectionForAllDefinitionTypes() {
    final String collectionId = createNewCollectionWithDefaultScope(PROCESS);
    createScopeWithTenants(collectionId, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS, DECISION);
    return collectionId;
  }

  public String createNewCollectionWithDefaultProcessScope() {
    return createNewCollectionWithDefaultScope(PROCESS);
  }

  public String createNewCollectionWithDefaultDecisionScope() {
    return createNewCollectionWithDefaultScope(DECISION);
  }

  public String createNewCollectionWithDefaultScope(DefinitionType definitionType) {
    final String collectionId = createNewCollection();
    createScopeWithTenants(collectionId, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS, definitionType);
    return collectionId;
  }

  public String createNewCollectionWithProcessScope(final ProcessInstanceEngineDto instanceEngineDto) {
    final String collectionId = createNewCollection();
    createScopeWithTenants(
      collectionId,
      instanceEngineDto.getProcessDefinitionKey(),
      singletonList(instanceEngineDto.getTenantId()),
      PROCESS
    );
    return collectionId;
  }

  public String createNewCollectionWithProcessScope(final ProcessDefinitionEngineDto definitionEngineDto) {
    final String collectionId = createNewCollection();
    createScopeWithTenants(
      collectionId,
      definitionEngineDto.getKey(),
      singletonList(definitionEngineDto.getTenantId().orElse(null)),
      PROCESS
    );
    return collectionId;
  }

  public String createNewCollection() {
    return getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public String createNewCollection(final String user, final String password) {
    return getRequestExecutor()
      .withUserAuthentication(user, password)
      .buildCreateCollectionRequest()
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public void updateCollection(String collectionId, PartialCollectionDefinitionDto updatedCollection) {
    getRequestExecutor()
      .buildUpdatePartialCollectionRequest(collectionId, updatedCollection)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public CollectionDefinitionRestDto getCollectionById(final String collectionId) {
    return getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(CollectionDefinitionRestDto.class, Response.Status.OK.getStatusCode());
  }

  public List<AuthorizedReportDefinitionDto> getReportsForCollection(final String collectionId) {
    return getRequestExecutor()
      .buildGetReportsForCollectionRequest(collectionId)
      .executeAndReturnList(
        AuthorizedReportDefinitionDto.class,
        200
      );
  }

  public List<AlertDefinitionDto> getAlertsForCollection(final String collectionId) {
    return getRequestExecutor()
      .buildGetAlertsForCollectionRequest(collectionId)
      .executeAndReturnList(AlertDefinitionDto.class,200);
  }

  public List<EntityDto> getEntitiesForCollection(final String collectionId){
    return getRequestExecutor()
      .buildGetCollectionEntitiesRequest(collectionId)
      .executeAndReturnList(EntityDto.class, Response.Status.OK.getStatusCode());
  }

  public void updateCollectionScopeAsKermit(final String collectionId,
                                            final CollectionScopeEntryRestDto scopeEntry,
                                            final List<String> tenants) {
    getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(
        collectionId,
        scopeEntry.getId(),
        new CollectionScopeEntryUpdateDto(tenants)
      )
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public void createScopeForCollection(final String collectionId,
                                       final String definitionKey,
                                       final DefinitionType definitionType) {
    addScopeEntryToCollection(collectionId, createSimpleScopeEntry(definitionKey, definitionType));
  }

  public List<CollectionScopeEntryRestDto> getCollectionScope(final String collectionId) {
    return getRequestExecutor()
      .buildGetScopeForCollectionRequest(collectionId)
      .execute(new TypeReference<List<CollectionScopeEntryRestDto>>() {
      });
  }

  public List<CollectionScopeEntryRestDto> getCollectionScopeForKermit(final String collectionId) {
    return getRequestExecutor()
      .buildGetScopeForCollectionRequest(collectionId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(new TypeReference<List<CollectionScopeEntryRestDto>>() {
      });
  }

  public void createScopeWithTenants(final String collectionId, String definitionKey,
                                     List<String> tenants, final DefinitionType definitionType) {
    final CollectionScopeEntryDto scopeEntry = new CollectionScopeEntryDto(definitionType, definitionKey, tenants);
    addScopeEntryToCollection(collectionId, scopeEntry);
  }

  public Response deleteCollection(String id) {
    return getRequestExecutor()
      .buildDeleteCollectionRequest(id, true)
      .execute();
  }

  public CollectionScopeEntryDto createSimpleScopeEntry(String definitionKey, DefinitionType definitionType) {
    return new CollectionScopeEntryDto(definitionType, definitionKey, DEFAULT_TENANTS);
  }

  public void addScopeEntryToCollection(final String collectionId, final CollectionScopeEntryDto entry) {
    addScopeEntriesToCollection(collectionId, singletonList(entry));
  }

  public void addScopeEntriesToCollection(final String collectionId, final List<CollectionScopeEntryDto> entries) {
    getRequestExecutor()
      .buildAddScopeEntriesToCollectionRequest(collectionId, entries)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public void addScopeEntryToCollectionWithUser(final String collectionId, final CollectionScopeEntryDto entry,
                                                final String user, final String password) {
    getRequestExecutor()
      .buildAddScopeEntriesToCollectionRequest(collectionId, singletonList(entry))
      .withUserAuthentication(user, password)
      .execute(IdDto.class, Response.Status.NO_CONTENT.getStatusCode());
  }

  public IdDto addRoleToCollection(final String collectionId, final CollectionRoleDto roleDto) {
    return getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());
  }

  public OptimizeRequestExecutor getAlertsRequest(final String userId, final String password,
                                                  final String collectionId) {
    return getRequestExecutor()
      .buildGetAlertsForCollectionRequest(collectionId)
      .withUserAuthentication(userId, password);
  }

  public List<CollectionRoleRestDto> getCollectionRoles(final String collectionId) {
    return getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_USERNAME)
      .buildGetRolesToCollectionRequest(collectionId)
      .executeAndReturnList(CollectionRoleRestDto.class, Response.Status.OK.getStatusCode());
  }

  public List<IdDto> getCollectionRoleIdDtos(final String collectionId) {
    return getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_USERNAME)
      .buildGetRolesToCollectionRequest(collectionId)
      .executeAndReturnList(IdDto.class, Response.Status.OK.getStatusCode());
  }

  public IdDto copyCollection(String collectionId) {
    return copyCollection(collectionId, null);
  }

  public IdDto copyCollection(String collectionId, String newName) {
    OptimizeRequestExecutor executor = getRequestExecutor()
      .buildCopyCollectionRequest(collectionId);

    if (newName != null) {
      executor.addSingleQueryParam("name", newName);
    }

    return executor
      .execute(IdDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}

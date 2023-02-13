/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import io.github.netmikey.logunit.api.LogCapturer;
import org.assertj.core.api.Condition;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryResponseDto;
import org.camunda.optimize.service.collection.CollectionScopeService;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.event.Level;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.mockserver.model.HttpRequest.request;

public class CollectionRestServiceScopeIT extends AbstractIT {

  public static final String DEFAULT_DEFINITION_KEY = "_KEY_";

  @RegisterExtension
  @Order(5)
  protected final LogCapturer logCapturer =
    LogCapturer.create().forLevel(Level.DEBUG).captureForType(CollectionScopeService.class);

  @Test
  public void partialCollectionUpdateDoesNotAffectScopes() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(collectionId, createSimpleScopeEntry(DEFAULT_DEFINITION_KEY));
    final List<CollectionScopeEntryResponseDto> expectedCollectionScope =
      collectionClient.getCollectionScope(collectionId);

    // when
    final PartialCollectionDefinitionRequestDto collectionRenameDto = new PartialCollectionDefinitionRequestDto("Test");
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    final List<CollectionScopeEntryResponseDto> collectionScope = collectionClient.getCollectionScope(collectionId);
    assertThat(collectionScope).isEqualTo(expectedCollectionScope);
  }

  @Test
  public void getScopeForCollection() {
    // given
    final String definitionKey = DEFAULT_DEFINITION_KEY;
    addProcessDefinitionToElasticsearch(definitionKey, null);
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(definitionKey);
    collectionClient.addScopeEntryToCollection(collectionId, entry);

    importAllEngineEntitiesFromScratch();

    // when
    List<CollectionScopeEntryResponseDto> scopeEntries = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scopeEntries)
      .containsExactly(
        new CollectionScopeEntryResponseDto()
          .setId(PROCESS + ":" + definitionKey)
          .setDefinitionKey(definitionKey)
          .setDefinitionName(definitionKey)
          .setDefinitionType(PROCESS)
          .setTenants(Collections.singletonList(TENANT_NOT_DEFINED))
      );
  }

  @Test
  public void scopesAreOrderByDefinitionTypeAndThenDefinitionName() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto decisionScope1 = createSimpleScopeEntry("DECISION_KEY_LAST", DECISION);
    final CollectionScopeEntryDto decisionScope2 = createSimpleScopeEntry("xdecKey2", DECISION);
    final CollectionScopeEntryDto processScope1 = createSimpleScopeEntry("PROCESS_KEY_LAST", PROCESS);
    final CollectionScopeEntryDto processScope2 = createSimpleScopeEntry("xprocKey2", PROCESS);
    // should fallback to key as name when name is null
    addDecisionDefinitionToElasticsearch(decisionScope1.getDefinitionKey(), null);
    addProcessDefinitionToElasticsearch(processScope1.getDefinitionKey(), null);
    addDecisionDefinitionToElasticsearch(decisionScope2.getDefinitionKey(), "DECISION_KEY_FIRST");
    addProcessDefinitionToElasticsearch(processScope2.getDefinitionKey(), "PROCESS_KEY_FIRST");
    collectionClient.addScopeEntriesToCollection(
      collectionId, asList(decisionScope1, processScope1, decisionScope2, processScope2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    List<CollectionScopeEntryResponseDto> scopeEntries = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetScopeForCollectionRequest(collectionId)
      .execute(new TypeReference<List<CollectionScopeEntryResponseDto>>() {
      });

    // then
    assertThat(scopeEntries)
      .hasSize(4)
      .extracting(CollectionScopeEntryResponseDto::getDefinitionName)
      .containsExactly("PROCESS_KEY_FIRST", "PROCESS_KEY_LAST", "DECISION_KEY_FIRST", "DECISION_KEY_LAST");
  }

  @Test
  public void addDefinitionScopeEntry() {
    // given
    addProcessDefinitionToElasticsearch(DEFAULT_DEFINITION_KEY, null);
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);

    importAllEngineEntitiesFromScratch();

    // when
    collectionClient.addScopeEntryToCollection(collectionId, entry);
    List<CollectionScopeEntryResponseDto> scope = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scope)
      .hasSize(1)
      .extracting(CollectionScopeEntryResponseDto::getId)
      .containsExactly("process:_KEY_");
  }

  @Test
  public void addScopeEntries() {
    // given
    addProcessDefinitionToElasticsearch(DEFAULT_DEFINITION_KEY, null);
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);

    importAllEngineEntitiesFromScratch();

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    List<CollectionScopeEntryResponseDto> scope = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scope)
      .hasSize(1)
      .extracting(CollectionScopeEntryResponseDto::getId)
      .containsExactly("process:_KEY_");
  }

  @Test
  public void addScopeEntries_addsToExistingScopes() {
    // given
    addProcessDefinitionToElasticsearch(DEFAULT_DEFINITION_KEY, null);
    final String anotherDefinitionKey = "_ANOTHER_KEY_";
    addProcessDefinitionToElasticsearch(anotherDefinitionKey, null);
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);
    final CollectionScopeEntryDto anotherEntry = createSimpleScopeEntry(anotherDefinitionKey);

    importAllEngineEntitiesFromScratch();

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(anotherEntry));
    List<CollectionScopeEntryResponseDto> scope = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scope)
      .hasSize(2)
      .extracting(CollectionScopeEntryResponseDto::getId)
      .containsExactlyInAnyOrder("process:_KEY_", "process:_ANOTHER_KEY_");
  }

  @Test
  public void addScopeEntries_addsTenantsToExistingScopes() {
    // given
    addProcessDefinitionToElasticsearch(DEFAULT_DEFINITION_KEY, null);
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    addTenantToElasticsearch("newTenant");
    entry.getTenants().add("newTenant");

    importAllEngineEntitiesFromScratch();

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    List<CollectionScopeEntryResponseDto> scope = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scope)
      .hasSize(1)
      .have(new Condition<>(c -> c.getId().equals("process:_KEY_"), "Scope id should match process:_KEY_"))
      .flatExtracting(CollectionScopeEntryResponseDto::getTenantIds)
      .containsExactlyInAnyOrder(null, "newTenant");
  }

  @Test
  public void addScopeEntries_addsTenantsAndScopeToExistingScopes() {
    // given
    addProcessDefinitionToElasticsearch(DEFAULT_DEFINITION_KEY, null);
    final String anotherDefinitionKey = "_ANOTHER_KEY_";
    addProcessDefinitionToElasticsearch(anotherDefinitionKey, null);
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);
    final CollectionScopeEntryDto anotherEntry = createSimpleScopeEntry(anotherDefinitionKey);
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(anotherEntry));
    addTenantToElasticsearch("newTenant");
    entry.getTenants().add("newTenant");

    importAllEngineEntitiesFromScratch();

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    List<CollectionScopeEntryResponseDto> scope = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scope)
      .hasSize(2)
      .extracting(CollectionScopeEntryResponseDto::getId, CollectionScopeEntryResponseDto::getTenantIds)
      .containsExactlyInAnyOrder(
        new Tuple("process:_KEY_", Lists.newArrayList(null, "newTenant")),
        new Tuple("process:_ANOTHER_KEY_", Lists.newArrayList((Object) null))
      );
  }

  @Test
  public void addScopeEntries_doesNotRemoveTenants() {
    // given
    addProcessDefinitionToElasticsearch(DEFAULT_DEFINITION_KEY, null);
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    addTenantToElasticsearch("newTenant");
    entry.setTenants(Collections.singletonList("newTenant"));

    importAllEngineEntitiesFromScratch();

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    List<CollectionScopeEntryResponseDto> scope = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scope)
      .hasSize(1)
      .extracting(CollectionScopeEntryResponseDto::getId, CollectionScopeEntryResponseDto::getTenantIds)
      .containsExactlyInAnyOrder(
        new Tuple("process:_KEY_", Lists.newArrayList(null, "newTenant"))
      );
  }

  @Test
  public void addScopeEntries_sameScopeIsNotAddedTwice() {
    // given
    addProcessDefinitionToElasticsearch(DEFAULT_DEFINITION_KEY, null);
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);

    importAllEngineEntitiesFromScratch();

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    List<CollectionScopeEntryResponseDto> scope = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scope)
      .hasSize(1)
      .extracting(CollectionScopeEntryResponseDto::getId)
      .containsExactly("process:_KEY_");
  }

  @Test
  public void addScopeEntries_unknownCollectionResultsInNotFound() {
    // given
    collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildAddScopeEntriesToCollectionRequest("unknownId", Collections.singletonList(entry))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void addMultipleDefinitionScopeEntries() {
    // given
    final String definitionKey1 = "_KEY1_";
    addProcessDefinitionToElasticsearch(definitionKey1, null);
    final String definitionKey2 = "_KEY2_";
    addProcessDefinitionToElasticsearch(definitionKey2, null);
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry1 = createSimpleScopeEntry(definitionKey1);
    final CollectionScopeEntryDto entry2 = createSimpleScopeEntry(definitionKey2);

    importAllEngineEntitiesFromScratch();

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Lists.newArrayList(entry1, entry2));
    List<CollectionScopeEntryResponseDto> scope = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scope)
      .hasSize(2)
      .extracting(CollectionScopeEntryResponseDto::getId)
      .containsExactlyInAnyOrder("process:_KEY1_", "process:_KEY2_");
  }

  @Test
  public void updateDefinitionScopeEntry_addTenant() {
    // given
    addProcessDefinitionToElasticsearch(DEFAULT_DEFINITION_KEY, null);
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);
    collectionClient.addScopeEntryToCollection(collectionId, entry);

    importAllEngineEntitiesFromScratch();

    // when
    final String tenant1 = "tenant1";
    addTenantToElasticsearch(tenant1);
    entry.setTenants(Lists.newArrayList(null, tenant1));
    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(collectionId, entry.getId(), new CollectionScopeEntryUpdateDto(entry))
      .execute(Response.Status.NO_CONTENT.getStatusCode());

    // then
    List<CollectionScopeEntryResponseDto> scope = collectionClient.getCollectionScope(collectionId);

    assertThat(scope)
      .hasSize(1)
      .singleElement()
      .satisfies(
        scopeEntryDto -> assertThat(scopeEntryDto.getTenants())
          .extracting(TenantDto::getId)
          .containsExactly(null, tenant1)
      );
  }

  @Test
  public void updateDefinitionScopeEntry_removeTenant() {
    // given
    addProcessDefinitionToElasticsearch(DEFAULT_DEFINITION_KEY, null);
    final String collectionId = collectionClient.createNewCollection();
    final String tenant = "tenant";
    addTenantToElasticsearch(tenant);
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);
    entry.getTenants().add(tenant);

    importAllEngineEntitiesFromScratch();

    collectionClient.addScopeEntryToCollection(collectionId, entry);

    // when
    entry.setTenants(Collections.singletonList(null));
    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(collectionId, entry.getId(), new CollectionScopeEntryUpdateDto(entry))
      .execute(Response.Status.NO_CONTENT.getStatusCode());

    // then
    List<CollectionScopeEntryResponseDto> scope = collectionClient.getCollectionScope(collectionId);

    assertThat(scope)
      .hasSize(1)
      .singleElement()
      .satisfies(
        scopeEntryDto -> assertThat(scopeEntryDto.getTenants())
          .extracting(TenantDto::getId)
          .containsExactly((String) null)
      );
  }

  @Test
  public void updateUnknownScopeThrowsNotFound() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);
    collectionClient.addScopeEntryToCollection(collectionId, entry);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(collectionId, "fooScopeId", new CollectionScopeEntryUpdateDto(entry))
      .execute();

    // then not found is thrown
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void updateUnknownTenantsAreFilteredOut() {
    // given
    addProcessDefinitionToElasticsearch(DEFAULT_DEFINITION_KEY, null);
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);
    collectionClient.addScopeEntryToCollection(collectionId, entry);

    importAllEngineEntitiesFromScratch();

    // when
    final String tenant1 = "tenant1";
    addTenantToElasticsearch(tenant1);
    entry.setTenants(Lists.newArrayList(null, tenant1, "fooTenant"));
    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(collectionId, entry.getId(), new CollectionScopeEntryUpdateDto(entry))
      .execute(Response.Status.NO_CONTENT.getStatusCode());

    // then
    List<CollectionScopeEntryResponseDto> scope = collectionClient.getCollectionScope(collectionId);

    assertThat(scope)
      .hasSize(1)
      .singleElement()
      .satisfies(
        scopeEntryDto -> assertThat(scopeEntryDto.getTenants())
          .extracting(TenantDto::getId)
          .containsExactly(null, tenant1)
      );
  }

  @Test
  public void updatingNonExistingDefinitionScopeEntryFails() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final List<CollectionScopeEntryResponseDto> expectedScope = collectionClient.getCollectionScope(collectionId);
    final String notExistingScopeEntryId = "PROCESS:abc";

    // when
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(
        collectionId,
        notExistingScopeEntryId,
        new CollectionScopeEntryUpdateDto(entry)
      )
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

    assertThat(collectionClient.getCollectionScope(collectionId)).isEqualTo(expectedScope);
  }

  @Test
  public void removeScopeEntry() {
    // given
    addProcessDefinitionToElasticsearch(DEFAULT_DEFINITION_KEY, null);
    String collectionId = collectionClient.createNewCollection();
    CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);

    importAllEngineEntitiesFromScratch();

    collectionClient.addScopeEntryToCollection(collectionId, entry);

    List<CollectionScopeEntryResponseDto> scope = collectionClient.getCollectionScope(collectionId);

    assertThat(scope).hasSize(1);

    // when
    embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, entry.getId())
      .execute(Response.Status.NO_CONTENT.getStatusCode());

    scope = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scope).isEmpty();
  }

  @Test
  public void removeScopeDefinitionFailsDueReportConflict() {
    // given
    String collectionId = collectionClient.createNewCollection();
    CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);

    collectionClient.addScopeEntryToCollection(collectionId, entry);
    String reportId = reportClient.createAndStoreProcessReport(
      collectionId,
      DEFAULT_DEFINITION_KEY,
      Collections.singletonList(null)
    );

    // when
    ConflictResponseDto conflictResponseDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, entry.getId())
      .execute(ConflictResponseDto.class, Response.Status.CONFLICT.getStatusCode());

    // then
    assertThat(conflictResponseDto.getErrorCode()).isEqualTo(OptimizeCollectionConflictException.ERROR_CODE);
    assertThat(conflictResponseDto.getConflictedItems())
      .extracting(ConflictedItemDto::getId)
      .containsExactly(reportId);
  }

  @Test
  public void forceRemoveScopeDefinitionFailsIfEsFailsToRemoveReports() {
    // given
    String collectionId = collectionClient.createNewCollection();
    CollectionScopeEntryDto entry = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);
    collectionClient.addScopeEntryToCollection(collectionId, entry);
    reportClient.createAndStoreProcessReport(collectionId, DEFAULT_DEFINITION_KEY, Collections.singletonList(null));

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*" + SINGLE_PROCESS_REPORT_INDEX_NAME + ".*/_delete_by_query")
      .withMethod(POST);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, entry.getId(), true)
      .execute(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(collectionClient.getCollectionById(collectionId).getData().getScope())
      .extracting(CollectionScopeEntryDto::getId)
      .contains(entry.getId());
  }

  @Test
  public void removeNotExistingScopeDefinitionFails() {
    // given
    String collectionId = collectionClient.createNewCollection();

    // then
    embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, "PROCESS:_KEY_")
      .execute(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void bulkDeleteScopesFromCollectionUnauthenticatedUser() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildBulkDeleteScopeEntriesFromCollectionRequest(Arrays.asList("doesntMatter", "doesntMatter"), "doesntMatter")
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void bulkDeleteScopesFromCollectionUserNotAuthorizedToAccessCollection() {
    // given
    String collectionId = collectionClient.createNewCollection();
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildBulkDeleteScopeEntriesFromCollectionRequest(Collections.emptyList(), collectionId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void bulkDeleteScopesFromCollectionEmptyCollectionScopeList() {
    // given
    String collectionId = collectionClient.createNewCollection();

    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    Response response = buildAndExecuteBulkDeleteScopeEntriesFromCollectionRequest(
      Collections.emptyList(),
      collectionId
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void bulkDeleteScopesFromCollectionNullScopeList() {
    // given
    String collectionId = collectionClient.createNewCollection();

    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    Response response = buildAndExecuteBulkDeleteScopeEntriesFromCollectionRequest(null, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void bulkDeleteScopesFromCollectionCollectionNotFound() {
    // given
    final CollectionScopeEntryDto scopeEntry1 =
      new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    final CollectionScopeEntryDto scopeEntry2 =
      new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);

    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    Response response = buildAndExecuteBulkDeleteScopeEntriesFromCollectionRequest(Arrays.asList(
      scopeEntry1.getId(),
      scopeEntry2.getId()
    ), "doesNotExist");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void bulkDeleteScopesFromCollectionSkipsEntryIfScopeDoesNotExist() {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry1 =
      new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    final CollectionScopeEntryDto scopeEntry2 =
      new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry1);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry2);

    // when
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    Response response = buildAndExecuteBulkDeleteScopeEntriesFromCollectionRequest(Arrays.asList(
      scopeEntry1.getId(),
      "process:someKey",
      scopeEntry2.getId()
    ), collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(collectionClient.getCollectionById(collectionId).getData().getScope()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void bulkDeleteScopesFromCollection(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry1 =
      new CollectionScopeEntryDto(definitionType, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry1);
    reportClient.createSingleReport(
      collectionId,
      definitionType,
      DEFAULT_DEFINITION_KEY,
      DEFAULT_TENANTS
    );
    final CollectionScopeEntryDto scopeEntry2 = new CollectionScopeEntryDto(PROCESS, "someKey", DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry2);

    // when
    Response response = buildAndExecuteBulkDeleteScopeEntriesFromCollectionRequest(Arrays.asList(
      scopeEntry1.getId(),
      scopeEntry2.getId()
    ), collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(collectionClient.getCollectionById(collectionId).getData().getScope()).isEmpty();
    assertThat(reportClient.getAllReportsAsUser()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void bulkDeleteScopesFromCollectionScopesHaveTheSameId(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry1 =
      new CollectionScopeEntryDto(definitionType, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry1);
    reportClient.createSingleReport(
      collectionId,
      definitionType,
      DEFAULT_DEFINITION_KEY,
      DEFAULT_TENANTS
    );
    final CollectionScopeEntryDto scopeEntry2 = new CollectionScopeEntryDto(PROCESS, "someKey", DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry2);

    // when
    Response response = buildAndExecuteBulkDeleteScopeEntriesFromCollectionRequest(Arrays.asList(
      scopeEntry1.getId(),
      scopeEntry1.getId(),
      scopeEntry2.getId()
    ), collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(collectionClient.getCollectionById(collectionId).getData().getScope()).isEmpty();
    assertThat(reportClient.getAllReportsAsUser()).isEmpty();
  }

  @Test
  public void bulkDeleteOfScopesSkipsEntryIfEsFailsToRemoveAssociatedReports() {
    // given
    String collectionId = collectionClient.createNewCollection();
    CollectionScopeEntryDto scopeEntry1 = createSimpleScopeEntry(DEFAULT_DEFINITION_KEY);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry1);
    reportClient.createAndStoreProcessReport(collectionId, DEFAULT_DEFINITION_KEY, Collections.singletonList(null));
    final CollectionScopeEntryDto scopeEntry2 = new CollectionScopeEntryDto(PROCESS, "someKey", DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry2);

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*" + SINGLE_PROCESS_REPORT_INDEX_NAME + ".*/_delete_by_query")
      .withMethod(POST);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    buildAndExecuteBulkDeleteScopeEntriesFromCollectionRequest(
      Arrays.asList(
        scopeEntry1.getId(),
        scopeEntry2.getId()
      ),
      collectionId
    );

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(collectionClient.getCollectionById(collectionId).getData().getScope()).hasSize(1)
      .extracting(CollectionScopeEntryDto::getId)
      .containsExactly(scopeEntry1.getId());
    String message = String.format(
      "There was an error while deleting reports associated to collection scope with id %s. The scope cannot be " +
        "deleted.",
      scopeEntry1.getId()
    );
    logCapturer.assertContains(message);
  }

  private void addDecisionDefinitionToElasticsearch(final String key,
                                                    final String name) {
    final DecisionDefinitionOptimizeDto decisionDefinitionDto = DecisionDefinitionOptimizeDto.builder()
      .id(key)
      .key(key)
      .version("1")
      .dmn10Xml("someXml")
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .name(name)
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      DECISION_DEFINITION_INDEX_NAME, decisionDefinitionDto.getId(), decisionDefinitionDto
    );
  }

  private void addProcessDefinitionToElasticsearch(final String key,
                                                   final String name) {
    final ProcessDefinitionOptimizeDto expectedDto = ProcessDefinitionOptimizeDto.builder()
      .id(key)
      .key(key)
      .name(name)
      .version("1")
      .bpmn20Xml("someXml")
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME, expectedDto.getId(), expectedDto
    );
  }

  private CollectionScopeEntryDto createSimpleScopeEntry(String definitionKey) {
    return createSimpleScopeEntry(definitionKey, PROCESS);
  }

  private CollectionScopeEntryDto createSimpleScopeEntry(String definitionKey, DefinitionType definitionType) {
    List<String> tenants = new ArrayList<>();
    tenants.add(null);
    return new CollectionScopeEntryDto(definitionType, definitionKey, tenants);
  }

  private void addTenantToElasticsearch(final String tenantId) {
    TenantDto tenantDto = new TenantDto(tenantId, "ATenantName", DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, tenantId, tenantDto);
    embeddedOptimizeExtension.reloadTenantCache();
  }

  private Response buildAndExecuteBulkDeleteScopeEntriesFromCollectionRequest(List<String> collectionScopeIds,
                                                                              String collectionId) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildBulkDeleteScopeEntriesFromCollectionRequest(collectionScopeIds, collectionId)
      .execute();
  }
}

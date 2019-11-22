/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import org.apache.http.HttpStatus;
import org.assertj.core.api.Condition;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryRestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public class CollectionRestServiceScopeIT extends AbstractIT {
  
  @Test
  public void partialCollectionUpdateDoesNotAffectScopes() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(collectionId, createSimpleScopeEntry("_KEY_"));
    final SimpleCollectionDefinitionDto expectedCollection = collectionClient.getCollection(collectionId);

    // when
    final PartialCollectionDefinitionDto collectionRenameDto = new PartialCollectionDefinitionDto("Test");
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(204);
    final SimpleCollectionDefinitionDto collection = collectionClient.getCollection(collectionId);
    assertThat(collection.getData().getScope()).isEqualTo(expectedCollection.getData().getScope());
  }

  @Test
  public void getScopeForCollection() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final String definitionKey = "_KEY_";
    final CollectionScopeEntryDto entry = createSimpleScopeEntry(definitionKey);
    collectionClient.addScopeEntryToCollection(collectionId, entry);

    // when
    List<CollectionScopeEntryRestDto> scopeEntries = collectionClient.getCollectionScope(collectionId);

    // then
    assertThat(scopeEntries)
      .containsExactly(
        new CollectionScopeEntryRestDto()
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

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<CollectionScopeEntryRestDto> scopeEntries = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetScopeForCollectionRequest(collectionId)
      .execute(new TypeReference<List<CollectionScopeEntryRestDto>>() {});

    // then
    assertThat(scopeEntries)
      .hasSize(4)
      .extracting(CollectionScopeEntryRestDto::getDefinitionName)
      .containsExactly("PROCESS_KEY_FIRST", "PROCESS_KEY_LAST", "DECISION_KEY_FIRST", "DECISION_KEY_LAST");
  }

  @Test
  public void addDefinitionScopeEntry() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");

    // when
    collectionClient.addScopeEntryToCollection(collectionId, entry);
    AuthorizedResolvedCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(AuthorizedResolvedCollectionDefinitionDto.class, 200);

    // then
    assertThat(collectionDefinitionDto.getDefinitionDto().getData().getScope())
      .hasSize(1)
      .extracting(CollectionScopeEntryDto::getId)
      .containsExactly("process:_KEY_");
  }

  @Test
  public void addScopeEntries() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    SimpleCollectionDefinitionDto collectionDefinitionDto = collectionClient.getCollection(collectionId);

    // then
    assertThat(collectionDefinitionDto.getData().getScope().size()).isEqualTo(1);
    assertThat(collectionDefinitionDto.getData().getScope())
      .hasSize(1)
      .extracting(CollectionScopeEntryDto::getId)
      .containsExactly("process:_KEY_");
  }

  @Test
  public void addScopeEntries_addsToExistingScopes() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");
    final CollectionScopeEntryDto anotherEntry = createSimpleScopeEntry("_ANOTHER_KEY_");

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(anotherEntry));
    SimpleCollectionDefinitionDto collectionDefinitionDto = collectionClient.getCollection(collectionId);

    // then
    assertThat(collectionDefinitionDto.getData().getScope())
      .hasSize(2)
      .extracting(CollectionScopeEntryDto::getId)
      .containsExactlyInAnyOrder("process:_KEY_", "process:_ANOTHER_KEY_");
  }

  @Test
  public void addScopeEntries_addsTenantsToExistingScopes() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    addTenantToElasticsearch("newTenant");
    entry.getTenants().add("newTenant");

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    SimpleCollectionDefinitionDto collectionDefinitionDto = collectionClient.getCollection(collectionId);

    // then
    assertThat(collectionDefinitionDto.getData().getScope())
      .hasSize(1)
      .have(new Condition<>(c -> c.getId().equals("process:_KEY_"), "Scope id should match process:_KEY_"))
      .flatExtracting(CollectionScopeEntryDto::getTenants)
      .containsExactlyInAnyOrder(null, "newTenant");
  }

  @Test
  public void addScopeEntries_addsTenantsAndScopeToExistingScopes() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");
    final CollectionScopeEntryDto anotherEntry = createSimpleScopeEntry("_ANOTHER_KEY_");
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(anotherEntry));
    addTenantToElasticsearch("newTenant");
    entry.getTenants().add("newTenant");


    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    SimpleCollectionDefinitionDto collectionDefinitionDto = collectionClient.getCollection(collectionId);

    // then
    assertThat(collectionDefinitionDto.getData().getScope())
      .hasSize(2)
      .extracting(CollectionScopeEntryDto::getId, CollectionScopeEntryDto::getTenants)
      .containsExactlyInAnyOrder(
        new Tuple("process:_KEY_", Lists.newArrayList(null, "newTenant")),
        new Tuple("process:_ANOTHER_KEY_", Lists.newArrayList((Object) null))
      );
  }

  @Test
  public void addScopeEntries_doesNotRemoveTenants() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    addTenantToElasticsearch("newTenant");
    entry.setTenants(Collections.singletonList("newTenant"));

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    SimpleCollectionDefinitionDto collectionDefinitionDto = collectionClient.getCollection(collectionId);

    // then
    assertThat(collectionDefinitionDto.getData().getScope())
      .hasSize(1)
      .extracting(CollectionScopeEntryDto::getId, CollectionScopeEntryDto::getTenants)
      .containsExactlyInAnyOrder(
        new Tuple("process:_KEY_", Lists.newArrayList(null, "newTenant"))
      );
  }

  @Test
  public void addScopeEntries_sameScopeIsNotAddedTwice() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    collectionClient.addScopeEntriesToCollection(collectionId, Collections.singletonList(entry));
    SimpleCollectionDefinitionDto collectionDefinitionDto = collectionClient.getCollection(collectionId);

    // then
    assertThat(collectionDefinitionDto.getData().getScope().size()).isEqualTo(1);
    assertThat(collectionDefinitionDto.getData().getScope())
      .hasSize(1)
      .extracting(CollectionScopeEntryDto::getId)
      .containsExactly("process:_KEY_");
  }

  @Test
  public void addScopeEntries_unknownCollectionResultsInNotFound() {
    // given
    collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildAddScopeEntriesToCollectionRequest("unknownId", Collections.singletonList(entry))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void addMultipleDefinitionScopeEntries() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry1 = createSimpleScopeEntry("_KEY1_");
    final CollectionScopeEntryDto entry2 = createSimpleScopeEntry("_KEY2_");

    // when
    collectionClient.addScopeEntriesToCollection(collectionId, Lists.newArrayList(entry1, entry2));
    AuthorizedResolvedCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(AuthorizedResolvedCollectionDefinitionDto.class, 200);

    // then
    assertThat(collectionDefinitionDto.getDefinitionDto().getData().getScope())
      .hasSize(2)
      .extracting(CollectionScopeEntryDto::getId)
      .containsExactlyInAnyOrder("process:_KEY1_", "process:_KEY2_");
  }

  @Test
  public void updateDefinitionScopeEntry_addTenant() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");
    collectionClient.addScopeEntryToCollection(collectionId, entry);

    // when
    final String tenant1 = "tenant1";
    addTenantToElasticsearch(tenant1);
    entry.setTenants(Lists.newArrayList(null, tenant1));
    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(collectionId, entry.getId(), new CollectionScopeEntryUpdateDto(entry))
      .execute(204);

    // then
    ResolvedCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(ResolvedCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope())
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        scopeEntryDto -> assertThat(scopeEntryDto.getTenants()).containsExactly(null, tenant1)
      );
  }

  @Test
  public void updateDefinitionScopeEntry_removeTenant() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final String tenant = "tenant";
    addTenantToElasticsearch(tenant);
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");
    entry.getTenants().add(tenant);
    collectionClient.addScopeEntryToCollection(collectionId, entry);

    // when
    entry.setTenants(Collections.singletonList(null));
    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(collectionId, entry.getId(), new CollectionScopeEntryUpdateDto(entry))
      .execute(204);

    // then
    ResolvedCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(ResolvedCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope())
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        scopeEntryDto -> assertThat(scopeEntryDto.getTenants()).containsExactly((String) null)
      );
  }

  @Test
  public void updateUnknownScopeThrowsNotFound() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");
    collectionClient.addScopeEntryToCollection(collectionId, entry);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(collectionId, "fooScopeId", new CollectionScopeEntryUpdateDto(entry))
      .execute();

    // then not found is thrown
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void updateUnknownTenantsAreFilteredOut() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");
    collectionClient.addScopeEntryToCollection(collectionId, entry);

    // when
    final String tenant1 = "tenant1";
    addTenantToElasticsearch(tenant1);
    entry.setTenants(Lists.newArrayList(null, tenant1, "fooTenant"));
    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(collectionId, entry.getId(), new CollectionScopeEntryUpdateDto(entry))
      .execute(204);

    // then
    ResolvedCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(ResolvedCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope())
      .hasSize(1)
      .hasOnlyOneElementSatisfying(
        scopeEntryDto -> assertThat(scopeEntryDto.getTenants()).containsExactly(null, tenant1)
      );
  }

  @Test
  public void updatingNonExistingDefinitionScopeEntryFails() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = collectionClient.getCollection(collectionId);
    final String notExistingScopeEntryId = "PROCESS:abc";

    // when
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(
        collectionId,
        notExistingScopeEntryId,
        new CollectionScopeEntryUpdateDto(entry)
      )
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NOT_FOUND);

    assertThat(collectionClient.getCollection(collectionId)).isEqualTo(expectedCollection);
  }

  @Test
  public void removeScopeEntry() {
    String collectionId = collectionClient.createNewCollection();
    CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");

    collectionClient.addScopeEntryToCollection(collectionId, entry);

    SimpleCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope().size()).isEqualTo(1);

    embeddedOptimizeExtension.getRequestExecutor()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, entry.getId())
      .execute(204);

    collectionDefinitionDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope().size()).isEqualTo(0);
  }

  @Test
  public void removeScopeDefinitionFailsDueReportConflict() {
    String collectionId = collectionClient.createNewCollection();
    CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");

    collectionClient.addScopeEntryToCollection(collectionId, entry);

    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.getData().setProcessDefinitionKey("_KEY_");
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    String reportId = createNewSingleProcessReport(singleProcessReportDefinitionDto);

    ConflictResponseDto conflictResponseDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, entry.getId())
      .execute(ConflictResponseDto.class, 409);

    assertThat(conflictResponseDto.getConflictedItems())
      .extracting(ConflictedItemDto::getId)
      .containsExactly(reportId);
  }

  @Test
  public void removeNotExistingScopeDefinitionFails() {
    String collectionId = collectionClient.createNewCollection();

    embeddedOptimizeExtension.getRequestExecutor()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, "PROCESS:_KEY_")
      .execute(404);
  }

  private void addDecisionDefinitionToElasticsearch(final String key,
                                                    final String name) {
    final DecisionDefinitionOptimizeDto decisionDefinitionDto = DecisionDefinitionOptimizeDto.builder()
      .id(key)
      .key(key)
      .version("1")
      .engine(DEFAULT_ENGINE_ALIAS)
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
      .engine(DEFAULT_ENGINE_ALIAS)
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

  private String createNewSingleProcessReport(final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private void addTenantToElasticsearch(final String tenantId) {
    TenantDto tenantDto = new TenantDto(tenantId, "ATenantName", DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, tenantId, tenantDto);
  }
}

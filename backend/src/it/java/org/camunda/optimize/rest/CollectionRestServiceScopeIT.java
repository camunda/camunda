/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.http.HttpStatus;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryRestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;

public class CollectionRestServiceScopeIT extends AbstractIT {

  @Test
  public void partialCollectionUpdateDoesNotAffectScopes() {
    //given
    final String collectionId = createNewCollection();
    addScopeEntryToCollection(collectionId, createSimpleScopeEntry("_KEY_"));
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);

    // when
    final PartialCollectionDefinitionDto collectionRenameDto = new PartialCollectionDefinitionDto("Test");
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(204);
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getScope()).isEqualTo(expectedCollection.getData().getScope());
  }

  @Test
  public void getScopeForCollection() {
    // given
    final String collectionId = createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");
    addScopeEntryToCollection(collectionId, entry);

    // when
    List<CollectionScopeEntryRestDto> scopeEntries = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetScopeForCollectionRequest(collectionId)
      .execute(new TypeReference<List<CollectionScopeEntryRestDto>>() {
      });

    // then
    assertThat(scopeEntries)
      .containsExactly(
        new CollectionScopeEntryRestDto().setDefinitionKey("_KEY_")
          .setDefinitionName("_KEY_")
          .setDefinitionType(PROCESS)
          .setTenants(Collections.singletonList(TENANT_NOT_DEFINED))
      );
  }

  @Test
  public void scopesAreOrderByDefinitionTypeAndThenDefinitionName() {
    // given
    final String collectionId = createNewCollection();
    final CollectionScopeEntryDto decisionScope1 = createSimpleScopeEntry("DECISION_KEY_LAST", DECISION);
    final CollectionScopeEntryDto decisionScope2 = createSimpleScopeEntry("DECISION_KEY_FIRST", DECISION);
    final CollectionScopeEntryDto processScope1 = createSimpleScopeEntry("PROCESS_KEY_LAST", PROCESS);
    final CollectionScopeEntryDto processScope2 = createSimpleScopeEntry("PROCESS_KEY_FIRST", PROCESS);
    addScopeEntryToCollection(collectionId, decisionScope1);
    addScopeEntryToCollection(collectionId, processScope1);
    addScopeEntryToCollection(collectionId, decisionScope2);
    addScopeEntryToCollection(collectionId, processScope2);

    // when
    List<CollectionScopeEntryRestDto> scopeEntries = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetScopeForCollectionRequest(collectionId)
      .execute(new TypeReference<List<CollectionScopeEntryRestDto>>() {
      });

    // then
    assertThat(scopeEntries)
      .hasSize(4)
      .extracting(CollectionScopeEntryRestDto::getDefinitionName)
      .containsExactly("PROCESS_KEY_FIRST", "PROCESS_KEY_LAST", "DECISION_KEY_FIRST","DECISION_KEY_LAST");
  }

  @Test
  public void addDefinitionScopeEntry() {
    // given
    final String collectionId = createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");

    // when
    final String scopeEntryId = addScopeEntryToCollection(collectionId, entry);
    SimpleCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    // then
    assertThat(scopeEntryId).isEqualTo("process:_KEY_");
    assertThat(collectionDefinitionDto.getData().getScope().size()).isEqualTo(1);
    assertThat(collectionDefinitionDto.getData().getScope().get(0).getId()).isEqualTo(scopeEntryId);
  }

  @Test
  public void addMultipleDefinitionScopeEntries() {
    // given
    final String collectionId = createNewCollection();
    final CollectionScopeEntryDto entry1 = createSimpleScopeEntry("_KEY1_");
    final CollectionScopeEntryDto entry2 = createSimpleScopeEntry("_KEY2_");

    // when
    final String scopeEntryId1 = addScopeEntryToCollection(collectionId, entry1);
    final String scopeEntryId2 = addScopeEntryToCollection(collectionId, entry2);

    SimpleCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    // then
    assertThat(scopeEntryId1).isEqualTo("process:_KEY1_");
    assertThat(scopeEntryId2).isEqualTo("process:_KEY2_");
    assertThat(collectionDefinitionDto.getData().getScope()).containsExactlyInAnyOrder(entry1, entry2);
  }

  @Test
  public void addConflictingScopeDefinitionFails() {
    // given
    final String collectionId = createNewCollection();
    addScopeEntryToCollection(collectionId, createSimpleScopeEntry("_KEY_"));

    // when
    embeddedOptimizeExtension.getRequestExecutor()
      .buildAddScopeEntryToCollectionRequest(collectionId, createSimpleScopeEntry("_KEY_"))
      // then
      .execute(409);
  }

  @Test
  public void updateDefinitionScopeEntry() {
    // given
    final String collectionId = createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");
    final String scopeEntryId = addScopeEntryToCollection(collectionId, entry);

    // when
    entry.setVersions(Collections.singletonList("1"));
    embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(collectionId, scopeEntryId, new CollectionScopeEntryUpdateDto(entry))
      .execute(204);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    ResolvedCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(ResolvedCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope().get(0).getVersions().size()).isEqualTo(1);
    assertThat(collectionDefinitionDto.getData().getScope().get(0).getVersions().get(0)).isEqualTo("1");
  }

  @Test
  public void updatingNonExistingDefinitionScopeEntryFails() {
    // given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);
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

    assertThat(getCollection(collectionId)).isEqualTo(expectedCollection);
  }

  @Test
  public void removeScopeEntry() {
    String collectionId = createNewCollection();
    CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");

    String scopeEntryId = addScopeEntryToCollection(collectionId, entry);

    SimpleCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope().size()).isEqualTo(1);

    embeddedOptimizeExtension.getRequestExecutor()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, scopeEntryId)
      .execute(204);

    collectionDefinitionDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope().size()).isEqualTo(0);
  }

  @Test
  public void removeScopeDefinitionFailsDueReportConflict() {
    String collectionId = createNewCollection();
    CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");

    final String scopeEntryId = addScopeEntryToCollection(collectionId, entry);

    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.getData().setProcessDefinitionKey("_KEY_");
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    String reportId = createNewSingleProcessReport(singleProcessReportDefinitionDto);

    ConflictResponseDto conflictResponseDto = embeddedOptimizeExtension.getRequestExecutor()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, scopeEntryId)
      .execute(ConflictResponseDto.class, 409);

    assertThat(conflictResponseDto.getConflictedItems())
      .extracting(ConflictedItemDto::getId)
      .containsExactly(reportId);
  }

  @Test
  public void removeNotExistingScopeDefinitionFails() {
    String collectionId = createNewCollection();

    embeddedOptimizeExtension.getRequestExecutor()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, "PROCESS:_KEY_")
      .execute(404);
  }

  private CollectionScopeEntryDto createSimpleScopeEntry(String definitionKey) {
    return createSimpleScopeEntry(definitionKey, PROCESS);
  }

  private CollectionScopeEntryDto createSimpleScopeEntry(String definitionKey, DefinitionType definitionType) {
    return new CollectionScopeEntryDto(
      definitionType, definitionKey, Collections.singletonList(null), Collections.singletonList("ALL")
    );
  }

  private String addScopeEntryToCollection(final String collectionId, final CollectionScopeEntryDto entry) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildAddScopeEntryToCollectionRequest(collectionId, entry)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewSingleProcessReport(final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private SimpleCollectionDefinitionDto getCollection(final String id) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(id)
      .execute(SimpleCollectionDefinitionDto.class, 200);
  }

  private String createNewCollection() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }
}

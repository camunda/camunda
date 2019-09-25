/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.apache.http.HttpStatus;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class CollectionRestServiceScopeIT {

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void partialCollectionUpdateDoesNotAffectScopes() {
    //given
    final String collectionId = createNewCollection();
    addScopeEntryToCollection(collectionId, createSimpleScopeEntry("_KEY_"));
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);

    // when
    final PartialCollectionUpdateDto collectionRenameDto = new PartialCollectionUpdateDto("Test");
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest(collectionId, collectionRenameDto)
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
    final SimpleCollectionDefinitionDto collection = getCollection(collectionId);
    assertThat(collection.getData().getScope(), is(expectedCollection.getData().getScope()));
  }

  @Test
  public void addDefinitionScopeEntry() {
    // given
    final String collectionId = createNewCollection();
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");

    // when
    final String scopeEntryId = addScopeEntryToCollection(collectionId, entry);
    SimpleCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeRule.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    // then
    assertThat(scopeEntryId, is("process:_KEY_"));
    assertThat(collectionDefinitionDto.getData().getScope().size(), is(1));
    assertThat(collectionDefinitionDto.getData().getScope().get(0).getId(), is(scopeEntryId));
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

    SimpleCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeRule.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    // then
    assertThat(scopeEntryId1, is("process:_KEY1_"));
    assertThat(scopeEntryId2, is("process:_KEY2_"));
    assertThat(collectionDefinitionDto.getData().getScope(), containsInAnyOrder(entry1, entry2));
  }

  @Test
  public void addConflictingScopeDefinitionFails() {
    // given
    final String collectionId = createNewCollection();
    addScopeEntryToCollection(collectionId, createSimpleScopeEntry("_KEY_"));

    // when
    embeddedOptimizeRule.getRequestExecutor()
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
    embeddedOptimizeRule.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(collectionId, scopeEntryId, new CollectionScopeEntryUpdateDto(entry))
      .execute(204);

    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    ResolvedCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeRule.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(ResolvedCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope().get(0).getVersions().size(), is(1));
    assertThat(collectionDefinitionDto.getData().getScope().get(0).getVersions().get(0), is("1"));
  }

  @Test
  public void updatingNonExistingDefinitionScopeEntryFails() {
    // given
    final String collectionId = createNewCollection();
    final SimpleCollectionDefinitionDto expectedCollection = getCollection(collectionId);
    final String notExistingScopeEntryId = "PROCESS:abc";

    // when
    final CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(
        collectionId,
        notExistingScopeEntryId,
        new CollectionScopeEntryUpdateDto(entry)
      )
      .execute();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_NOT_FOUND));

    assertThat(getCollection(collectionId), is(expectedCollection));
  }

  @Test
  public void removeScopeEntry() {
    String collectionId = createNewCollection();
    CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");

    String scopeEntryId = addScopeEntryToCollection(collectionId, entry);

    SimpleCollectionDefinitionDto collectionDefinitionDto = embeddedOptimizeRule.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope().size(), is(1));

    embeddedOptimizeRule.getRequestExecutor()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, scopeEntryId)
      .execute(204);

    collectionDefinitionDto = embeddedOptimizeRule.getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(SimpleCollectionDefinitionDto.class, 200);

    assertThat(collectionDefinitionDto.getData().getScope().size(), is(0));
  }

  @Test
  public void removeScopeDefinitionFailsDueReportConflict() {
    String collectionId = createNewCollection();
    CollectionScopeEntryDto entry = createSimpleScopeEntry("_KEY_");

    final String scopeEntryId = addScopeEntryToCollection(collectionId, entry);

    String reportId = createNewSingleProcessReportInCollection(collectionId);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.getData().setProcessDefinitionKey("_KEY_");
    updateReport(reportId, report);

    ConflictResponseDto conflictResponseDto = embeddedOptimizeRule.getRequestExecutor()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, scopeEntryId)
      .execute(ConflictResponseDto.class, 409);

    assertThat(
      conflictResponseDto.getConflictedItems().stream().map(ConflictedItemDto::getId).collect(Collectors.toList()),
      contains(reportId)
    );
  }

  @Test
  public void removeNotExistingScopeDefinitionFails() {
    String collectionId = createNewCollection();

    embeddedOptimizeRule.getRequestExecutor()
      .buildRemoveScopeEntryFromCollectionRequest(collectionId, "PROCESS:_KEY_")
      .execute(404);
  }

  private CollectionScopeEntryDto createSimpleScopeEntry(String definitionKey) {
    return new CollectionScopeEntryDto(
      DefinitionType.PROCESS, definitionKey, Collections.singletonList("ALL"), Collections.singletonList(null)
    );
  }

  private void updateReport(final String id, final SingleProcessReportDefinitionDto updatedReport) {
    embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute(204);
  }

  private String addScopeEntryToCollection(final String collectionId, final CollectionScopeEntryDto entry) {
    return embeddedOptimizeRule.getRequestExecutor()
      .buildAddScopeEntryToCollectionRequest(collectionId, entry)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewSingleProcessReportInCollection(final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();
  }

  private SimpleCollectionDefinitionDto getCollection(final String id) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetCollectionRequest(id)
      .execute(SimpleCollectionDefinitionDto.class, 200);
  }

  private String createNewCollection() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }
}

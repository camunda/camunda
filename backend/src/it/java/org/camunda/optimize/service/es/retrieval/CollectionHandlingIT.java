/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntityUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRole;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.es.writer.CollectionWriter.DEFAULT_COLLECTION_NAME;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;


public class CollectionHandlingIT {

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void collectionIsWrittenToElasticsearch() throws IOException {
    // given
    String id = createNewCollection();

    // then
    GetRequest getRequest = new GetRequest(COLLECTION_INDEX_NAME, COLLECTION_INDEX_NAME, id);
    GetResponse getResponse = elasticSearchRule.getOptimizeElasticClient().get(getRequest, RequestOptions.DEFAULT);

    // then
    assertThat(getResponse.isExists(), is(true));
  }

  @Test
  public void newCollectionIsCorrectlyInitialized() {
    // given
    String id = createNewCollection();

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections, is(notNullValue()));
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto collection = collections.get(0);
    assertThat(collection.getId(), is(id));
    assertThat(collection.getName(), is(DEFAULT_COLLECTION_NAME));
    assertThat(collection.getData().getEntities(), notNullValue());
    assertThat(collection.getData().getEntities().size(), is(0));
    assertThat(collection.getData().getConfiguration(), notNullValue());
    // author is automatically added as manager
    assertThat(collection.getData().getRoles(), notNullValue());
    assertThat(collection.getData().getRoles().size(), is(1));
    final CollectionRoleDto roleDto = collection.getData().getRoles().get(0);
    assertThat(roleDto.getId(), is(notNullValue()));
    assertThat(roleDto.getIdentity(), is(notNullValue()));
    assertThat(roleDto.getIdentity().getId(), is(DEFAULT_USERNAME));
    assertThat(roleDto.getIdentity().getType(), is(IdentityType.USER));
    assertThat(roleDto.getRole(), is(CollectionRole.MANAGER));
  }

  @Test
  public void returnEmptyListWhenNoCollectionIsDefined() {
    // given
    createNewSingleReport();

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections, is(notNullValue()));
    assertThat(collections.size(), is(0));
  }

  @Test
  public void updateCollection() {
    // given
    String id = createNewCollection();
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    PartialCollectionUpdateDto collectionUpdate = new PartialCollectionUpdateDto();
    collectionUpdate.setName("MyCollection");
    final Map<String, String> configuration = Collections.singletonMap("Foo", "Bar");
    final PartialCollectionDataDto data = new PartialCollectionDataDto();
    data.setConfiguration(configuration);
    collectionUpdate.setData(data);


    // when
    updateCollectionRequest(id, collectionUpdate);
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto storedCollection = collections.get(0);
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getName(), is("MyCollection"));
    assertThat(storedCollection.getLastModifier(), is("demo"));
    assertThat(storedCollection.getLastModified(), is(now));
    final ResolvedCollectionDataDto resultCollectionData = storedCollection.getData();
    assertEquals(resultCollectionData.getConfiguration(), configuration);
    assertThat(resultCollectionData.getEntities().size(), is(0));
  }

  @Test
  public void updatePartialCollection() {
    // given
    String id = createNewCollection();
    String reportId = createNewSingleReport();
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    // when (update only name)
    PartialCollectionUpdateDto collectionUpdate = new PartialCollectionUpdateDto();
    collectionUpdate.setName("MyCollection");

    updateCollectionRequest(id, collectionUpdate);
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto storedCollection = collections.get(0);
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getName(), is("MyCollection"));
    assertThat(storedCollection.getLastModifier(), is("demo"));
    assertThat(storedCollection.getLastModified(), is(now));

    // when (update only configuration)
    collectionUpdate = new PartialCollectionUpdateDto();
    final Map<String, String> configuration = Collections.singletonMap("Foo", "Bar");
    PartialCollectionDataDto data = new PartialCollectionDataDto();
    data.setConfiguration(configuration);
    collectionUpdate.setData(data);

    updateCollectionRequest(id, collectionUpdate);
    collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    storedCollection = collections.get(0);
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getName(), is("MyCollection"));
    assertThat(storedCollection.getLastModifier(), is("demo"));
    assertThat(storedCollection.getLastModified(), is(now));
    ResolvedCollectionDataDto resultCollectionData = storedCollection.getData();
    assertEquals(resultCollectionData.getConfiguration(), configuration);


    // when (again only update name)
    collectionUpdate = new PartialCollectionUpdateDto();
    collectionUpdate.setName("TestNewCollection");

    updateCollectionRequest(id, collectionUpdate);
    collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    storedCollection = collections.get(0);
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getName(), is("TestNewCollection"));
    assertThat(storedCollection.getLastModifier(), is("demo"));
    assertThat(storedCollection.getLastModified(), is(now));
    resultCollectionData = storedCollection.getData();
    assertEquals(resultCollectionData.getConfiguration(), configuration);
  }

  @Test
  public void dashboardCanBeAddedToCollection() {
    // given
    String collectionId = createNewCollection();
    String dashboardId = createNewDashboard();

    // when
    moveDashboardToCollection(dashboardId, collectionId);
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto collection1 = collections.get(0);
    DashboardDefinitionDto dashboard = (DashboardDefinitionDto) collection1.getData().getEntities().get(0);
    assertThat(dashboard.getId(), is(dashboardId));
  }

  @Test
  public void collectionItemsAreOrderedByModificationDateDescending() {
    // given
    String collectionId = createNewCollection();
    String reportId1 = createNewSingleReport();
    String reportId2 = createNewSingleReport();
    String dashboardId = createNewDashboard();

    moveReportsToCollection(ImmutableList.of(reportId1, reportId2), collectionId);
    moveDashboardToCollection(dashboardId, collectionId);
    updateReport(reportId1, new SingleProcessReportDefinitionDto());

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto collection = collections.get(0);
    assertThat(collection.getData().getEntities().get(0).getId(), is(reportId1));
    assertThat(collection.getData().getEntities().get(1).getId(), is(dashboardId));
    assertThat(collection.getData().getEntities().get(2).getId(), is(reportId2));
  }

  @Test
  public void entityCanBeAddedAndRemovedFromCollection() {
    // given
    String collectionId = createNewCollection();
    String dashboardId = createNewDashboard();

    // when (add)
    moveDashboardToCollection(dashboardId, collectionId);
    elasticSearchRule.refreshAllOptimizeIndices();
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto collection1 = collections.get(0);
    DashboardDefinitionDto dashboard = (DashboardDefinitionDto) collection1.getData().getEntities().get(0);
    assertThat(dashboard.getId(), is(dashboardId));


    // when (remove)
    removeDashboardFromCollection(dashboardId);
    elasticSearchRule.refreshAllOptimizeIndices();
    collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    collection1 = collections.get(0);
    assertThat(collection1.getData().getEntities().size(), is(0));
  }


  @Test
  public void entityCanOnlyBeInOneCollectionAtATime() {
    // given
    String collectionId1 = createNewCollection();
    String collectionId2 = createNewCollection();
    String reportId = createNewSingleReport();
    moveReportToCollection(reportId, collectionId1);
    moveReportToCollection(reportId, collectionId2);
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(2));
    final Map<String, ResolvedCollectionDefinitionDto> resultMap = collections.stream()
      .collect(toMap(entry -> entry.getId(), entry -> entry));

    final ResolvedCollectionDefinitionDto collection1 = resultMap.get(collectionId1);
    assertThat(collection1.getData().getEntities().size(), is(0));

    final ResolvedCollectionDefinitionDto collection2 = resultMap.get(collectionId2);
    assertThat(collection2.getData().getEntities().size(), is(1));
    ReportDefinitionDto report = (ReportDefinitionDto) collection2.getData().getEntities().get(0);
    assertThat(report.getId(), is(reportId));
  }

  @Test
  public void doNotUpdateNullFieldsInCollection() {
    // given
    String id = createNewCollection();
    PartialCollectionUpdateDto collection = new PartialCollectionUpdateDto();

    // when
    updateCollectionRequest(id, collection);
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(1));
    ResolvedCollectionDefinitionDto storedCollection = collections.get(0);
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getCreated(), is(notNullValue()));
    assertThat(storedCollection.getLastModified(), is(notNullValue()));
    assertThat(storedCollection.getLastModifier(), is(notNullValue()));
    assertThat(storedCollection.getName(), is(notNullValue()));
    assertThat(storedCollection.getOwner(), is(notNullValue()));
  }

  @Test
  public void resultListIsSortedByName() {
    // given
    String id1 = createNewCollection();
    String id2 = createNewCollection();

    PartialCollectionUpdateDto collection = new PartialCollectionUpdateDto();
    collection.setName("B_collection");
    updateCollectionRequest(id1, collection);
    collection.setName("A_collection");
    updateCollectionRequest(id2, collection);

    // when
    List<ResolvedCollectionDefinitionDto> collections = getAllResolvedCollections();

    // then
    assertThat(collections.size(), is(2));
    assertThat(collections.get(0).getId(), is(id2));
    assertThat(collections.get(1).getId(), is(id1));
  }


  @Test
  public void deletedReportsAreRemovedFromCollectionWhenForced() {
    // given
    String collectionId = createNewCollection();
    String singleReportIdToDelete = createNewSingleReport();
    String combinedReportIdToDelete = createNewCombinedReport();

    moveReportsToCollection(Arrays.asList(singleReportIdToDelete, combinedReportIdToDelete), collectionId);
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    deleteReport(singleReportIdToDelete);
    deleteReport(combinedReportIdToDelete);

    // then
    List<ResolvedCollectionDefinitionDto> allResolvedCollections = getAllResolvedCollections();
    assertThat(allResolvedCollections.size(), is(1));
    assertThat(allResolvedCollections.get(0).getData().getEntities().size(), is(0));
  }

  @Test
  public void deletedDashboardsAreRemovedFromCollectionWhenForced() {
    // given
    String collectionId = createNewCollection();
    String dashboardIdToDelete = createNewDashboard();

    moveDashboardToCollection(dashboardIdToDelete, collectionId);
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    deleteDashboard(dashboardIdToDelete);

    // then
    List<ResolvedCollectionDefinitionDto> allResolvedCollections = getAllResolvedCollections();
    assertThat(allResolvedCollections.size(), is(1));
    assertThat(allResolvedCollections.get(0).getData().getEntities().size(), is(0));
  }

  @Test
  public void entitiesAreDeletedOnCollectionDelete() {
    // given
    String collectionId = createNewCollection();
    String singleReportId = createNewSingleReport();
    String combinedReportId = createNewCombinedReport();
    String dashboardId = createNewDashboard();

    moveReportsToCollection(Arrays.asList(singleReportId, combinedReportId), collectionId);
    moveDashboardToCollection(dashboardId, collectionId);
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    deleteCollection(collectionId);

    // then
    List<ResolvedCollectionDefinitionDto> allResolvedCollections = getAllResolvedCollections();
    assertThat(allResolvedCollections.size(), is(0));

    assertDashboardIsDeleted(dashboardId);
    assertReportIsDeleted(singleReportId);
    assertReportIsDeleted(combinedReportId);
  }

  private void assertReportIsDeleted(final String singleReportIdToDelete) {
    final Response response = embeddedOptimizeRule.getRequestExecutor()
      .buildGetReportRequest(singleReportIdToDelete)
      .execute();
    assertThat(response.getStatus(), is(404));
  }

  private void assertDashboardIsDeleted(final String dashboardIdToDelete) {
    final Response response = embeddedOptimizeRule.getRequestExecutor()
      .buildGetDashboardRequest(dashboardIdToDelete)
      .execute();
    assertThat(response.getStatus(), is(404));
  }

  private void moveDashboardToCollection(String entityId, String collectionId) {
    elasticSearchRule.moveDashboardToCollection(entityId, collectionId);
  }


  private String moveReportToCollection(String reportId, final String collectionId) {
    final ReportDefinitionDto definition = embeddedOptimizeRule.getRequestExecutor()
      .buildGetReportRequest(reportId)
      .execute(ReportDefinitionDto.class, 200);

    if (definition.getCombined()) {
      elasticSearchRule.moveCombinedReportToCollection(reportId, collectionId);
    } else {
      elasticSearchRule.moveSingleReportToCollection(reportId, definition.getReportType(), collectionId);
    }
    return collectionId;
  }


  private void moveReportsToCollection(List<String> entityIds, String collectionId) {
    for (String id : entityIds) {
      moveReportToCollection(id, collectionId);
    }
  }

  private void removeDashboardFromCollection(String entityId) {
    moveDashboardToCollection(entityId, null);
  }

  private String createNewSingleReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewDashboard() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewCombinedReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private Response deleteCollection(String id) {
    return embeddedOptimizeRule.getRequestExecutor()
      .buildDeleteCollectionRequest(id, true)
      .execute();
  }

  private void deleteReport(String reportId) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, true)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private void deleteDashboard(String dashboardId) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteDashboardRequest(dashboardId, true)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private String createNewCollection() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateCollectionRequest(String id, PartialCollectionUpdateDto renameCollection) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdatePartialCollectionRequest(id, renameCollection)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private void updateReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response = getUpdateReportResponse(id, updatedReport);
    assertThat(response.getStatus(), is(204));
  }

  private Response getUpdateReportResponse(String id, SingleProcessReportDefinitionDto updatedReport) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();
  }

  private List<ResolvedCollectionDefinitionDto> getAllResolvedCollections() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllCollectionsRequest()
      .executeAndReturnList(ResolvedCollectionDefinitionDto.class, 200);
  }


}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.es.writer.CollectionWriter.DEFAULT_COLLECTION_NAME;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public class CollectionHandlingIT extends AbstractIT {

  private static Stream<DefinitionType> definitionTypes() {
    return Stream.of(PROCESS, DECISION);
  }

  @Test
  public void collectionIsWrittenToElasticsearch() throws IOException {
    // given
    String id = collectionClient.createNewCollection();

    // then
    GetRequest getRequest = new GetRequest()
      .index(COLLECTION_INDEX_NAME)
      .id(id);
    GetResponse getResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .get(getRequest, RequestOptions.DEFAULT);

    // then
    assertThat(getResponse.isExists()).isTrue();
  }

  @Test
  public void newCollectionIsCorrectlyInitialized() {
    // given
    String id = collectionClient.createNewCollection();

    // when
    CollectionDefinitionRestDto collection = collectionClient.getCollectionById(id);
    List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(id);

    // then
    assertThat(collection.getId()).isEqualTo(id);
    assertThat(collection.getName()).isEqualTo(DEFAULT_COLLECTION_NAME);
    assertThat(collectionEntities).isEmpty();
    assertThat(collection.getData().getConfiguration()).isNotNull();
  }

  @Test
  public void getResolvedCollection() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    final String reportId = createNewSingleProcessReportInCollection(collectionId);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    CollectionDefinitionRestDto collection = collectionClient.getCollectionById(collectionId);
    List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);

    // then
    assertThat(collection).isNotNull();
    assertThat(collection.getId()).isEqualTo(collectionId);
    assertThat(collectionEntities.size()).isEqualTo(2);
    assertThat(collectionEntities.stream().map(EntityDto::getId).collect(Collectors.toList()))
      .containsExactlyInAnyOrder(dashboardId, reportId);
  }

  @Test
  public void getResolvedCollectionContainsCombinedReportSubEntityCounts() {
    //given
    final String collectionId = collectionClient.createNewCollection();
    final String reportId1 = createNewSingleProcessReportInCollection(collectionId);
    final String reportId2 = createNewSingleProcessReportInCollection(collectionId);
    final String combinedReportId = reportClient.createEmptyCombinedReport(collectionId);

    final CombinedReportDefinitionDto combinedReportUpdate = new CombinedReportDefinitionDto();
    combinedReportUpdate.setData(createCombinedReportData(reportId1, reportId2));
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportUpdate)
      .execute();

    // when
    CollectionDefinitionRestDto collection = collectionClient.getCollectionById(collectionId);
    List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);

    // then
    assertThat(collection).isNotNull();
    assertThat(collection.getId()).isEqualTo(collectionId);
    assertThat(collectionEntities.size()).isEqualTo(3);
    final EntityDto combinedReportEntityDto = collectionEntities.stream()
      .filter(EntityDto::getCombined)
      .findFirst()
      .get();
    assertThat(combinedReportEntityDto.getData().getSubEntityCounts().size()).isEqualTo(1);
    assertThat(combinedReportEntityDto.getData().getSubEntityCounts().get(EntityType.REPORT)).isEqualTo(2L);
  }

  @Test
  public void updateCollection() {
    // given
    String id = collectionClient.createNewCollection();
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    PartialCollectionDefinitionDto collectionUpdate = new PartialCollectionDefinitionDto();
    collectionUpdate.setName("MyCollection");
    final Map<String, String> configuration = Collections.singletonMap("Foo", "Bar");
    final PartialCollectionDataDto data = new PartialCollectionDataDto();
    data.setConfiguration(configuration);
    collectionUpdate.setData(data);

    // when
    collectionClient.updateCollection(id, collectionUpdate);
    CollectionDefinitionRestDto collection = collectionClient.getCollectionById(id);
    List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(id);

    // then
    assertThat(collection.getId()).isEqualTo(id);
    assertThat(collection.getName()).isEqualTo("MyCollection");
    assertThat(collection.getLastModifier()).isEqualTo("demo");
    assertThat(collection.getLastModified()).isEqualTo(now);
    assertThat(collection.getData().getConfiguration()).isEqualTo(configuration);
    assertThat(collectionEntities.size()).isEqualTo(0);
  }

  @Test
  public void updatePartialCollection() {
    // given
    String id = collectionClient.createNewCollection();
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    // when (update only name)
    PartialCollectionDefinitionDto collectionUpdate = new PartialCollectionDefinitionDto();
    collectionUpdate.setName("MyCollection");

    collectionClient.updateCollection(id, collectionUpdate);
    CollectionDefinitionRestDto collection = collectionClient.getCollectionById(id);

    // then
    assertThat(collection.getId()).isEqualTo(id);
    assertThat(collection.getName()).isEqualTo("MyCollection");
    assertThat(collection.getLastModifier()).isEqualTo("demo");
    assertThat(collection.getLastModified()).isEqualTo(now);

    // when (update only configuration)
    collectionUpdate = new PartialCollectionDefinitionDto();
    final Map<String, String> configuration = Collections.singletonMap("Foo", "Bar");
    PartialCollectionDataDto data = new PartialCollectionDataDto();
    data.setConfiguration(configuration);
    collectionUpdate.setData(data);

    collectionClient.updateCollection(id, collectionUpdate);
    collection = collectionClient.getCollectionById(id);

    // then
    assertThat(collection.getId()).isEqualTo(id);
    assertThat(collection.getName()).isEqualTo("MyCollection");
    assertThat(collection.getLastModifier()).isEqualTo("demo");
    assertThat(collection.getLastModified()).isEqualTo(now);
    CollectionDataDto resultCollectionData = collection.getData();
    assertThat(resultCollectionData.getConfiguration()).isEqualTo(configuration);


    // when (again only update name)
    collectionUpdate = new PartialCollectionDefinitionDto();
    collectionUpdate.setName("TestNewCollection");

    collectionClient.updateCollection(id, collectionUpdate);
    collection = collectionClient.getCollectionById(id);

    // then
    assertThat(collection.getId()).isEqualTo(id);
    assertThat(collection.getName()).isEqualTo("TestNewCollection");
    assertThat(collection.getLastModifier()).isEqualTo("demo");
    assertThat(collection.getLastModified()).isEqualTo(now);
    resultCollectionData = collection.getData();
    assertThat(resultCollectionData.getConfiguration()).isEqualTo(configuration);
  }

  @Test
  public void singleProcessReportCanBeCreatedInsideCollection() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String reportId = createNewSingleProcessReportInCollection(collectionId);

    // when
    List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);

    // then
    EntityDto report = collectionEntities.get(0);
    assertThat(report.getId()).isEqualTo(reportId);
    assertThat(report.getEntityType()).isEqualTo(EntityType.REPORT);
    assertThat(report.getReportType()).isEqualTo(ReportType.PROCESS);
    assertThat(report.getCombined()).isEqualTo(false);
  }

  @Test
  public void singleDecisionReportCanBeCreatedInsideCollection() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String reportId = createNewSingleDecisionReportInCollection(collectionId);

    // when
    List<EntityDto> copiedCollectionEntities = collectionClient.getEntitiesForCollection(collectionId);

    // then
    EntityDto report = copiedCollectionEntities.get(0);
    assertThat(report.getId()).isEqualTo(reportId);
    assertThat(report.getEntityType()).isEqualTo(EntityType.REPORT);
    assertThat(report.getReportType()).isEqualTo(ReportType.DECISION);
    assertThat(report.getCombined()).isEqualTo(false);
  }

  @Test
  public void combinedProcessReportCanBeCreatedInsideCollection() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String reportId = reportClient.createEmptyCombinedReport(collectionId);

    // when
    List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);

    // then
    EntityDto report = collectionEntities.get(0);
    assertThat(report.getId()).isEqualTo(reportId);
    assertThat(report.getEntityType()).isEqualTo(EntityType.REPORT);
    assertThat(report.getReportType()).isEqualTo(ReportType.PROCESS);
    assertThat(report.getCombined()).isEqualTo(true);
  }

  @Test
  public void dashboardCanBeCreatedInsideCollection() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    // when
    List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);

    // then
    EntityDto dashboard = collectionEntities.get(0);
    assertThat(dashboard.getId()).isEqualTo(dashboardId);
    assertThat(dashboard.getEntityType()).isEqualTo(EntityType.DASHBOARD);
    assertThat(dashboard.getReportType()).isNull();
    assertThat(dashboard.getCombined()).isNull();
  }

  @Test
  public void singleProcessReportCanNotBeCreatedForInvalidCollection() {
    // given
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setCollectionId("invalidId");

    // when
    final Response createResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute();

    // then
    assertThat(createResponse.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void singleDecisionReportCanNotBeCreatedForInvalidCollection() {
    // given
    SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
    singleDecisionReportDefinitionDto.setCollectionId("invalidId");

    // when
    final Response createResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute();

    // then
    assertThat(createResponse.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void combinedProcessReportCanNotBeCreatedForInvalidCollection() {
    // given
    CombinedReportDefinitionDto combinedReportDefinitionDto = new CombinedReportDefinitionDto();
    combinedReportDefinitionDto.setCollectionId("invalidId");

    // when
    final Response createResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute();

    // then
    assertThat(createResponse.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void dashboardCanNotBeCreatedForInvalidCollection() {
    // given
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setCollectionId("invalidId");

    // when
    final Response createResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute();

    // then
    assertThat(createResponse.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void collectionItemsAreOrderedByTypeAndModificationDateDescending() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String reportId1 = createNewSingleProcessReportInCollection(collectionId);
    String reportId2 = createNewSingleProcessReportInCollection(collectionId);
    String dashboardId1 = dashboardClient.createEmptyDashboard(collectionId);
    String dashboardId2 = dashboardClient.createEmptyDashboard(collectionId);

    reportClient.updateSingleProcessReport(reportId1, new SingleProcessReportDefinitionDto());

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);

    // then
    assertThat(collectionEntities.get(0).getId()).isEqualTo(dashboardId2);
    assertThat(collectionEntities.get(1).getId()).isEqualTo(dashboardId1);
    assertThat(collectionEntities.get(2).getId()).isEqualTo(reportId1);
    assertThat(collectionEntities.get(3).getId()).isEqualTo(reportId2);
  }

  @Test
  public void doNotUpdateNullFieldsInCollection() {
    // given
    String id = collectionClient.createNewCollection();
    PartialCollectionDefinitionDto collection = new PartialCollectionDefinitionDto();

    // when
    collectionClient.updateCollection(id, collection);
    CollectionDefinitionRestDto storedCollection = collectionClient.getCollectionById(id);

    // then
    assertThat(storedCollection.getId()).isEqualTo(id);
    assertThat(storedCollection.getCreated()).isNotNull();
    assertThat(storedCollection.getLastModified()).isNotNull();
    assertThat(storedCollection.getLastModifier()).isNotNull();
    assertThat(storedCollection.getName()).isNotNull();
    assertThat(storedCollection.getOwner()).isNotNull();
  }

  @Test
  public void deletedReportsAreRemovedFromCollectionWhenForced() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String singleReportIdToDelete = createNewSingleProcessReportInCollection(collectionId);
    String combinedReportIdToDelete = reportClient.createEmptyCombinedReport(collectionId);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    reportClient.deleteReport(singleReportIdToDelete, true);
    reportClient.deleteReport(combinedReportIdToDelete, true);

    // then
    List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);
    assertThat(collectionEntities.size()).isEqualTo(0);
  }

  @Test
  public void deletedDashboardsAreRemovedFromCollectionWhenForced() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String dashboardIdToDelete = dashboardClient.createEmptyDashboard(collectionId);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    dashboardClient.deleteDashboard(dashboardIdToDelete, true);

    // then
    List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);
    assertThat(collectionEntities.size()).isEqualTo(0);
  }

  @Test
  public void entitiesAreDeletedOnCollectionDelete() {
    // given
    String collectionId = collectionClient.createNewCollection();
    String singleReportId = createNewSingleProcessReportInCollection(collectionId);
    String combinedReportId = reportClient.createEmptyCombinedReport(collectionId);
    String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    collectionClient.deleteCollection(collectionId);

    // then
    final Response getCollectionByIdResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute();
    assertThat(getCollectionByIdResponse.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

    assertDashboardIsDeleted(dashboardId);
    assertReportIsDeleted(singleReportId);
    assertReportIsDeleted(combinedReportId);
  }

  @Test
  public void copyAnEmptyCollectionWithCustomPermissionsAndScope() {
    //given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.grantUserOptimizeAccess("kermit");
    String collectionId = collectionClient.createNewCollection();
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, new CollectionRoleDto(
        new IdentityDto("kermit", IdentityType.USER),
        RoleType.EDITOR
      ))
      .execute();

    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddScopeEntryToCollectionRequest(collectionId, new CollectionScopeEntryDto("PROCESS:invoice"))
      .execute();

    //when
    IdDto copyId = collectionClient.copyCollection(collectionId);

    CollectionDefinitionRestDto copyDefinition = collectionClient.getCollectionById(copyId.getId());

    //then
    assertThat(copyDefinition.getName().toLowerCase().contains("copy")).isEqualTo(true);
  }

  @Test
  public void copyCollectionWithASingleReport() {
    //given
    String collectionId = collectionClient.createNewCollection();
    String originalReportId = createNewSingleProcessReportInCollection(collectionId);

    // when
    IdDto copyId = collectionClient.copyCollection(collectionId);

    List<EntityDto> copiedCollectionEntities = collectionClient.getEntitiesForCollection(copyId.getId());

    String reportCopyId = copiedCollectionEntities.get(0).getId();

    SingleProcessReportDefinitionDto originalReport = reportClient.getSingleProcessReportDefinitionDto(originalReportId);

    SingleProcessReportDefinitionDto copiedReport = reportClient.getSingleProcessReportDefinitionDto(reportCopyId);

    //then
    assertThat(originalReport.getData()).isEqualTo(copiedReport.getData());
    assertThat(copiedReport.getName()).isEqualTo(originalReport.getName());
  }

  @Test
  public void copyCollectionWithADashboard() {
    //given
    String collectionId = collectionClient.createNewCollection();
    String originalReportId = createNewSingleProcessReportInCollection(collectionId);
    String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    DashboardDefinitionDto dashboardDefinition = dashboardClient.getDashboard(dashboardId);

    dashboardDefinition.setReports(Collections.singletonList(new ReportLocationDto(
      originalReportId,
      new PositionDto(),
      new DimensionDto(),
      null
    )));

    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardDefinition)
      .execute();

    //when
    IdDto collectionCopyId = collectionClient.copyCollection(collectionId);

    CollectionDefinitionRestDto copiedCollectionDefinition =
      collectionClient.getCollectionById(collectionCopyId.getId());
    List<EntityDto> copiedCollectionEntities = collectionClient.getEntitiesForCollection(collectionId);

    String copiedDashboardId = copiedCollectionEntities.stream()
      .filter(e -> e.getEntityType().equals(EntityType.DASHBOARD)).findFirst().get().getId();

    String copiedReportId = copiedCollectionEntities.stream()
      .filter(e -> e.getEntityType().equals(EntityType.REPORT)).findFirst().get().getId();

    DashboardDefinitionDto copiedDashboard = dashboardClient.getDashboard(copiedDashboardId);

    SingleProcessReportDefinitionDto copiedReportDefinition = reportClient.getSingleProcessReportDefinitionDto(copiedReportId);
    SingleProcessReportDefinitionDto originalReportDefinition = reportClient.getSingleProcessReportDefinitionDto(originalReportId);
    //then
    //the dashboard references the same report entity as the report itself
    assertThat(copiedDashboard.getReports().get(0).getId()).isEqualTo(copiedReportId);

    assertThat(copiedDashboard.getName()).isEqualTo(dashboardDefinition.getName());
    assertThat(copiedReportDefinition.getName()).isEqualTo(originalReportDefinition.getName());

    assertThat(copiedCollectionEntities.size()).isEqualTo(2);
    assertThat(copiedCollectionEntities.stream()
                 .anyMatch(e -> e.getId().equals(copiedReportId))).isEqualTo(true);
    assertThat(copiedCollectionEntities.stream()
                 .anyMatch(e -> e.getId().equals(copiedDashboardId))).isEqualTo(true);
  }

  @Test
  public void copyCollectionWithANestedReport() {
    //given
    String collectionId = collectionClient.createNewCollection();
    String originalReportId = createNewSingleProcessReportInCollection(collectionId);
    String combinedReportId = reportClient.createEmptyCombinedReport(collectionId);
    String dashboardId = dashboardClient.createEmptyDashboard(collectionId);

    DashboardDefinitionDto dashboardDefinition = dashboardClient.getDashboard(dashboardId);
    CombinedReportDefinitionDto combinedReportDefinition = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportRequest(combinedReportId)
      .execute(CombinedReportDefinitionDto.class, Response.Status.OK.getStatusCode());

    combinedReportDefinition.getData()
      .setReports(Collections.singletonList(new CombinedReportItemDto(originalReportId)));

    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportDefinition)
      .execute();

    ArrayList<ReportLocationDto> reportLocationDtos = new ArrayList<>();
    reportLocationDtos.add(new ReportLocationDto(
      combinedReportId,
      new PositionDto(),
      new DimensionDto(),
      null
    ));
    reportLocationDtos.add(new ReportLocationDto(
      originalReportId,
      new PositionDto(),
      new DimensionDto(),
      null
    ));

    dashboardDefinition.setReports(reportLocationDtos);

    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboardDefinition)
      .execute();

    IdDto copiedCollectionId = collectionClient.copyCollection(collectionId);
    List<EntityDto> copiedCollectionEntities = collectionClient.getEntitiesForCollection(copiedCollectionId.getId());
    SingleProcessReportDefinitionDto originalSingleReportDefinition = reportClient.getSingleProcessReportDefinitionDto(
      originalReportId);

    assertThat(copiedCollectionEntities.size()).isEqualTo(3);

    List<String> copiedCollectionEntityNames = copiedCollectionEntities.stream()
      .map(EntityDto::getName)
      .collect(Collectors.toList());

    assertThat(copiedCollectionEntityNames)
      .containsExactlyInAnyOrder(
        dashboardDefinition.getName(),
        combinedReportDefinition.getName(),
        originalSingleReportDefinition.getName()
      );
  }

  @Test
  public void copyCollectionWithNewName() {
    String collectionId = collectionClient.createNewCollection();

    IdDto copyWithoutNewNameId = collectionClient.copyCollection(collectionId);
    CollectionDefinitionRestDto copiedCollectionWithoutNewName = collectionClient.getCollectionById(copyWithoutNewNameId
                                                                                                      .getId());

    IdDto copyWithNewNameId = collectionClient.copyCollection(collectionId, "newCoolName");
    CollectionDefinitionRestDto copiedCollectionWithNewName =
      collectionClient.getCollectionById(copyWithNewNameId.getId());

    CollectionDefinitionRestDto originalCollection = collectionClient.getCollectionById(collectionId);

    assertThat(copiedCollectionWithNewName.getName()).isEqualTo("newCoolName");
    assertThat(copiedCollectionWithoutNewName.getName().contains(originalCollection.getName())).isEqualTo(true);
    assertThat(copiedCollectionWithoutNewName.getName().toLowerCase().contains("copy")).isEqualTo(true);
  }

  @ParameterizedTest(name = "Copy collection and all alerts within the collection for report definition type {0}")
  @MethodSource("definitionTypes")
  public void copyCollectionAndAllContainingAlerts(final DefinitionType definitionType) {
    // given
    String originalId = collectionClient.createNewCollectionWithDefaultScope(definitionType);

    List<String> reportsToCopy = new ArrayList<>();
    reportsToCopy.add(reportClient.createReportForCollectionAsUser(
      originalId,
      definitionType,
      DEFAULT_DEFINITION_KEY,
      DEFAULT_TENANTS
    ));
    reportsToCopy.add(reportClient.createReportForCollectionAsUser(
      originalId,
      definitionType,
      DEFAULT_DEFINITION_KEY,
      DEFAULT_TENANTS
    ));

    List<String> alertsToCopy = new ArrayList<>();
    reportsToCopy
      .forEach(reportId -> alertsToCopy.add(alertClient.createAlertForReport(reportId)));

    // when
    CollectionDefinitionRestDto copy = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCopyCollectionRequest(originalId)
      .execute(CollectionDefinitionRestDto.class, Response.Status.OK.getStatusCode());

    // then
    List<AuthorizedReportDefinitionDto> copiedReports = collectionClient.getReportsForCollection(copy.getId());
    List<AlertDefinitionDto> copiedAlerts = collectionClient.getAlertsForCollection(copy.getId());
    Set<String> copiedReportIdsWithAlert = copiedAlerts.stream().map(AlertCreationDto::getReportId).collect(toSet());

    assertThat(copiedReports.size()).isEqualTo(reportsToCopy.size());
    assertThat(copiedAlerts.size()).isEqualTo(alertsToCopy.size());
    assertThat(copiedReportIdsWithAlert.size()).isEqualTo(copiedReports.size());
    assertThat(copiedReports.stream()
                 .allMatch(report -> copiedReportIdsWithAlert.contains(report.getDefinitionDto().getId()))).isTrue();
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void deleteSingleScopeOverrulesConflictsOnForceSet(DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry =
      new CollectionScopeEntryDto(definitionType, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);
    final String reportId = reportClient.createSingleReport(
      collectionId,
      definitionType,
      DEFAULT_DEFINITION_KEY,
      DEFAULT_TENANTS
    );
    alertClient.createAlertForReport(reportId);
    final String dashboardId = dashboardClient.createDashboard(collectionId, singletonList(reportId));

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeEntry.getId(), true)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // then
    assertThat(dashboardClient.getDashboard(dashboardId).getReports()).isEmpty();
    assertThat(collectionClient.getReportsForCollection(collectionId)).isEmpty();
    assertThat(collectionClient.getAlertsForCollection(collectionId)).isEmpty();
    assertThat(collectionClient.getCollectionScope(collectionId)).isEmpty();
  }

  @Test
  public void deleteSingleScopeOverrulesCombinedReportConflictsOnForceSet() {
    // given
    String collectionId = collectionClient.createNewCollection();
    final CollectionScopeEntryDto scopeEntry =
      new CollectionScopeEntryDto(PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);
    final String singleReportId =
      reportClient.createSingleReport(collectionId, PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    final String combinedReportId = reportClient.createCombinedReport(collectionId, singletonList(singleReportId));

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteScopeEntryFromCollectionRequest(collectionId, scopeEntry.getId(), true)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // then
    assertThat(collectionClient.getReportsForCollection(collectionId))
      .hasSize(1)
      .extracting(AuthorizedReportDefinitionDto::getDefinitionDto)
      .extracting(r -> (CombinedReportDefinitionDto) r)
      .hasOnlyOneElementSatisfying(r -> assertThat(r.getId()).isEqualTo(combinedReportId))
      .extracting(CombinedReportDefinitionDto::getData)
      .flatExtracting(CombinedReportDataDto::getReportIds)
      .isEmpty();
    assertThat(collectionClient.getCollectionScope(collectionId)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void updateSingleScope_oneTenantRemoved(DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollection();
    addTenantToElasticsearch("tenantToBeRemovedFromScope");
    final ArrayList<String> tenants = new ArrayList<>(DEFAULT_TENANTS);
    tenants.add("tenantToBeRemovedFromScope");
    final CollectionScopeEntryDto scopeEntry =
      new CollectionScopeEntryDto(definitionType, DEFAULT_DEFINITION_KEY, tenants);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);
    reportClient.createSingleReport(collectionId, definitionType, DEFAULT_DEFINITION_KEY, tenants);

    // when
    tenants.remove("tenantToBeRemovedFromScope");
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(
        collectionId,
        scopeEntry.getId(),
        new CollectionScopeEntryUpdateDto(tenants),
        true
      )
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // then
    assertThat(collectionClient.getReportsForCollection(collectionId))
      .hasSize(1)
      .extracting(AuthorizedReportDefinitionDto::getDefinitionDto)
      .extracting(r -> (SingleReportDefinitionDto<?>) r)
      .extracting(SingleReportDefinitionDto::getData)
      .flatExtracting(SingleReportDataDto::getTenantIds)
      .containsExactlyElementsOf(tenants);
  }

  @ParameterizedTest
  @EnumSource(DefinitionType.class)
  public void updateSingleScope_reportsWithoutTenantsAreNotBeingRemoved(DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollection();
    addTenantToElasticsearch("tenantToBeRemovedFromScope");
    final ArrayList<String> tenants = new ArrayList<>(DEFAULT_TENANTS);
    tenants.add("tenantToBeRemovedFromScope");
    final CollectionScopeEntryDto scopeEntry =
      new CollectionScopeEntryDto(definitionType, DEFAULT_DEFINITION_KEY, tenants);
    collectionClient.addScopeEntryToCollection(collectionId, scopeEntry);
    final String singleReportId = reportClient.createSingleReport(
      collectionId,
      definitionType,
      DEFAULT_DEFINITION_KEY,
      singletonList("tenantToBeRemovedFromScope")
    );
    final String alertId = alertClient.createAlertForReport(singleReportId);
    final String dashboardId = dashboardClient.createDashboard(collectionId, singletonList(singleReportId));

    // when
    tenants.remove("tenantToBeRemovedFromScope");
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCollectionScopeEntryRequest(
        collectionId,
        scopeEntry.getId(),
        new CollectionScopeEntryUpdateDto(tenants),
        true
      )
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // then
    assertThat(dashboardClient.getDashboard(dashboardId).getReports())
      .extracting(ReportLocationDto::getId)
      .contains(singleReportId);
    assertThat(collectionClient.getReportsForCollection(collectionId))
      .extracting(AuthorizedReportDefinitionDto::getDefinitionDto)
      .extracting(ReportDefinitionDto::getId)
      .contains(singleReportId);
    assertThat(collectionClient.getAlertsForCollection(collectionId))
      .extracting(AlertDefinitionDto::getId)
      .contains(alertId);
  }

  private void addTenantToElasticsearch(final String tenantId) {
    TenantDto tenantDto = new TenantDto(tenantId, "ATenantName", DEFAULT_ENGINE_ALIAS);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, tenantId, tenantDto);
  }

  private void assertReportIsDeleted(final String singleReportIdToDelete) {
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetReportRequest(singleReportIdToDelete)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  private void assertDashboardIsDeleted(final String dashboardIdToDelete) {
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDashboardRequest(dashboardIdToDelete)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  private String createNewSingleProcessReportInCollection(final String collectionId) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleDecisionReportInCollection(final String collectionId) {
    SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
    singleDecisionReportDefinitionDto.setCollectionId(collectionId);
    return reportClient.createSingleDecisionReport(singleDecisionReportDefinitionDto);
  }
}

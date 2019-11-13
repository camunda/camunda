/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.es.writer.CollectionWriter.DEFAULT_COLLECTION_NAME;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReport;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_INDEX_NAME;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class CollectionHandlingIT extends AbstractIT {

  private static Stream<Integer> definitionTypes() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  @Test
  public void collectionIsWrittenToElasticsearch() throws IOException {
    // given
    String id = createNewCollection();

    // then
    GetRequest getRequest = new GetRequest(COLLECTION_INDEX_NAME, COLLECTION_INDEX_NAME, id);
    GetResponse getResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .get(getRequest, RequestOptions.DEFAULT);

    // then
    assertThat(getResponse.isExists(), is(true));
  }

  @Test
  public void newCollectionIsCorrectlyInitialized() {
    // given
    String id = createNewCollection();

    // when
    ResolvedCollectionDefinitionDto collection = getCollectionById(id);

    // then
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
    assertThat(roleDto.getRole(), is(RoleType.MANAGER));
  }

  @Test
  public void getResolvedCollection() {
    //given
    final String collectionId = createNewCollection();
    final String dashboardId = createNewDashboardInCollection(collectionId);
    final String reportId = createNewSingleProcessReportInCollection(collectionId);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ResolvedCollectionDefinitionDto collection = getCollectionById(collectionId);

    // then
    assertThat(collection, is(notNullValue()));
    assertThat(collection.getId(), is(collectionId));
    assertThat(collection.getData().getEntities().size(), is(2));
    assertThat(
      collection.getData().getEntities().stream().map(EntityDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(dashboardId, reportId)
    );
  }

  @Test
  public void getResolvedCollectionContainsCombinedReportSubEntityCounts() {
    //given
    final String collectionId = createNewCollection();
    final String reportId1 = createNewSingleProcessReportInCollection(collectionId);
    final String reportId2 = createNewSingleProcessReportInCollection(collectionId);
    final String combinedReportId = createNewCombinedReportInCollection(collectionId);


    final CombinedReportDefinitionDto combinedReportUpdate = new CombinedReportDefinitionDto();
    combinedReportUpdate.setData(createCombinedReport(reportId1, reportId2));
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportUpdate)
      .execute();

    // when
    ResolvedCollectionDefinitionDto collection = getCollectionById(collectionId);

    // then
    assertThat(collection, is(notNullValue()));
    assertThat(collection.getId(), is(collectionId));
    assertThat(collection.getData().getEntities().size(), is(3));
    final EntityDto combinedReportEntityDto = collection.getData().getEntities().stream()
      .filter(EntityDto::getCombined)
      .findFirst()
      .get();
    assertThat(combinedReportEntityDto.getData().getSubEntityCounts().size(), is(1));
    assertThat(combinedReportEntityDto.getData().getSubEntityCounts().get(EntityType.REPORT), is(2L));
  }

  @Test
  public void updateCollection() {
    // given
    String id = createNewCollection();
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    PartialCollectionDefinitionDto collectionUpdate = new PartialCollectionDefinitionDto();
    collectionUpdate.setName("MyCollection");
    final Map<String, String> configuration = Collections.singletonMap("Foo", "Bar");
    final PartialCollectionDataDto data = new PartialCollectionDataDto();
    data.setConfiguration(configuration);
    collectionUpdate.setData(data);


    // when
    updateCollectionRequest(id, collectionUpdate);
    ResolvedCollectionDefinitionDto collection = getCollectionById(id);

    // then
    assertThat(collection.getId(), is(id));
    assertThat(collection.getName(), is("MyCollection"));
    assertThat(collection.getLastModifier(), is("demo"));
    assertThat(collection.getLastModified(), is(now));
    final ResolvedCollectionDataDto resultCollectionData = collection.getData();
    assertThat(resultCollectionData.getConfiguration(), equalTo(configuration));
    assertThat(resultCollectionData.getEntities().size(), is(0));
  }

  @Test
  public void updatePartialCollection() {
    // given
    String id = createNewCollection();
    OffsetDateTime now = OffsetDateTime.parse("2019-04-23T18:00:00+01:00");
    LocalDateUtil.setCurrentTime(now);

    // when (update only name)
    PartialCollectionDefinitionDto collectionUpdate = new PartialCollectionDefinitionDto();
    collectionUpdate.setName("MyCollection");

    updateCollectionRequest(id, collectionUpdate);
    ResolvedCollectionDefinitionDto collection = getCollectionById(id);

    // then
    assertThat(collection.getId(), is(id));
    assertThat(collection.getName(), is("MyCollection"));
    assertThat(collection.getLastModifier(), is("demo"));
    assertThat(collection.getLastModified(), is(now));

    // when (update only configuration)
    collectionUpdate = new PartialCollectionDefinitionDto();
    final Map<String, String> configuration = Collections.singletonMap("Foo", "Bar");
    PartialCollectionDataDto data = new PartialCollectionDataDto();
    data.setConfiguration(configuration);
    collectionUpdate.setData(data);

    updateCollectionRequest(id, collectionUpdate);
    collection = getCollectionById(id);

    // then
    assertThat(collection.getId(), is(id));
    assertThat(collection.getName(), is("MyCollection"));
    assertThat(collection.getLastModifier(), is("demo"));
    assertThat(collection.getLastModified(), is(now));
    ResolvedCollectionDataDto resultCollectionData = collection.getData();
    assertThat(resultCollectionData.getConfiguration(), is(configuration));


    // when (again only update name)
    collectionUpdate = new PartialCollectionDefinitionDto();
    collectionUpdate.setName("TestNewCollection");

    updateCollectionRequest(id, collectionUpdate);
    collection = getCollectionById(id);

    // then
    assertThat(collection.getId(), is(id));
    assertThat(collection.getName(), is("TestNewCollection"));
    assertThat(collection.getLastModifier(), is("demo"));
    assertThat(collection.getLastModified(), is(now));
    resultCollectionData = collection.getData();
    assertThat(resultCollectionData.getConfiguration(), is(configuration));
  }

  @Test
  public void singleProcessReportCanBeCreatedInsideCollection() {
    // given
    String collectionId = createNewCollection();
    String reportId = createNewSingleProcessReportInCollection(collectionId);

    // when
    ResolvedCollectionDefinitionDto collection = getCollectionById(collectionId);

    // then
    EntityDto report = collection.getData().getEntities().get(0);
    assertThat(report.getId(), is(reportId));
    assertThat(report.getEntityType(), is(EntityType.REPORT));
    assertThat(report.getReportType(), is(ReportType.PROCESS));
    assertThat(report.getCombined(), is(false));
  }

  @Test
  public void singleDecisionReportCanBeCreatedInsideCollection() {
    // given
    String collectionId = createNewCollection();
    String reportId = createNewSingleDecisionReportInCollection(collectionId);

    // when
    ResolvedCollectionDefinitionDto collection = getCollectionById(collectionId);

    // then
    EntityDto report = collection.getData().getEntities().get(0);
    assertThat(report.getId(), is(reportId));
    assertThat(report.getEntityType(), is(EntityType.REPORT));
    assertThat(report.getReportType(), is(ReportType.DECISION));
    assertThat(report.getCombined(), is(false));
  }

  @Test
  public void combinedProcessReportCanBeCreatedInsideCollection() {
    // given
    String collectionId = createNewCollection();
    String reportId = createNewCombinedReportInCollection(collectionId);

    // when
    ResolvedCollectionDefinitionDto collection = getCollectionById(collectionId);

    // then
    EntityDto report = collection.getData().getEntities().get(0);
    assertThat(report.getId(), is(reportId));
    assertThat(report.getEntityType(), is(EntityType.REPORT));
    assertThat(report.getReportType(), is(ReportType.PROCESS));
    assertThat(report.getCombined(), is(true));
  }

  @Test
  public void dashboardCanBeCreatedInsideCollection() {
    // given
    String collectionId = createNewCollection();
    String dashboardId = createNewDashboardInCollection(collectionId);

    // when
    ResolvedCollectionDefinitionDto collection = getCollectionById(collectionId);

    // then
    EntityDto dashboard = collection.getData().getEntities().get(0);
    assertThat(dashboard.getId(), is(dashboardId));
    assertThat(dashboard.getEntityType(), is(EntityType.DASHBOARD));
    assertThat(dashboard.getReportType(), is(nullValue()));
    assertThat(dashboard.getCombined(), is(nullValue()));
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
    assertThat(createResponse.getStatus(), is(HttpServletResponse.SC_NOT_FOUND));
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
    assertThat(createResponse.getStatus(), is(HttpServletResponse.SC_NOT_FOUND));
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
    assertThat(createResponse.getStatus(), is(HttpServletResponse.SC_NOT_FOUND));
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
    assertThat(createResponse.getStatus(), is(HttpServletResponse.SC_NOT_FOUND));
  }

  @Test
  public void collectionItemsAreOrderedByTypeAndModificationDateDescending() {
    // given
    String collectionId = createNewCollection();
    String reportId1 = createNewSingleProcessReportInCollection(collectionId);
    String reportId2 = createNewSingleProcessReportInCollection(collectionId);
    String dashboardId1 = createNewDashboardInCollection(collectionId);
    String dashboardId2 = createNewDashboardInCollection(collectionId);

    updateReport(reportId1, new SingleProcessReportDefinitionDto());

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ResolvedCollectionDefinitionDto collection = getCollectionById(collectionId);

    // then
    assertThat(collection.getData().getEntities().get(0).getId(), is(dashboardId2));
    assertThat(collection.getData().getEntities().get(1).getId(), is(dashboardId1));
    assertThat(collection.getData().getEntities().get(2).getId(), is(reportId1));
    assertThat(collection.getData().getEntities().get(3).getId(), is(reportId2));
  }

  @Test
  public void doNotUpdateNullFieldsInCollection() {
    // given
    String id = createNewCollection();
    PartialCollectionDefinitionDto collection = new PartialCollectionDefinitionDto();

    // when
    updateCollectionRequest(id, collection);
    ResolvedCollectionDefinitionDto storedCollection = getCollectionById(id);

    // then
    assertThat(storedCollection.getId(), is(id));
    assertThat(storedCollection.getCreated(), is(notNullValue()));
    assertThat(storedCollection.getLastModified(), is(notNullValue()));
    assertThat(storedCollection.getLastModifier(), is(notNullValue()));
    assertThat(storedCollection.getName(), is(notNullValue()));
    assertThat(storedCollection.getOwner(), is(notNullValue()));
  }

  @Test
  public void deletedReportsAreRemovedFromCollectionWhenForced() {
    // given
    String collectionId = createNewCollection();
    String singleReportIdToDelete = createNewSingleProcessReportInCollection(collectionId);
    String combinedReportIdToDelete = createNewCombinedReportInCollection(collectionId);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    deleteReport(singleReportIdToDelete);
    deleteReport(combinedReportIdToDelete);

    // then
    ResolvedCollectionDefinitionDto collection = getCollectionById(collectionId);
    assertThat(collection.getData().getEntities().size(), is(0));
  }

  @Test
  public void deletedDashboardsAreRemovedFromCollectionWhenForced() {
    // given
    String collectionId = createNewCollection();
    String dashboardIdToDelete = createNewDashboardInCollection(collectionId);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    deleteDashboard(dashboardIdToDelete);

    // then
    ResolvedCollectionDefinitionDto collection = getCollectionById(collectionId);
    assertThat(collection.getData().getEntities().size(), is(0));
  }

  @Test
  public void entitiesAreDeletedOnCollectionDelete() {
    // given
    String collectionId = createNewCollection();
    String singleReportId = createNewSingleProcessReportInCollection(collectionId);
    String combinedReportId = createNewCombinedReportInCollection(collectionId);
    String dashboardId = createNewDashboardInCollection(collectionId);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    deleteCollection(collectionId);

    // then
    final Response getCollectionByIdResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute();
    assertThat(getCollectionByIdResponse.getStatus(), is(404));

    assertDashboardIsDeleted(dashboardId);
    assertReportIsDeleted(singleReportId);
    assertReportIsDeleted(combinedReportId);
  }

  @Test
  public void copyAnEmptyCollectionWithCustomPermissionsAndScope() {
    //given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.grantUserOptimizeAccess("kermit");
    String collectionId = createNewCollection();
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, new CollectionRoleDto(new UserDto("kermit"), RoleType.EDITOR))
      .execute();

    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildAddScopeEntryToCollectionRequest(collectionId, new CollectionScopeEntryDto("PROCESS:invoice"))
      .execute();

    //when
    IdDto copyId = copyCollection(collectionId);

    ResolvedCollectionDefinitionDto copyDefinition = getResolvedCollectionDefinitionDto(copyId);

    //then
    assertThat(copyDefinition.getName().toLowerCase().contains("copy"), is(true));
    assertThat(copyDefinition.getData()
                 .getRoles()
                 .contains(new CollectionRoleDto(new UserDto("kermit"), RoleType.EDITOR)), is(true));
    assertThat(copyDefinition.getData().getScope().contains(new CollectionScopeEntryDto("PROCESS:invoice")), is(true));
  }

  @Test
  public void copyCollectionWithASingleReport() {
    //given
    String collectionId = createNewCollection();
    String originalReportId = createNewSingleProcessReportInCollection(collectionId);

    // when
    IdDto copyId = copyCollection(collectionId);

    ResolvedCollectionDefinitionDto copyDefinition = getResolvedCollectionDefinitionDto(copyId);

    String reportCopyId = copyDefinition.getData().getEntities().get(0).getId();

    SingleProcessReportDefinitionDto originalReport = getSingleProcessReportDefinitionDto(originalReportId);

    SingleProcessReportDefinitionDto copiedReport = getSingleProcessReportDefinitionDto(reportCopyId);

    //then
    assertThat(originalReport.getData(), is(copiedReport.getData()));
    assertThat(copiedReport.getName(), is(originalReport.getName()));
  }

  @Test
  public void copyCollectionWithADashboard() {
    //given
    String collectionId = createNewCollection();
    String originalReportId = createNewSingleProcessReportInCollection(collectionId);
    String dashboardId = createNewDashboardInCollection(collectionId);

    DashboardDefinitionDto dashboardDefinition = getDashboardDefinitionDto(dashboardId);

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
    IdDto collectionCopyId = copyCollection(collectionId);

    ResolvedCollectionDefinitionDto copiedCollectionDefinition = getResolvedCollectionDefinitionDto(collectionCopyId);

    String copiedDashboardId = copiedCollectionDefinition.getData()
      .getEntities()
      .stream()
      .filter(e -> e.getEntityType().equals(EntityType.DASHBOARD)).findFirst().get().getId();

    String copiedReportId = copiedCollectionDefinition.getData()
      .getEntities()
      .stream()
      .filter(e -> e.getEntityType().equals(EntityType.REPORT)).findFirst().get().getId();

    DashboardDefinitionDto copiedDashboard = getDashboardDefinitionDto(copiedDashboardId);

    SingleProcessReportDefinitionDto copiedReportDefinition = getSingleProcessReportDefinitionDto(copiedReportId);
    SingleProcessReportDefinitionDto originalReportDefinition = getSingleProcessReportDefinitionDto(originalReportId);
    //then
    //the dashboard references the same report entity as the report itself
    assertThat(copiedDashboard.getReports().get(0).getId(), is(copiedReportId));

    assertThat(copiedDashboard.getName(), is(dashboardDefinition.getName()));
    assertThat(copiedReportDefinition.getName(), is(originalReportDefinition.getName()));

    assertThat(copiedCollectionDefinition.getData().getEntities().size(), is(2));
    assertThat(copiedCollectionDefinition.getData()
                 .getEntities()
                 .stream()
                 .anyMatch(e -> e.getId().equals(copiedReportId)), is(true));
    assertThat(copiedCollectionDefinition.getData()
                 .getEntities()
                 .stream()
                 .anyMatch(e -> e.getId().equals(copiedDashboardId)), is(true));
  }

  @Test
  public void copyCollectionWithANestedReport() {
    //given
    // we are adding the default user here to the cache, since otherwise the
    // test becomes flaky due to time outs from fetching the user
    UserDto expectedUserDtoWithData =
      new UserDto(DEFAULT_USERNAME, DEFAULT_FIRSTNAME, DEFAULT_LASTNAME, "me@camunda.com");
    embeddedOptimizeExtension.getIdentityService().addIdentity(expectedUserDtoWithData);

    String collectionId = createNewCollection();
    String originalReportId = createNewSingleProcessReportInCollection(collectionId);
    String combinedReportId = createNewCombinedReportInCollection(collectionId);
    String dashboardId = createNewDashboardInCollection(collectionId);

    DashboardDefinitionDto dashboardDefinition = getDashboardDefinitionDto(dashboardId);
    CombinedReportDefinitionDto combinedReportDefinition = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportRequest(combinedReportId)
      .execute(CombinedReportDefinitionDto.class, 200);

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

    IdDto copiedCollectionId = copyCollection(collectionId);
    ResolvedCollectionDefinitionDto copiedCollectionDefinition = getResolvedCollectionDefinitionDto(copiedCollectionId);
    SingleProcessReportDefinitionDto originalSingleReportDefinition = getSingleProcessReportDefinitionDto(
      originalReportId);

    assertThat(copiedCollectionDefinition.getData().getEntities().size(), is(3));

    List<String> copiedCollectionEntityNames = copiedCollectionDefinition.getData()
      .getEntities()
      .stream()
      .map(EntityDto::getName)
      .collect(Collectors.toList());

    assertThat(
      copiedCollectionEntityNames,
      containsInAnyOrder(
        dashboardDefinition.getName(), combinedReportDefinition.getName(),
        originalSingleReportDefinition.getName()
      )
    );
  }

  @Test
  public void copyCollectionWithNewName() {
    String collectionId = createNewCollection();

    IdDto copyWithoutNewNameId = copyCollection(collectionId);
    ResolvedCollectionDefinitionDto copiedCollectionWithoutNewName = getCollectionById(copyWithoutNewNameId.getId());

    IdDto copyWithNewNameId = copyCollection(collectionId, "newCoolName");
    ResolvedCollectionDefinitionDto copiedCollectionWithNewName = getCollectionById(copyWithNewNameId.getId());

    ResolvedCollectionDefinitionDto originalCollection = getCollectionById(collectionId);

    assertThat(copiedCollectionWithNewName.getName(), is("newCoolName"));
    assertThat(copiedCollectionWithoutNewName.getName().contains(originalCollection.getName()), is(true));
    assertThat(copiedCollectionWithoutNewName.getName().toLowerCase().contains("copy"), is(true));
  }

  @ParameterizedTest(name = "Copy collection and all alerts within the collection for report definition type {0}")
  @MethodSource("definitionTypes")
  public void copyCollectionAndAllContainingAlerts(final int definitionType) {
    // given
    IdDto originalId = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200);

    List<String> reportsToCopy = new ArrayList<>();
    reportsToCopy.add(createReportForCollection(originalId.getId(), definitionType));
    reportsToCopy.add(createReportForCollection(originalId.getId(), definitionType));

    List<String> alertsToCopy = new ArrayList<>();
    reportsToCopy.stream()
      .forEach(reportId -> alertsToCopy.add(createAlertForReport(reportId)));

    // when
    ResolvedCollectionDefinitionDto copy = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCopyCollectionRequest(originalId.getId())
      .execute(ResolvedCollectionDefinitionDto.class, 200);

    // then
    List<AuthorizedReportDefinitionDto> copiedReports = getReportsForCollection(copy.getId());
    List<AlertDefinitionDto> copiedAlerts = getAlertsForCollection(copy.getId());
    Set<String> copiedReportIdsWithAlert = copiedAlerts.stream().map(alert -> alert.getReportId()).collect(toSet());

    assertThat(copiedReports.size(), is(reportsToCopy.size()));
    assertThat(copiedAlerts.size(), is(alertsToCopy.size()));
    assertThat(
      copiedReportIdsWithAlert.size(),
      is(copiedReports.size())
    );
    assertThat(
      copiedReports.stream().allMatch(report -> copiedReportIdsWithAlert.contains(report.getDefinitionDto().getId())),
      is(true)
    );
  }

  private DashboardDefinitionDto getDashboardDefinitionDto(String dashboardId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDashboardRequest(dashboardId)
      .execute(DashboardDefinitionDto.class, 200);
  }

  private ResolvedCollectionDefinitionDto getResolvedCollectionDefinitionDto(IdDto copyId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(copyId.getId())
      .execute(ResolvedCollectionDefinitionDto.class, 200);
  }

  private SingleProcessReportDefinitionDto getSingleProcessReportDefinitionDto(String originalReportId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportRequest(originalReportId)
      .execute(SingleProcessReportDefinitionDto.class, 200);
  }

  private IdDto copyCollection(String collectionId) {
    return copyCollection(collectionId, null);
  }

  private IdDto copyCollection(String collectionId, String newName) {
    OptimizeRequestExecutor executor = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCopyCollectionRequest(collectionId);

    if (newName != null) {
      executor.addSingleQueryParam("name", newName);
    }

    return executor
      .execute(IdDto.class, 200);
  }

  private void assertReportIsDeleted(final String singleReportIdToDelete) {
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetReportRequest(singleReportIdToDelete)
      .execute();
    assertThat(response.getStatus(), is(404));
  }

  private void assertDashboardIsDeleted(final String dashboardIdToDelete) {
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDashboardRequest(dashboardIdToDelete)
      .execute();
    assertThat(response.getStatus(), is(404));
  }

  private String createNewSingleProcessReportInCollection(final String collectionId) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewSingleDecisionReportInCollection(final String collectionId) {
    SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
    singleDecisionReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewDashboardInCollection(final String collectionId) {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewCombinedReportInCollection(final String collectionId) {
    CombinedReportDefinitionDto combinedReportDefinitionDto = new CombinedReportDefinitionDto();
    combinedReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private Response deleteCollection(String id) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildDeleteCollectionRequest(id, true)
      .execute();
  }

  private void deleteReport(String reportId) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, true)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private void deleteDashboard(String dashboardId) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteDashboardRequest(dashboardId, true)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private String createNewCollection() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateCollectionRequest(String id, PartialCollectionDefinitionDto renameCollection) {
    Response response = embeddedOptimizeExtension
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
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();
  }

  private ResolvedCollectionDefinitionDto getCollectionById(final String collectionId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(ResolvedCollectionDefinitionDto.class, 200);
  }

  private String createAlertForReport(final String reportId) {
    AlertCreationDto alertCreationDto = new AlertCreationDto();

    AlertInterval interval = new AlertInterval();
    interval.setUnit("Seconds");
    interval.setValue(1);
    alertCreationDto.setCheckInterval(interval);
    alertCreationDto.setThreshold(0);
    alertCreationDto.setThresholdOperator(">");
    alertCreationDto.setEmail("test@camunda.com");
    alertCreationDto.setName("test alert");
    alertCreationDto.setReportId(reportId);

    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(alertCreationDto)
      .execute(String.class, 200);
  }

  private List<AuthorizedReportDefinitionDto> getReportsForCollection(final String collectionId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportsForCollectionRequest(collectionId)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .executeAndReturnList(
        AuthorizedReportDefinitionDto.class,
        200
      );
  }

  private List<AlertDefinitionDto> getAlertsForCollection(final String collectionId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAlertsForCollectionRequest(collectionId)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .executeAndReturnList(
        AlertDefinitionDto.class,
        200
      );
  }

  private String createReportForCollection(final String collectionId, final int resourceType) {
    switch (resourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        SingleProcessReportDefinitionDto procReport = getProcessReportDefinitionDto(collectionId);
        return createNewProcessReportAsUser(procReport);

      case RESOURCE_TYPE_DECISION_DEFINITION:
        SingleDecisionReportDefinitionDto decReport = getDecisionReportDefinitionDto(collectionId);
        return createNewDecisionReportAsUser(decReport);

      default:
        throw new OptimizeRuntimeException("Unknown resource type provided.");
    }
  }

  private SingleProcessReportDefinitionDto getProcessReportDefinitionDto(final String collectionId) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey("someKey")
      .setProcessDefinitionVersion("someVersion")
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setName("aProcessReport");
    report.setCollectionId(collectionId);
    return report;
  }

  private SingleDecisionReportDefinitionDto getDecisionReportDefinitionDto(final String collectionId) {
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("someKey")
      .setDecisionDefinitionVersion("someVersion")
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();

    SingleDecisionReportDefinitionDto report = new SingleDecisionReportDefinitionDto();
    report.setData(reportData);
    report.setName("aDecisionReport");
    report.setCollectionId(collectionId);
    return report;
  }

  private String createNewDecisionReportAsUser(final SingleDecisionReportDefinitionDto decReport) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .buildCreateSingleDecisionReportRequest(decReport)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewProcessReportAsUser(final SingleProcessReportDefinitionDto procReport) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .buildCreateSingleProcessReportRequest(procReport)
      .execute(IdDto.class, 200)
      .getId();
  }
}

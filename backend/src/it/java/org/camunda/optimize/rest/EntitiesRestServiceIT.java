/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Lists;
import org.assertj.core.api.Assertions;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.camunda.optimize.rest.RestTestUtil.getOffsetDiffInHours;
import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;
import static org.camunda.optimize.service.es.writer.CollectionWriter.DEFAULT_COLLECTION_NAME;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class EntitiesRestServiceIT extends AbstractIT {

  @Test
  public void getEntities_WithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetAllPrivateReportsRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

  @Test
  public void getEntities_ReturnsMyUsersReports() {
    //given
    addSingleReportToOptimize("B Report", ReportType.PROCESS);
    addSingleReportToOptimize("A Report", ReportType.DECISION);
    addCombinedReport("D Combined");

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> privateEntities = entitiesClient.getAllEntities();

    // then
    assertThat(privateEntities.size(), is(3));
    assertThat(
      privateEntities.stream().map(EntityDto::getReportType).collect(Collectors.toList()),
      containsInAnyOrder(ReportType.PROCESS, ReportType.PROCESS, ReportType.DECISION)
    );
    assertThat(
      privateEntities.stream().map(EntityDto::getCombined).collect(Collectors.toList()),
      containsInAnyOrder(false, false, true)
    );
  }

  @Test
  public void getEntities_adoptTimezoneFromHeader() {
    //given
    OffsetDateTime now = OffsetDateTime.now(TimeZone.getTimeZone("Europe/Berlin").toZoneId());
    LocalDateUtil.setCurrentTime(now);

    addSingleReportToOptimize("My Report", ReportType.PROCESS);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> privateEntities = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAllEntitiesRequest()
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      .executeAndReturnList(EntityDto.class, Response.Status.OK.getStatusCode());

    // then
    Assertions.assertThat(privateEntities)
      .isNotNull()
      .hasSize(1);
    EntityDto entityDto = privateEntities.get(0);
    Assertions.assertThat(entityDto.getCreated()).isEqualTo(now);
    Assertions.assertThat(entityDto.getLastModified()).isEqualTo(now);
    Assertions.assertThat(getOffsetDiffInHours(entityDto.getCreated(), now)).isEqualTo(1.);
    Assertions.assertThat(getOffsetDiffInHours(entityDto.getLastModified(), now)).isEqualTo(1.);
  }

  @Test
  public void getEntities_DoesNotReturnOtherUsersReports() {
    //given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.grantUserOptimizeAccess("kermit");
    addSingleReportToOptimize("B Report", ReportType.PROCESS, null, "kermit");
    addSingleReportToOptimize("A Report", ReportType.DECISION);
    addCombinedReport("D Combined");

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when (default user)
    final List<EntityDto> defaultUserEntities = entitiesClient.getAllEntities();

    // then
    assertThat(defaultUserEntities.size(), is(2));
    assertThat(
      defaultUserEntities.stream().map(EntityDto::getName).collect(Collectors.toList()),
      containsInAnyOrder("A Report", "D Combined")
    );

    // when
    final List<EntityDto> kermitUserEntities = entitiesClient.getAllEntitiesAsUser(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(kermitUserEntities.size(), is(1));
    assertThat(
      kermitUserEntities.stream().map(EntityDto::getName).collect(Collectors.toList()),
      containsInAnyOrder("B Report")
    );
  }

  @Test
  public void getEntities_emptyDefinitionKeyIsHandledAsEmptyReport() {
    // this is a regression test that could occur for old empty reports
    // see https://jira.camunda.com/browse/OPT-3496

    // given
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setName("empty");
    // an empty string definition key caused trouble
    singleProcessReportDefinitionDto.getData().setProcessDefinitionKey("");
    reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when (default user)
    final List<EntityDto> defaultUserEntities = entitiesClient.getAllEntities();

    // then
    assertThat(defaultUserEntities.size(), is(1));
    assertThat(
      defaultUserEntities.stream().map(EntityDto::getName).collect(Collectors.toList()),
      containsInAnyOrder(singleProcessReportDefinitionDto.getName())
    );
  }

  @Test
  public void getEntities_ReturnsMyUsersDashboards() {
    //given
    addDashboardToOptimize("A Dashboard");
    addDashboardToOptimize("B Dashboard");

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> privateEntities = entitiesClient.getAllEntities();

    // then
    assertThat(privateEntities.size(), is(2));
  }

  @Test
  public void getEntities_DoesNotReturnOtherUsersDashboards() {
    //given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.grantUserOptimizeAccess("kermit");
    addDashboardToOptimize("A Dashboard");
    addDashboardToOptimize("B Dashboard", null, "kermit");

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when (default user)
    final List<EntityDto> defaultUserEntities = entitiesClient.getAllEntities();

    // then
    assertThat(defaultUserEntities.size(), is(1));
    assertThat(
      defaultUserEntities.stream().map(EntityDto::getName).collect(Collectors.toList()),
      containsInAnyOrder("A Dashboard")
    );

    // when
    final List<EntityDto> kermitUserEntities = entitiesClient.getAllEntitiesAsUser(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(kermitUserEntities.size(), is(1));
    assertThat(
      kermitUserEntities.stream().map(EntityDto::getName).collect(Collectors.toList()),
      containsInAnyOrder("B Dashboard")
    );
  }

  @Test
  public void getEntities_ReturnsCollections() {
    //given
    collectionClient.createNewCollection();
    collectionClient.createNewCollection();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> privateEntities = entitiesClient.getAllEntities();

    // then
    assertThat(privateEntities.size(), is(2));
  }

  @Test
  public void getEntities_DoesNotReturnEntitiesInCollections() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    addSingleReportToOptimize("A Report", ReportType.DECISION);
    addSingleReportToOptimize("B Report", ReportType.PROCESS, collectionId, DEFAULT_USERNAME);
    addDashboardToOptimize("C Dashboard", collectionId, DEFAULT_USERNAME);
    addCombinedReport("D Combined");

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> defaultUserEntities = entitiesClient.getAllEntities();

    // then
    assertThat(defaultUserEntities.size(), is(3));
    assertThat(
      defaultUserEntities.stream().map(EntityDto::getName).collect(Collectors.toList()),
      containsInAnyOrder("A Report", "D Combined", DEFAULT_COLLECTION_NAME)
    );
  }

  @Test
  public void getEntities_OrderedByTypeAndLastModified() {
    //given
    addCollection("B Collection");
    addCollection("A Collection");
    addSingleReportToOptimize("D Report", ReportType.PROCESS);
    addSingleReportToOptimize("C Report", ReportType.DECISION);
    addDashboardToOptimize("B Dashboard");
    addDashboardToOptimize("A Dashboard");

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> entities = entitiesClient.getAllEntities();

    // then
    assertThat(entities.size(), is(6));
    assertThat(entities.get(0).getName(), is("A Collection"));
    assertThat(entities.get(0).getEntityType(), is(EntityType.COLLECTION));
    assertThat(entities.get(1).getName(), is("B Collection"));
    assertThat(entities.get(1).getEntityType(), is(EntityType.COLLECTION));
    assertThat(entities.get(2).getName(), is("A Dashboard"));
    assertThat(entities.get(2).getEntityType(), is(EntityType.DASHBOARD));
    assertThat(entities.get(3).getName(), is("B Dashboard"));
    assertThat(entities.get(3).getEntityType(), is(EntityType.DASHBOARD));
    assertThat(entities.get(4).getName(), is("C Report"));
    assertThat(entities.get(4).getEntityType(), is(EntityType.REPORT));
    assertThat(entities.get(5).getName(), is("D Report"));
    assertThat(entities.get(5).getEntityType(), is(EntityType.REPORT));
  }

  @Test
  public void getEntities_IncludesCollectionSubEntityCountsIfThereAreNoEntities() {
    // given
    collectionClient.createNewCollection();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> defaultUserEntities = entitiesClient.getAllEntities();

    // then
    assertThat(defaultUserEntities.size(), is(1));
    final EntityDto collectionEntityDto = defaultUserEntities.get(0);
    assertThat(collectionEntityDto.getData().getSubEntityCounts().size(), is(2));
    assertThat(collectionEntityDto.getData().getSubEntityCounts().get(EntityType.REPORT), is(0L));
    assertThat(collectionEntityDto.getData().getSubEntityCounts().get(EntityType.DASHBOARD), is(0L));
  }

  @Test
  public void getEntities_IncludesCollectionSubEntityCounts() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    addSingleReportToOptimize("A Report", ReportType.DECISION, collectionId, DEFAULT_USERNAME);
    addSingleReportToOptimize("B Report", ReportType.PROCESS, collectionId, DEFAULT_USERNAME);
    addDashboardToOptimize("C Dashboard", collectionId, DEFAULT_USERNAME);
    addCombinedReport("D Combined", collectionId);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> defaultUserEntities = entitiesClient.getAllEntities();

    // then
    assertThat(defaultUserEntities.size(), is(1));
    final EntityDto collectionEntityDto = defaultUserEntities.get(0);
    assertThat(collectionEntityDto.getData().getSubEntityCounts().size(), is(2));
    assertThat(collectionEntityDto.getData().getSubEntityCounts().get(EntityType.REPORT), is(3L));
    assertThat(collectionEntityDto.getData().getSubEntityCounts().get(EntityType.DASHBOARD), is(1L));
  }

  @Test
  public void getEntities_IncludesCollectionRoleCountsByDefault() {
    // given
    collectionClient.createNewCollection();

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> defaultUserEntities = entitiesClient.getAllEntities();

    // then
    assertThat(defaultUserEntities.size(), is(1));
    final EntityDto collectionEntityDto = defaultUserEntities.get(0);
    assertThat(collectionEntityDto.getData().getRoleCounts().size(), is(2));
    assertThat(collectionEntityDto.getData().getRoleCounts().get(IdentityType.USER), is(1L));
    assertThat(collectionEntityDto.getData().getRoleCounts().get(IdentityType.GROUP), is(0L));
  }

  @Test
  public void getEntities_IncludesCollectionRoleCounts() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    final String user1 = "user1";
    authorizationClient.addUserAndGrantOptimizeAccess(user1);
    addRoleToCollection(collectionId, user1, IdentityType.USER);
    final String groupA = "groupA";
    authorizationClient.createGroupAndGrantOptimizeAccess(groupA, groupA);
    addRoleToCollection(collectionId, groupA, IdentityType.GROUP);
    final String groupB = "groupB";
    authorizationClient.createGroupAndGrantOptimizeAccess(groupB, groupB);
    addRoleToCollection(collectionId, groupB, IdentityType.GROUP);
    final String groupC = "groupC";
    authorizationClient.createGroupAndGrantOptimizeAccess(groupC, groupC);
    addRoleToCollection(collectionId, groupC, IdentityType.GROUP);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> defaultUserEntities = entitiesClient.getAllEntities();

    // then
    assertThat(defaultUserEntities.size(), is(1));
    final EntityDto collectionEntityDto = defaultUserEntities.get(0);
    assertThat(collectionEntityDto.getData().getRoleCounts().size(), is(2));
    assertThat(collectionEntityDto.getData().getRoleCounts().get(IdentityType.USER), is(2L));
    assertThat(collectionEntityDto.getData().getRoleCounts().get(IdentityType.GROUP), is(3L));
  }

  @Test
  public void getEntities_IncludesPrivateCombinedReportSubEntityCounts() {
    // given
    final String reportId1 = addSingleReportToOptimize("A Report", ReportType.PROCESS);
    final String reportId2 = addSingleReportToOptimize("B Report", ReportType.PROCESS);
    final String combinedReportId = addCombinedReport("D Combined");

    final CombinedReportDefinitionDto combinedReportUpdate = new CombinedReportDefinitionDto();
    combinedReportUpdate.setData(createCombinedReportData(reportId1, reportId2));
    reportClient.updateCombinedReport(combinedReportId, Lists.newArrayList(reportId1, reportId2));

    // when
    final List<EntityDto> defaultUserEntities = entitiesClient.getAllEntities();

    // then
    assertThat(defaultUserEntities.size(), is(3));
    final EntityDto combinedReportEntityDto = defaultUserEntities.stream()
      .filter(EntityDto::getCombined)
      .findFirst()
      .orElseThrow(() -> new OptimizeIntegrationTestException("Should have an entity!"));
    assertThat(combinedReportEntityDto.getData().getSubEntityCounts().size(), is(1));
    assertThat(combinedReportEntityDto.getData().getSubEntityCounts().get(EntityType.REPORT), is(2L));
  }

  @Test
  public void getEntityNames_WorksForAllPossibleEntities() {
    //given
    String reportId = addSingleReportToOptimize("aReportName", ReportType.PROCESS);
    String dashboardId = addDashboardToOptimize("aDashboardName");
    String collectionId = addCollection("aCollectionName");
    String eventProcessId = addEventProcessMappingToOptimize("anEventProcessName");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    EntityNameDto result = entitiesClient.getEntityNames(collectionId, dashboardId, reportId, eventProcessId);

    // then
    assertThat(result.getCollectionName(), is("aCollectionName"));
    assertThat(result.getDashboardName(), is("aDashboardName"));
    assertThat(result.getReportName(), is("aReportName"));
    assertThat(result.getEventBasedProcessName(), is("anEventProcessName"));
  }

  @Test
  public void getEntityNames_ReturnsNoResponseForEventBasedProcessIfThereIsNone() {
    //given
    String reportId = addSingleReportToOptimize("aReportName", ReportType.PROCESS);
    String dashboardId = addDashboardToOptimize("aDashboardName");
    String collectionId = addCollection("aCollectionName");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    EntityNameDto result = entitiesClient.getEntityNames(collectionId, dashboardId, reportId, "eventProcessId");

    // then
    assertThat(result.getCollectionName(), is("aCollectionName"));
    assertThat(result.getDashboardName(), is("aDashboardName"));
    assertThat(result.getReportName(), is("aReportName"));
    assertThat(result.getEventBasedProcessName(), is(nullValue()));
  }

  @Test
  public void getEntityNames_SeveralReportsDoNotDistortResult() {
    //given
    String reportId = addSingleReportToOptimize("aProcessReportName", ReportType.PROCESS);
    addSingleReportToOptimize("aDecisionReportName", ReportType.DECISION);
    addCombinedReport("aCombinedReportName");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    EntityNameDto result = entitiesClient.getEntityNames(null, null, reportId, null);

    // then
    assertThat(result.getCollectionName(), nullValue());
    assertThat(result.getDashboardName(), nullValue());
    assertThat(result.getReportName(), is("aProcessReportName"));
    assertThat(result.getEventBasedProcessName(), nullValue());
  }

  @Test
  public void getEntityNames_WorksForDecisionReports() {
    //given
    String reportId = addSingleReportToOptimize("aDecisionReportName", ReportType.DECISION);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    EntityNameDto result = entitiesClient.getEntityNames(null, null, reportId, null);

    // then
    assertThat(result.getCollectionName(), nullValue());
    assertThat(result.getDashboardName(), nullValue());
    assertThat(result.getReportName(), is("aDecisionReportName"));
    assertThat(result.getEventBasedProcessName(), nullValue());
  }

  @Test
  public void getEntityNames_WorksForCombinedReports() {
    //given
    String reportId = addCombinedReport("aCombinedReportName");
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    EntityNameDto result = entitiesClient.getEntityNames(null, null, reportId, null);

    // then
    assertThat(result.getCollectionName(), nullValue());
    assertThat(result.getDashboardName(), nullValue());
    assertThat(result.getReportName(), is("aCombinedReportName"));
    assertThat(result.getEventBasedProcessName(), nullValue());
  }

  @Test
  public void getEntityNames_NotAvailableIdReturns404() {
    //given
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetEntityNamesRequest(new EntityNameRequestDto(null, null, "notAvailableRequest", null))
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
  }

  @Test
  public void getEntityNames_NoIdProvidedReturns400() {
    //given
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetEntityNamesRequest(new EntityNameRequestDto(null, null, null, null))
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  private String addCollection(final String collectionName) {
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.updateCollection(collectionId, new PartialCollectionDefinitionDto(collectionName));
    return collectionId;
  }

  private void addRoleToCollection(final String collectionId,
                                   final String identityId,
                                   final IdentityType identityType) {

    final CollectionRoleDto roleDto = new CollectionRoleDto(
      identityType.equals(IdentityType.USER)
        ? new IdentityDto(identityId, IdentityType.USER)
        : new IdentityDto(identityId, IdentityType.GROUP),
      RoleType.EDITOR
    );
    collectionClient.addRoleToCollection(collectionId, roleDto);
  }

  private String addSingleReportToOptimize(String name, ReportType reportType) {
    return addSingleReportToOptimize(name, reportType, null, DEFAULT_USERNAME);
  }

  private String addSingleReportToOptimize(String name, ReportType reportType, String collectionId, String user) {
    switch (reportType) {
      case PROCESS:
        SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
        singleProcessReportDefinitionDto.setName(name);
        singleProcessReportDefinitionDto.setCollectionId(collectionId);
        return reportClient.createSingleProcessReportAsUser(singleProcessReportDefinitionDto, user, user);
      case DECISION:
        SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
        singleDecisionReportDefinitionDto.setName(name);
        singleDecisionReportDefinitionDto.setCollectionId(collectionId);
        return reportClient.createNewDecisionReportAsUser(singleDecisionReportDefinitionDto, user, user);
      default:
        throw new IllegalStateException("ReportType not allowed!");
    }
  }

  private String addDashboardToOptimize(String name) {
    return addDashboardToOptimize(name, null, DEFAULT_USERNAME);
  }

  private String addDashboardToOptimize(String name, String collectionId, String user) {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setName(name);
    dashboardDefinitionDto.setCollectionId(collectionId);
    return dashboardClient.createDashboardAsUser(dashboardDefinitionDto, user, user);
  }

  private String addCombinedReport(String name) {
    return addCombinedReport(name, null);
  }

  private String addCombinedReport(String name, String collectionId) {
    CombinedReportDefinitionDto combinedReportDefinitionDto = new CombinedReportDefinitionDto();
    combinedReportDefinitionDto.setName(name);
    combinedReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(IdDto.class, Response.Status.OK.getStatusCode()).getId();
  }

  @SuppressWarnings("SameParameterValue")
  private String addEventProcessMappingToOptimize(final String eventProcessName) {
    EventProcessMappingCreateRequestDto eventBasedProcessDto = EventProcessMappingCreateRequestDto.eventProcessMappingCreateBuilder()
      .name(eventProcessName)
      .eventSources(Collections.singletonList(
        EventSourceEntryDto.builder()
          .eventScope(Collections.singletonList(EventScopeType.ALL))
          .type(EventSourceType.EXTERNAL)
          .build()))
      .build();
    return eventProcessClient.createEventProcessMapping(eventBasedProcessDto);
  }

}

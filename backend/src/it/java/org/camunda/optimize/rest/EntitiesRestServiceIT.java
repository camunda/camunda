/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionUpdateDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.writer.CollectionWriter.DEFAULT_COLLECTION_NAME;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReport;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class EntitiesRestServiceIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void getEntities_WithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetAllReportsRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getEntities_ReturnsMyUsersReports() {
    //given
    addSingleReportToOptimize("B Report", ReportType.PROCESS);
    addSingleReportToOptimize("A Report", ReportType.DECISION);
    addCombinedReport("D Combined");

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> privateEntities = getEntities();

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
  public void getEntities_DoesNotReturnOtherUsersReports() {
    //given
    engineRule.addUser("kermit", "kermit");
    engineRule.grantUserOptimizeAccess("kermit");
    addSingleReportToOptimize("B Report", ReportType.PROCESS, null, "kermit");
    addSingleReportToOptimize("A Report", ReportType.DECISION);
    addCombinedReport("D Combined");

    elasticSearchRule.refreshAllOptimizeIndices();

    // when (default user)
    final List<EntityDto> defaultUserEntities = getEntities();

    // then
    assertThat(defaultUserEntities.size(), is(2));
    assertThat(
      defaultUserEntities.stream().map(EntityDto::getName).collect(Collectors.toList()),
      containsInAnyOrder("A Report", "D Combined")
    );

    // when
    final List<EntityDto> kermitUserEntities = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication("kermit", "kermit")
      .buildGetAllEntitiesRequest()
      .executeAndReturnList(EntityDto.class, 200);

    // then
    assertThat(kermitUserEntities.size(), is(1));
    assertThat(
      kermitUserEntities.stream().map(EntityDto::getName).collect(Collectors.toList()),
      containsInAnyOrder("B Report")
    );
  }

  @Test
  public void getEntities_ReturnsMyUsersDashboards() {
    //given
    addDashboardToOptimize("A Dashboard");
    addDashboardToOptimize("B Dashboard");

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> privateEntities = getEntities();

    // then
    assertThat(privateEntities.size(), is(2));
  }

  @Test
  public void getEntities_DoesNotReturnOtherUsersDashboards() {
    //given
    engineRule.addUser("kermit", "kermit");
    engineRule.grantUserOptimizeAccess("kermit");
    addDashboardToOptimize("A Dashboard");
    addDashboardToOptimize("B Dashboard", null, "kermit");

    elasticSearchRule.refreshAllOptimizeIndices();

    // when (default user)
    final List<EntityDto> defaultUserEntities = getEntities();

    // then
    assertThat(defaultUserEntities.size(), is(1));
    assertThat(
      defaultUserEntities.stream().map(EntityDto::getName).collect(Collectors.toList()),
      containsInAnyOrder("A Dashboard")
    );

    // when
    final List<EntityDto> kermitUserEntities = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication("kermit", "kermit")
      .buildGetAllEntitiesRequest()
      .executeAndReturnList(EntityDto.class, 200);

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
    addEmptyCollectionToOptimize();
    addEmptyCollectionToOptimize();

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> privateEntities = getEntities();

    // then
    assertThat(privateEntities.size(), is(2));
  }

  @Test
  public void getEntities_DoesNotReturnEntitiesInCollections() {
    // given
    final String collectionId = addEmptyCollectionToOptimize();

    addSingleReportToOptimize("A Report", ReportType.DECISION);
    addSingleReportToOptimize("B Report", ReportType.PROCESS, collectionId, DEFAULT_USERNAME);
    addDashboardToOptimize("C Dashboard", collectionId, DEFAULT_USERNAME);
    addCombinedReport("D Combined");

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> defaultUserEntities = getEntities();

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

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> entities = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllEntitiesRequest()
      .executeAndReturnList(EntityDto.class, 200);

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
    addEmptyCollectionToOptimize();

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> defaultUserEntities = getEntities();

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
    final String collectionId = addEmptyCollectionToOptimize();

    addSingleReportToOptimize("A Report", ReportType.DECISION, collectionId, DEFAULT_USERNAME);
    addSingleReportToOptimize("B Report", ReportType.PROCESS, collectionId, DEFAULT_USERNAME);
    addDashboardToOptimize("C Dashboard", collectionId, DEFAULT_USERNAME);
    addCombinedReport("D Combined", collectionId, DEFAULT_USERNAME);

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> defaultUserEntities = getEntities();

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
    addEmptyCollectionToOptimize();

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> defaultUserEntities = getEntities();

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
    final String collectionId = addEmptyCollectionToOptimize();
    final String user1 = "user1";
    engineRule.addUser(user1, user1);
    engineRule.grantUserOptimizeAccess(user1);
    addRoleToCollection(collectionId, user1, IdentityType.USER);
    final String groupA = "groupA";
    engineRule.createGroup(groupA);
    addRoleToCollection(collectionId, groupA, IdentityType.GROUP);
    final String groupB = "groupB";
    engineRule.createGroup(groupB);
    addRoleToCollection(collectionId, groupB, IdentityType.GROUP);
    final String groupC = "groupC";
    engineRule.createGroup(groupC);
    addRoleToCollection(collectionId, groupC, IdentityType.GROUP);

    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final List<EntityDto> defaultUserEntities = getEntities();

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
    combinedReportUpdate.setData(createCombinedReport(reportId1, reportId2));
    embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportUpdate)
      .execute();

    // when
    final List<EntityDto> defaultUserEntities = getEntities();

    // then
    assertThat(defaultUserEntities.size(), is(3));
    final EntityDto combinedReportEntityDto = defaultUserEntities.stream()
      .filter(EntityDto::getCombined)
      .findFirst()
      .get();
    assertThat(combinedReportEntityDto.getData().getSubEntityCounts().size(), is(1));
    assertThat(combinedReportEntityDto.getData().getSubEntityCounts().get(EntityType.REPORT), is(2L));
  }

  @Test
  public void getEntityNames_WorksForAllPossibleEntities() {
    //given
    String reportId = addSingleReportToOptimize("aReportName", ReportType.PROCESS);
    String dashboardId = addDashboardToOptimize("aDashboardName");
    String collectionId = addCollection("aCollectionName");
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    EntityNameDto result = getEntityNames(collectionId, dashboardId, reportId);

    // then
    assertThat(result.getCollectionName(), is("aCollectionName"));
    assertThat(result.getDashboardName(), is("aDashboardName"));
    assertThat(result.getReportName(), is("aReportName"));
  }

  @Test
  public void getEntityNames_SeveralReportsDoNotDistortResult() {
    //given
    String reportId = addSingleReportToOptimize("aProcessReportName", ReportType.PROCESS);
    addSingleReportToOptimize("aDecisionReportName", ReportType.DECISION);
    addCombinedReport("aCombinedReportName");
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    EntityNameDto result = getEntityNames(null, null, reportId);

    // then
    assertThat(result.getCollectionName(), nullValue());
    assertThat(result.getDashboardName(), nullValue());
    assertThat(result.getReportName(), is("aProcessReportName"));
  }

  @Test
  public void getEntityNames_WorksForDecisionReports() {
    //given
    String reportId = addSingleReportToOptimize("aDecisionReportName", ReportType.DECISION);
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    EntityNameDto result = getEntityNames(null, null, reportId);

    // then
    assertThat(result.getCollectionName(), nullValue());
    assertThat(result.getDashboardName(), nullValue());
    assertThat(result.getReportName(), is("aDecisionReportName"));
  }

  @Test
  public void getEntityNames_WorksForCombinedReports() {
    //given
    String reportId = addCombinedReport("aCombinedReportName");
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    EntityNameDto result = getEntityNames(null, null, reportId);

    // then
    assertThat(result.getCollectionName(), nullValue());
    assertThat(result.getDashboardName(), nullValue());
    assertThat(result.getReportName(), is("aCombinedReportName"));
  }

  @Test
  public void getEntityNames_NotAvailableIdReturns404() {
    //given
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetEntityNamesRequest(new EntityNameRequestDto(null, null, "notAvailableRequest"))
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void getEntityNames_NoIdProvidedReturns400() {
    //given
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetEntityNamesRequest(new EntityNameRequestDto(null, null, null))
      .execute();

    // then
    assertThat(response.getStatus(), is(400));
  }

  private String addCollection(final String collectionName) {
    final String collectionId = addEmptyCollectionToOptimize();
    updateCollectionRequest(collectionId, new PartialCollectionUpdateDto(collectionName));
    return collectionId;
  }

  private void addRoleToCollection(final String collectionId,
                                   final String identityId,
                                   final IdentityType identityType) {

    final CollectionRoleDto roleDto = new CollectionRoleDto(
      new IdentityDto(identityId, identityType),
      RoleType.EDITOR
    );
    embeddedOptimizeRule
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute(IdDto.class, 200);
  }

  private String addSingleReportToOptimize(String name, ReportType reportType) {
    return addSingleReportToOptimize(name, reportType, null, DEFAULT_USERNAME);
  }

  private String addSingleReportToOptimize(String name, ReportType reportType, String collectionId, String user) {
    switch (reportType) {
      case PROCESS:
        final String processReportId = embeddedOptimizeRule
          .getRequestExecutor()
          .buildCreateSingleProcessReportRequest(collectionId)
          .withUserAuthentication(user, user)
          .execute(IdDto.class, 200)
          .getId();

        SingleProcessReportDefinitionDto processReportDefinition = new SingleProcessReportDefinitionDto();
        processReportDefinition.setName(name);

        embeddedOptimizeRule.getRequestExecutor()
          .withUserAuthentication(user, user)
          .buildUpdateSingleProcessReportRequest(processReportId, processReportDefinition)
          .execute(204);

        return processReportId;
      case DECISION:
        final String decisionReportId = embeddedOptimizeRule
          .getRequestExecutor()
          .buildCreateSingleDecisionReportRequest(collectionId)
          .withUserAuthentication(user, user)
          .execute(IdDto.class, 200)
          .getId();

        SingleDecisionReportDefinitionDto decisionReportDefinition = new SingleDecisionReportDefinitionDto();
        decisionReportDefinition.setName(name);

        embeddedOptimizeRule.getRequestExecutor()
          .withUserAuthentication(user, user)
          .buildUpdateSingleDecisionReportRequest(decisionReportId, decisionReportDefinition)
          .execute(204);

        return decisionReportId;
      default:
        throw new IllegalStateException("ReportType not allowed!");
    }
  }

  private String addDashboardToOptimize(String name) {
    return addDashboardToOptimize(name, null, DEFAULT_USERNAME);
  }

  private String addDashboardToOptimize(String name, String collectionId, String user) {
    final String id = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest(collectionId)
      .withUserAuthentication(user, user)
      .execute(IdDto.class, 200)
      .getId();

    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setName(name);

    embeddedOptimizeRule.getRequestExecutor()
      .buildUpdateDashboardRequest(id, dashboardDefinitionDto)
      .withUserAuthentication(user, user)
      .execute(204);

    return id;
  }

  private String addCombinedReport(String name) {
    return addCombinedReport(name, null, DEFAULT_USERNAME);
  }

  private String addCombinedReport(String name, String collectionId, String user) {
    final String id = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(collectionId)
      .withUserAuthentication(user, user)
      .execute(IdDto.class, 200).getId();

    CombinedReportDefinitionDto definitionDto = new CombinedReportDefinitionDto();
    definitionDto.setName(name);

    embeddedOptimizeRule.getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(id, definitionDto)
      .withUserAuthentication(user, user)
      .execute(204);

    return id;
  }

  private List<EntityDto> getEntities() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllEntitiesRequest()
      .executeAndReturnList(EntityDto.class, 200);
  }

  private EntityNameDto getEntityNames(String collectionId, String dashboardId, String reportId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetEntityNamesRequest(new EntityNameRequestDto(collectionId, dashboardId, reportId))
      .execute(EntityNameDto.class, 200);
  }

  private String addEmptyCollectionToOptimize() {
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
}

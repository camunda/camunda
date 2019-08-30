/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;


public class DashboardRestServiceIT {

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void createNewDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildCreateDashboardRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewDashboard() {
    // when
    IdDto idDto = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdDto.class, 200);

    // then the status code is okay
    assertThat(idDto, is(notNullValue()));
  }

  @Test
  public void updateDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildUpdateDashboardRequest("1", null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateNonExistingDashboard() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateDashboardRequest("nonExistingId", new DashboardDefinitionDto())
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void updateDashboard() {
    //given
    String id = addEmptyDashboard();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateDashboardRequest(id, new DashboardDefinitionDto())
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void updateDashboardDoesNotChangeCollectionId() {
    //given
    final String collectionId = addEmptyCollectionToOptimize();
    String id = addEmptyDashboardToCollection(collectionId);

    // when
    embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateDashboardRequest(id, new DashboardDefinitionDto())
      .execute();

    // then
    final DashboardDefinitionDto dashboard = getDashboard(id);
    assertThat(dashboard.getCollectionId(), is(collectionId));
  }

  @Test
  public void copyDashboard() {
    // given
    String dashboardId = addEmptyDashboard();
    addEmptyReportToDashboard(dashboardId);

    engineIntegrationRule.addUser("john", "john");
    engineIntegrationRule.grantAllAuthorizations("john");

    // when
    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .withUserAuthentication("john", "john")
      .execute(IdDto.class, 200);

    // then
    DashboardDefinitionDto oldDashboard = getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = getDashboard(copyId.getId());
    assertThat(dashboard.toString(), is(oldDashboard.toString()));
    assertThat(dashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(dashboard.getLastModifier(), is("john"));

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty(), is(false));
    assertThat(newReportIds, containsInAnyOrder(oldDashboard.getReports().stream()
                                                  .map(ReportLocationDto::getId).toArray()));
  }

  @Test
  public void copyDashboardWithNameParameter() {
    // given
    final String dashboardId = addEmptyDashboard();
    addEmptyReportToDashboard(dashboardId);

    final String testDashboardCopyName = "This is my new report copy! ;-)";

    // when
    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("name", testDashboardCopyName)
      .execute(IdDto.class, 200);

    // then
    DashboardDefinitionDto oldDashboard = getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = getDashboard(copyId.getId());
    assertThat(dashboard.toString(), is(oldDashboard.toString()));
    assertThat(dashboard.getName(), is(testDashboardCopyName));
  }

  @Test
  public void copyPrivateDashboardAndMoveToCollection() {
    // given
    final String collectionId = addEmptyCollectionToOptimize();
    String dashboardId = addEmptyDashboard();
    addEmptyReportToDashboard(dashboardId);

    engineIntegrationRule.addUser("john", "john");
    engineIntegrationRule.grantAllAuthorizations("john");

    // when
    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId, collectionId)
      .withUserAuthentication("john", "john")
      .execute(IdDto.class, 200);

    // then
    DashboardDefinitionDto oldDashboard = getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = getDashboard(copyId.getId());
    assertThat(dashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(dashboard.getLastModifier(), is("john"));
    assertThat(dashboard.getCollectionId(), is(collectionId));
    assertThat(oldDashboard.getCollectionId(), is(nullValue()));

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty(), is(false));
    assertThat(newReportIds, not(containsInAnyOrder(oldDashboard.getReports().stream()
                                                      .map(ReportLocationDto::getId).toArray())));
  }


  @Test
  public void copyDashboardFromCollectionToPrivateEntities() {
    // given
    final String collectionId = addEmptyCollectionToOptimize();
    String dashboardId = addEmptyDashboardToCollection(collectionId);
    addEmptyReportToDashboard(dashboardId, collectionId);

    engineIntegrationRule.addUser("john", "john");
    engineIntegrationRule.grantAllAuthorizations("john");


    // when
    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("collectionId", "null")
      .withUserAuthentication("john", "john")
      .execute(IdDto.class, 200);

    // then
    DashboardDefinitionDto oldDashboard = getDashboard(dashboardId);
    DashboardDefinitionDto dashboard = getDashboard(copyId.getId());
    assertThat(dashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(dashboard.getLastModifier(), is("john"));
    assertThat(dashboard.getCollectionId(), is(nullValue()));
    assertThat(oldDashboard.getCollectionId(), is(collectionId));

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty(), is(false));
    assertThat(newReportIds, not(containsInAnyOrder(oldDashboard.getReports().stream()
                                                      .map(ReportLocationDto::getId).toArray())));
  }

  @Test
  public void copyDashboardFromCollectionToDifferentCollection() {
    // given
    final String oldCollectionId = addEmptyCollectionToOptimize();
    String dashboardId = addEmptyDashboardToCollection(oldCollectionId);
    addEmptyReportToDashboard(dashboardId, oldCollectionId);

    engineIntegrationRule.addUser("john", "john");
    engineIntegrationRule.grantAllAuthorizations("john");

    final String newCollectionId = addEmptyCollectionToOptimize();

    // when
    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("collectionId", newCollectionId)
      .withUserAuthentication("john", "john")
      .execute(IdDto.class, 200);

    // then
    DashboardDefinitionDto oldDashboard = getDashboard(dashboardId);
    DashboardDefinitionDto newDashboard = getDashboard(copyId.getId());
    assertThat(newDashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(newDashboard.getLastModifier(), is("john"));
    assertThat(newDashboard.getCollectionId(), is(newCollectionId));
    assertThat(oldDashboard.getCollectionId(), is(oldCollectionId));

    final List<String> newReportIds = newDashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty(), is(false));
    assertThat(newReportIds, not(containsInAnyOrder(oldDashboard.getReports().stream()
                                                      .map(ReportLocationDto::getId).toArray())));
  }

  @Test
  public void getStoredDashboardsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetAllDashboardsRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getStoredDashboards() {
    OptimizeRequestExecutor requestExecutor = embeddedOptimizeRule
      .getRequestExecutor();

    //given
    String id = requestExecutor
      .buildCreateDashboardRequest()
      .execute(IdDto.class, 200)
      .getId();

    // when
    List<DashboardDefinitionDto> dashboards = getAllDashboards();

    // then
    assertThat(dashboards.size(), is(1));
    assertThat(dashboards.get(0).getId(), is(id));
  }

  @Test
  public void getDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetDashboardRequest("asdf")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getDashboard() {
    //given
    String id = addEmptyDashboard();

    // when
    DashboardDefinitionDto dashboard = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDashboardRequest(id)
      .execute(DashboardDefinitionDto.class, 200);

    // then
    assertThat(dashboard, is(notNullValue()));
    assertThat(dashboard.getId(), is(id));
  }

  @Test
  public void getDashboardForNonExistingIdThrowsError() {
    // when
    String response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDashboardRequest("fooid")
      .execute(String.class, 404);

    // then the status code is okay
    assertThat(response.contains("Dashboard does not exist!"), is(true));
  }

  @Test
  public void deleteDashboardWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildDeleteDashboardRequest("1124")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewDashboard() {
    //given
    String id = addEmptyDashboard();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteDashboardRequest(id)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllDashboards().size(), is(0));
  }

  @Test
  public void deleteNonExistingDashboard() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteDashboardRequest("nonExistingId")
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  private String addEmptyDashboard() {
    return addEmptyDashboardToCollection(null);
  }

  private String addEmptyDashboardToCollection(final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String addEmptySingleProcessReport() {
    return addEmptySingleProcessReportToCollection(null);
  }

  private String addEmptySingleProcessReportToCollection(final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();
  }

  private List<DashboardDefinitionDto> getAllDashboards() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllDashboardsRequest()
      .executeAndReturnList(DashboardDefinitionDto.class, 200);
  }

  private DashboardDefinitionDto getDashboard(String dashboardId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetDashboardRequest(dashboardId)
      .execute(DashboardDefinitionDto.class, 200);
  }

  private String addEmptyCollectionToOptimize() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }


  private void updateDashboardRequest(final String dashboardId,
                                      final List<ReportLocationDto> reports) {


    final DashboardDefinitionDto dashboard = embeddedOptimizeRule.getRequestExecutor()
      .buildGetDashboardRequest(dashboardId).execute(DashboardDefinitionDto.class, 200);

    if (reports != null) {
      dashboard.setReports(reports);
    }

    embeddedOptimizeRule.getRequestExecutor()
      .buildUpdateDashboardRequest(dashboardId, dashboard)
      .execute(204);

  }

  private void addEmptyReportToDashboard(final String dashboardId, String collectionId) {
    final String reportId = addEmptySingleProcessReport();
    final ReportLocationDto reportLocationDto = new ReportLocationDto();
    reportLocationDto.setId(reportId);
    updateDashboardRequest(dashboardId, Collections.singletonList(reportLocationDto));
  }

  private void addEmptyReportToDashboard(final String dashboardId) {
    addEmptyReportToDashboard(dashboardId, null);
  }
}

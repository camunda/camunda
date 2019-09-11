/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;


public class DashboardHandlingIT {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @After
  public void cleanUp() {
    LocalDateUtil.reset();
  }

  @Test
  public void dashboardIsWrittenToElasticsearch() throws IOException {
    // given
    String id = addEmptyPrivateDashboard();

    // when
    GetRequest getRequest = new GetRequest(DASHBOARD_INDEX_NAME, DASHBOARD_INDEX_NAME, id);
    GetResponse getResponse = elasticSearchRule.getOptimizeElasticClient().get(getRequest, RequestOptions.DEFAULT);

    // then
    assertThat(getResponse.isExists(), is(true));
  }

  @Test
  public void writeAndThenReadGivesTheSameResult() {
    // given
    String id = addEmptyPrivateDashboard();

    // when
    DashboardDefinitionDto dashboard = getDashboardById(id);

    // then
    assertThat(dashboard, is(notNullValue()));
    assertThat(dashboard.getId(), is(id));
  }

  @Test
  public void createAndGetSeveralDashboards() {
    // given
    String id1 = addEmptyPrivateDashboard();
    String id2 = addEmptyPrivateDashboard();

    // when
    DashboardDefinitionDto dashboard1 = getDashboardById(id1);
    DashboardDefinitionDto dashboard2 = getDashboardById(id2);

    // then
    assertThat(dashboard1, is(notNullValue()));
    assertThat(dashboard1.getId(), is(id1));
    assertThat(dashboard2, is(notNullValue()));
    assertThat(dashboard2.getId(), is(id2));
  }

  @Test
  public void copyPrivateDashboardAndMoveToCollection() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    addEmptyReportToDashboard(dashboardId);

    final String collectionId = addEmptyCollectionToOptimize();

    // when
    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId, collectionId)
      .execute(IdDto.class, 200);

    // then
    DashboardDefinitionDto oldDashboard = getDashboardById(dashboardId);
    DashboardDefinitionDto dashboard = getDashboardById(copyId.getId());
    assertThat(dashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(dashboard.getCollectionId(), is(collectionId));
    assertThat(oldDashboard.getCollectionId(), is(nullValue()));

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty(), is(false));
    assertThat(
      newReportIds,
      not(containsInAnyOrder(oldDashboard.getReports().stream().map(ReportLocationDto::getId).toArray()))
    );
  }

  @Test
  public void copyDashboardFromCollectionToPrivateEntities() {
    // given
    final String collectionId = addEmptyCollectionToOptimize();
    String dashboardId = addEmptyDashboardToCollectionAsDefaultUser(collectionId);
    addEmptyReportToDashboard(dashboardId);

    // when
    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("collectionId", "null")
      .execute(IdDto.class, 200);

    // then
    DashboardDefinitionDto oldDashboard = getDashboardById(dashboardId);
    DashboardDefinitionDto dashboard = getDashboardById(copyId.getId());
    assertThat(dashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(dashboard.getCollectionId(), is(nullValue()));
    assertThat(oldDashboard.getCollectionId(), is(collectionId));

    final List<String> newReportIds = dashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty(), is(false));
    assertThat(
      newReportIds,
      not(containsInAnyOrder(oldDashboard.getReports().stream().map(ReportLocationDto::getId).toArray()))
    );
  }

  @Test
  public void copyDashboardFromCollectionToDifferentCollection() {
    // given
    final String oldCollectionId = addEmptyCollectionToOptimize();
    String dashboardId = addEmptyDashboardToCollectionAsDefaultUser(oldCollectionId);
    addEmptyReportToDashboard(dashboardId);

    final String newCollectionId = addEmptyCollectionToOptimize();

    // when
    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyDashboardRequest(dashboardId)
      .addSingleQueryParam("collectionId", newCollectionId)
      .execute(IdDto.class, 200);

    // then
    DashboardDefinitionDto oldDashboard = getDashboardById(dashboardId);
    DashboardDefinitionDto newDashboard = getDashboardById(copyId.getId());
    assertThat(newDashboard.getName(), is(oldDashboard.getName() + " – Copy"));
    assertThat(newDashboard.getCollectionId(), is(newCollectionId));
    assertThat(oldDashboard.getCollectionId(), is(oldCollectionId));

    final List<String> newReportIds = newDashboard.getReports()
      .stream()
      .map(ReportLocationDto::getId)
      .collect(Collectors.toList());

    assertThat(newReportIds.isEmpty(), is(false));
    assertThat(
      newReportIds,
      not(containsInAnyOrder(oldDashboard.getReports().stream().map(ReportLocationDto::getId).toArray()))
    );
  }

  @Test
  public void updateDashboard() {
    // given
    final String shouldBeIgnoredString = "shouldNotBeUpdated";
    final OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    final String id = addEmptyPrivateDashboard();
    final ReportLocationDto reportLocationDto = new ReportLocationDto();
    reportLocationDto.setId("report-123");
    reportLocationDto.setConfiguration("testConfiguration");

    final DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setReports(Collections.singletonList(reportLocationDto));
    dashboard.setId(shouldBeIgnoredString);
    dashboard.setLastModifier("shouldNotBeUpdatedManually");
    dashboard.setName("MyDashboard");
    dashboard.setCreated(shouldBeIgnoredDate);
    dashboard.setLastModified(shouldBeIgnoredDate);
    dashboard.setOwner(shouldBeIgnoredString);

    // when
    updateDashboard(id, dashboard);
    DashboardDefinitionDto updatedDashboard = getDashboardById(id);

    // then
    assertThat(updatedDashboard.getReports().size(), is(1));
    ReportLocationDto retrievedLocation = updatedDashboard.getReports().get(0);
    assertThat(retrievedLocation.getId(), is("report-123"));
    assertThat(retrievedLocation.getConfiguration(), is("testConfiguration"));
    assertThat(updatedDashboard.getId(), is(id));
    assertThat(updatedDashboard.getCreated(), is(not(shouldBeIgnoredDate)));
    assertThat(updatedDashboard.getLastModifier(), is(DEFAULT_USERNAME));
    assertThat(updatedDashboard.getLastModified(), is(not(shouldBeIgnoredDate)));
    assertThat(updatedDashboard.getName(), is("MyDashboard"));
    assertThat(updatedDashboard.getOwner(), is(DEFAULT_USERNAME));
  }

  @Test
  public void doNotUpdateNullFieldsInDashboard() {
    // given
    String id = addEmptyPrivateDashboard();
    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();

    // when
    updateDashboard(id, dashboard);
    DashboardDefinitionDto updatedDashboard = getDashboardById(id);

    // then
    assertThat(updatedDashboard.getId(), is(id));
    assertThat(updatedDashboard.getCreated(), is(notNullValue()));
    assertThat(updatedDashboard.getLastModified(), is(notNullValue()));
    assertThat(updatedDashboard.getLastModifier(), is(notNullValue()));
    assertThat(updatedDashboard.getName(), is(notNullValue()));
    assertThat(updatedDashboard.getOwner(), is(notNullValue()));
  }

  @Test
  public void deletedSingleReportIsRemovedFromDashboardWhenForced() {
    // given
    String dashboardId = addEmptyPrivateDashboard();
    String reportIdToDelete = createNewSingleReport();

    ReportLocationDto reportToBeDeletedReportLocationDto = new ReportLocationDto();
    reportToBeDeletedReportLocationDto.setId(reportIdToDelete);
    reportToBeDeletedReportLocationDto.setConfiguration("testConfiguration");
    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setReports(Collections.singletonList(reportToBeDeletedReportLocationDto));
    updateDashboard(dashboardId, dashboard);

    // when
    deleteReport(reportIdToDelete, true);

    // then
    DashboardDefinitionDto updatedDashboard = getDashboardById(dashboardId);
    updatedDashboard.getReports().forEach(
      reportLocationDto -> assertThat(reportLocationDto.getId(), is(not(reportIdToDelete)))
    );
  }

  private void deleteReport(String reportId, Boolean force) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  private String addEmptyPrivateDashboard() {
    return addEmptyDashboardToCollectionAsDefaultUser(null);
  }

  private String addEmptyDashboardToCollectionAsDefaultUser(final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();
  }

  private DashboardDefinitionDto getDashboardById(final String dashboardId) {
    return embeddedOptimizeRule.getRequestExecutor()
      .buildGetDashboardRequest(dashboardId)
      .execute(DashboardDefinitionDto.class, 200);
  }

  private void updateDashboard(String id, DashboardDefinitionDto updatedDashboard) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateDashboardRequest(id, updatedDashboard)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  private void updateDashboard(final String dashboardId, final List<ReportLocationDto> reports) {
    final DashboardDefinitionDto dashboard = embeddedOptimizeRule.getRequestExecutor()
      .buildGetDashboardRequest(dashboardId).execute(DashboardDefinitionDto.class, 200);

    if (reports != null) {
      dashboard.setReports(reports);
    }

    updateDashboard(dashboardId, dashboard);
  }

  private String createNewSingleReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private void addEmptyReportToDashboard(final String dashboardId) {
    final String reportId = addEmptySingleProcessReport();
    final ReportLocationDto reportLocationDto = new ReportLocationDto();
    reportLocationDto.setId(reportId);
    updateDashboard(dashboardId, Collections.singletonList(reportLocationDto));
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

  private String addEmptyCollectionToOptimize() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportType.DECISION;
import static org.camunda.optimize.rest.RestTestUtil.getOffsetDiffInHours;
import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ALERT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.REPORT_SHARE_INDEX_NAME;
import static org.camunda.optimize.util.DmnModels.createDecisionDefinitionWoName;
import static org.camunda.optimize.util.DmnModels.createDefaultDmnModel;
import static org.mockserver.model.HttpRequest.request;

public class ReportRestServiceIT extends AbstractReportRestServiceIT {

  @Test
  public void createNewReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void createNewSingleReport(final ReportType reportType) {
    // when
    String id = addEmptyReportToOptimize(reportType);
    // then
    assertThat(id).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void createNewSingleReportFromDefinition(final ReportType reportType) {
    // when
    String id = addReportToOptimizeWithDefinitionAndRandomXml(reportType);
    // then
    assertThat(id).isNotNull();
  }

  @Test
  public void createNewCombinedReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildCreateCombinedReportRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void createNewCombinedReport() {
    // when
    String id = reportClient.createNewCombinedReport();
    // then
    assertThat(id).isNotNull();
  }

  @Test
  public void createNewCombinedReportFromDefinition() {
    // when
    CombinedReportDefinitionRequestDto combinedReportDefinitionDto = new CombinedReportDefinitionRequestDto();
    combinedReportDefinitionDto.setData(ProcessReportDataBuilderHelper.createCombinedReportData());
    IdResponseDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdResponseDto.class, OK.getStatusCode());
    // then
    assertThat(idDto).isNotNull();
  }

  @Test
  public void updateReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildUpdateSingleProcessReportRequest("1", null)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void updateNonExistingReport() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest("nonExistingId", constructProcessReportWithFakePD())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void updateReport(final ReportType reportType) {
    //given
    String id = addEmptyReportToOptimize(reportType);

    // when
    Response response = updateReportRequest(id, reportType);

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void updateReportWithXml(final ReportType reportType) {
    //given
    String id = addEmptyReportToOptimize(reportType);

    // when
    final Response response = updateReportWithValidXml(id, reportType);

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void getStoredPrivateReports_excludesNonPrivateReports() {
    //given
    String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    String privateDecisionReportId = reportClient.createEmptySingleDecisionReport();
    String privateProcessReportId = reportClient.createEmptySingleProcessReport();
    reportClient.createEmptySingleProcessReportInCollection(collectionId);

    // when
    List<AuthorizedReportDefinitionResponseDto> reports = reportClient.getAllReportsAsUser();

    // then the returned list excludes reports in collections
    assertThat(
      reports.stream()
        .map(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
        .map(ReportDefinitionDto::getId)
        .collect(Collectors.toList()))
      .containsExactlyInAnyOrder(privateDecisionReportId, privateProcessReportId);
  }

  @Test
  public void getStoredPrivateReports_adoptTimezoneFromHeader() {
    //given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    reportClient.createEmptySingleProcessReport();

    // when
    List<AuthorizedReportDefinitionResponseDto> reports = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAllPrivateReportsRequest()
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      .executeAndReturnList(AuthorizedReportDefinitionResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(reports)
      .isNotNull()
      .hasSize(1);
    ReportDefinitionDto definitionDto = reports.get(0).getDefinitionDto();
    assertThat(definitionDto.getCreated()).isEqualTo(now);
    assertThat(definitionDto.getLastModified()).isEqualTo(now);
    assertThat(getOffsetDiffInHours(definitionDto.getCreated(), now)).isEqualTo(1.);
    assertThat(getOffsetDiffInHours(definitionDto.getLastModified(), now)).isEqualTo(1.);
  }

  @Test
  public void getStoredPrivateReportsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetAllPrivateReportsRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getStoredReportsWithNameFromXml() {
    //given
    String idProcessReport = reportClient.createEmptySingleProcessReport();
    updateReportWithValidXml(idProcessReport, ReportType.PROCESS);
    String idDecisionReport = reportClient.createEmptySingleDecisionReport();
    updateReportWithValidXml(idDecisionReport, DECISION);

    // when
    List<AuthorizedReportDefinitionResponseDto> reports = reportClient.getAllReportsAsUser();

    // then
    assertThat(reports).hasSize(2);
    assertThat(
      reports.stream()
        .map(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
        .map(ReportDefinitionDto::getId)
        .collect(Collectors.toList()))
      .containsExactlyInAnyOrder(idDecisionReport, idProcessReport);

    assertThat(
      reports.stream()
        .map(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
        .map(ReportDefinitionDto::getData)
        .map(data -> (SingleReportDataDto) data)
        .map(SingleReportDataDto::getDefinitionName)
        .collect(Collectors.toList())).containsExactlyInAnyOrder("Simple Process", "Invoice Classification");

    reports.forEach(
      reportDefinitionDto ->
        assertThat(
          ((SingleReportDataDto) reportDefinitionDto.getDefinitionDto().getData()).getConfiguration().getXml())
          .isNull()
    );
  }

  @Test
  public void getStoredReportsWithNoNameFromXml() {
    //given
    final String idProcessReport = reportClient.createEmptySingleProcessReport();
    final SingleProcessReportDefinitionRequestDto processReportDefinitionDto = getProcessReportDefinitionDtoWithXml(
      createProcessDefinitionXmlWithName(null)
    );
    reportClient.updateSingleProcessReport(idProcessReport, processReportDefinitionDto);

    final String idDecisionReport = reportClient.createEmptySingleDecisionReport();
    final SingleDecisionReportDefinitionRequestDto decisionReportDefinitionDto = getDecisionReportDefinitionDtoWithXml(
      createDecisionDefinitionWoName()
    );

    reportClient.updateDecisionReport(idDecisionReport, decisionReportDefinitionDto);

    // when
    List<AuthorizedReportDefinitionResponseDto> reports = reportClient.getAllReportsAsUser();

    // then
    assertThat(reports).hasSize(2);
    assertThat(
      reports.stream()
        .map(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
        .map(ReportDefinitionDto::getData)
        .map(data -> (SingleReportDataDto) data)
        .map(SingleReportDataDto::getDefinitionName)
        .collect(Collectors.toList()))
      .containsExactlyInAnyOrder(PROCESS_DEFINITION_KEY, DECISION_DEFINITION_KEY);

    reports.forEach(
      reportDefinitionDto ->
        assertThat(
          ((SingleReportDataDto) reportDefinitionDto.getDefinitionDto().getData()).getConfiguration().getXml()).isNull()
    );
  }

  @Test
  public void getReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetReportRequest("asdf")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void getReport(final ReportType reportType) {
    //given
    String id = addEmptyReportToOptimize(reportType);

    // when
    ReportDefinitionDto report = reportClient.getReportById(id);

    // then the status code is okay
    assertThat(report).isNotNull();
    assertThat(report.getReportType()).isEqualTo(reportType);
    assertThat(report.getId()).isEqualTo(id);
    assertThat(report.getOwner()).isEqualTo(DEFAULT_FULLNAME);
    assertThat(report.getLastModifier()).isEqualTo(DEFAULT_FULLNAME);
  }

  @Test
  public void getReport_adoptTimezoneFromHeader() {
    //given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    String reportId = reportClient.createEmptySingleProcessReport();

    // when
    ReportDefinitionDto report = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAllPrivateReportsRequest()
      .buildGetReportRequest(reportId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      .execute(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(report).isNotNull();
    assertThat(report.getCreated()).isEqualTo(now);
    assertThat(report.getLastModified()).isEqualTo(now);
    assertThat(getOffsetDiffInHours(report.getCreated(), now)).isEqualTo(1.);
    assertThat(getOffsetDiffInHours(report.getLastModified(), now)).isEqualTo(1.);
  }

  @Test
  public void getReport_forNonExistingIdThrowsNotFoundError() {
    // when
    String response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportRequest("fooId")
      .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());

    // then the status code is okay
    assertThat(response).containsSequence("Report does not exist.");
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void getReport_byIdContainsXml(ReportType reportType) {
    // given
    final String reportId = addReportToOptimizeWithDefinitionAndRandomXml(reportType);

    // when
    ReportDefinitionDto reportDefinition = reportClient.getReportById(reportId);

    // then
    String xmlString;
    switch (reportType) {
      case PROCESS:
        xmlString = ((SingleProcessReportDefinitionRequestDto) reportDefinition).getData().getConfiguration().getXml();
        break;
      case DECISION:
        xmlString = ((SingleDecisionReportDefinitionRequestDto) reportDefinition).getData().getConfiguration().getXml();
        break;
      default:
        xmlString = "";
    }
    assertThat(xmlString).containsSequence(RANDOM_STRING);
  }

  @Test
  public void deleteReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildDeleteReportRequest("1124")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void deleteReport(final ReportType reportType) {
    //given
    String id = addEmptyReportToOptimize(reportType);

    // when
    reportClient.deleteReport(id);

    // then
    assertThat(reportClient.getAllReportsAsUser()).isEmpty();
  }

  @Test
  public void deleteNonExistingReport() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest("nonExistingId")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void forceDeleteReport_notDeletedIfEsFailsWhenRemovingFromDashboards(final ReportType reportType) {
    //given
    String reportId = addEmptyReportToOptimize(reportType);
    DashboardDefinitionRestDto dashboardDefinitionDto = new DashboardDefinitionRestDto();
    final String dashboardId = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest(dashboardDefinitionDto)
      .execute(IdResponseDto.class, OK.getStatusCode())
      .getId();
    dashboardClient.updateDashboardWithReports(dashboardId, Arrays.asList(reportId, reportId));

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + DASHBOARD_INDEX_NAME + "/_update_by_query")
      .withMethod(POST);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, true)
      .execute();

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    assertThat(reportClient.getAllReportsAsUser())
      .extracting(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
      .extracting(ReportDefinitionDto::getId)
      .containsExactly(reportId);
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void forceDeleteReport_notDeletedIfEsFailsWhenDeletingAlertsForReport(final ReportType reportType) {
    //given
    String collectionId = collectionClient.createNewCollection();
    String reportId = addEmptyReportToOptimize(reportType, collectionId);
    alertClient.createAlertForReport(reportId);

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + ALERT_INDEX_NAME + "/_delete_by_query")
      .withMethod(POST);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    Response response = reportClient.deleteReport(reportId, true);

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    assertThat(collectionClient.getReportsForCollection(collectionId))
      .extracting(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
      .extracting(ReportDefinitionDto::getId)
      .containsExactly(reportId);

    assertThat(alertClient.getAllAlerts()).hasSize(1);
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void forceDeleteReport_notDeletedIfEsFailsWhenDeletingSharesForReport(final ReportType reportType) {
    //given
    String reportId = addEmptyReportToOptimize(reportType);
    ReportShareRestDto sharingDto = new ReportShareRestDto();
    sharingDto.setReportId(reportId);
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildShareReportRequest(sharingDto)
      .execute();

    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + REPORT_SHARE_INDEX_NAME + "/_doc/.*")
      .withMethod(DELETE);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId, true)
      .execute();

    // then
    esMockServer.verify(requestMatcher, VerificationTimes.once());
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    assertThat(reportClient.getAllReportsAsUser())
      .extracting(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
      .extracting(ReportDefinitionDto::getId)
      .containsExactly(reportId);
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void copySingleReport(ReportType reportType) {
    String id = createSingleReport(reportType);

    Response response = reportClient.copyReportToCollection(id, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    IdResponseDto copyId = response.readEntity(IdResponseDto.class);

    ReportDefinitionDto oldReport = reportClient.getReportById(id);
    ReportDefinitionDto report = reportClient.getReportById(copyId.getId());
    assertThat(report.getData()).hasToString(oldReport.getData().toString());
    assertThat(oldReport.getName() + " – Copy").isEqualTo(report.getName());
  }

  @Test
  public void copyCombinedReport() {
    String id = reportClient.createCombinedReport(null, new ArrayList<>());

    Response response = reportClient.copyReportToCollection(id, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    IdResponseDto copyId = response.readEntity(IdResponseDto.class);

    ReportDefinitionDto oldReport = reportClient.getReportById(id);
    ReportDefinitionDto report = reportClient.getReportById(copyId.getId());
    assertThat(report.getData()).hasToString(oldReport.getData().toString());
    assertThat(oldReport.getName() + " – Copy").isEqualTo(report.getName());
  }

  @Test
  public void copyReportWithNameParameter() {
    // given
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);

    SingleProcessReportDefinitionRequestDto single = constructProcessReportWithFakePD();
    String id = addSingleProcessReportWithDefinition(single.getData());

    final String testReportCopyName = "Hello World, I am a copied report???! :-o";

    // when
    IdResponseDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyReportRequest(id, collectionId)
      .addSingleQueryParam("name", testReportCopyName)
      .execute(IdResponseDto.class, OK.getStatusCode());

    // then
    ReportDefinitionDto oldReport = reportClient.getReportById(id);
    ReportDefinitionDto report = reportClient.getReportById(copyId.getId());
    assertThat(report.getData()).hasToString(oldReport.getData().toString());
    assertThat(report.getName()).isEqualTo(testReportCopyName);
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void copyPrivateSingleReportAndMoveToCollection(ReportType reportType) {
    // given
    String id = createSingleReport(reportType);
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(reportType.toDefinitionType());

    // when
    IdResponseDto copyId = reportClient.copyReportToCollection(id, collectionId).readEntity(IdResponseDto.class);

    // then
    ReportDefinitionDto oldReport = reportClient.getReportById(id);
    ReportDefinitionDto report = reportClient.getReportById(copyId.getId());
    assertThat(report.getData()).hasToString(oldReport.getData().toString());
    assertThat(oldReport.getName() + " – Copy").isEqualTo(report.getName());
    assertThat(oldReport.getCollectionId()).isNull();
    assertThat(report.getCollectionId()).isEqualTo(collectionId);
  }

  @Test
  public void copyPrivateCombinedReportAndMoveToCollection() {
    // given
    final String report1 = reportClient.createEmptySingleProcessReport();
    final String report2 = reportClient.createEmptySingleProcessReport();
    String id = reportClient.createCombinedReport(null, Arrays.asList(report1, report2));

    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);

    // when
    IdResponseDto copyId = reportClient.copyReportToCollection(id, collectionId).readEntity(IdResponseDto.class);

    // then
    ReportDefinitionDto oldReport = reportClient.getReportById(id);
    ReportDefinitionDto newReport = reportClient.getReportById(copyId.getId());
    assertThat(oldReport.getName() + " – Copy").isEqualTo(newReport.getName());
    assertThat(oldReport.getCollectionId()).isNull();
    assertThat(newReport.getCollectionId()).isEqualTo(collectionId);

    final CombinedReportDataDto oldData = (CombinedReportDataDto) oldReport.getData();
    assertThat(oldData.getReportIds()).isNotEmpty();
    assertThat(oldData.getReportIds()).containsExactlyInAnyOrder(report1, report2);

    final CombinedReportDataDto newData = (CombinedReportDataDto) newReport.getData();
    assertThat(newData.getReportIds()).isNotEmpty();
    assertThat(newData.getReportIds()).doesNotContain(report1, report2);

    newData.getReportIds()
      .forEach(newSingleReportId -> {
        final ReportDefinitionDto newSingleReport = reportClient.getReportById(newSingleReportId);
        assertThat(newSingleReport.getCollectionId()).isEqualTo(collectionId);
      });
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void copySingleReportFromCollectionToPrivateEntities(ReportType reportType) {
    // given
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(reportType.toDefinitionType());
    String id = createSingleReport(reportType, collectionId);

    // when
    IdResponseDto copyId = reportClient.copyReportToCollection(id, "null").readEntity(IdResponseDto.class);

    // then
    ReportDefinitionDto oldReport = reportClient.getReportById(id);
    ReportDefinitionDto report = reportClient.getReportById(copyId.getId());
    assertThat(report.getData()).hasToString(oldReport.getData().toString());
    assertThat(oldReport.getName() + " – Copy").isEqualTo(report.getName());
    assertThat(oldReport.getCollectionId()).isEqualTo(collectionId);
    assertThat(report.getCollectionId()).isNull();
  }

  @Test
  public void copyCombinedReportFromCollectionToPrivateEntities() {
    // given
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    final String report1 = reportClient.createEmptySingleProcessReportInCollection(collectionId);
    final String report2 = reportClient.createEmptySingleProcessReportInCollection(collectionId);
    String id = reportClient.createCombinedReport(collectionId, Arrays.asList(report1, report2));

    // when
    IdResponseDto copyId = reportClient.copyReportToCollection(id, "null").readEntity(IdResponseDto.class);

    // then
    ReportDefinitionDto oldReport = reportClient.getReportById(id);
    ReportDefinitionDto newReport = reportClient.getReportById(copyId.getId());

    assertThat(oldReport.getName() + " – Copy").isEqualTo(newReport.getName());
    assertThat(oldReport.getCollectionId()).isEqualTo(collectionId);
    assertThat(newReport.getCollectionId()).isNull();

    final CombinedReportDataDto oldData = (CombinedReportDataDto) oldReport.getData();
    assertThat(oldData.getReportIds()).isNotEmpty();
    assertThat(oldData.getReportIds()).containsExactlyInAnyOrder(report1, report2);

    final CombinedReportDataDto newData = (CombinedReportDataDto) newReport.getData();
    assertThat(newData.getReportIds()).isNotEmpty();
    assertThat(newData.getReportIds()).doesNotContain(report1, report2);

    newData.getReportIds()
      .forEach(newSingleReportId -> {
        final ReportDefinitionDto newSingleReport = reportClient.getReportById(newSingleReportId);
        assertThat(newSingleReport.getCollectionId()).isNull();
      });
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void copySingleReportFromCollectionToDifferentCollection(ReportType reportType) {
    // given
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(reportType.toDefinitionType());
    String id = createSingleReport(reportType, collectionId);
    final String newCollectionId = collectionClient.createNewCollectionWithDefaultScope(reportType.toDefinitionType());

    // when
    IdResponseDto copyId = reportClient.copyReportToCollection(id, newCollectionId).readEntity(IdResponseDto.class);

    // then
    ReportDefinitionDto oldReport = reportClient.getReportById(id);
    ReportDefinitionDto newReport = reportClient.getReportById(copyId.getId());
    assertThat(newReport.getData()).hasToString(oldReport.getData().toString());
    assertThat(oldReport.getName() + " – Copy").isEqualTo(newReport.getName());
    assertThat(oldReport.getCollectionId()).isEqualTo(collectionId);
    assertThat(newReport.getCollectionId()).isEqualTo(newCollectionId);
  }

  @Test
  public void copyCombinedReportFromCollectionToDifferentCollection() {
    // given
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    final String report1 = reportClient.createEmptySingleProcessReportInCollection(collectionId);
    final String report2 = reportClient.createEmptySingleProcessReportInCollection(collectionId);

    String id = reportClient.createCombinedReport(collectionId, Arrays.asList(report1, report2));

    final String newCollectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    IdResponseDto copyId = reportClient.copyReportToCollection(id, newCollectionId).readEntity(IdResponseDto.class);

    // then
    ReportDefinitionDto oldReport = reportClient.getReportById(id);
    ReportDefinitionDto newReport = reportClient.getReportById(copyId.getId());

    assertThat(oldReport.getName() + " – Copy").isEqualTo(newReport.getName());
    assertThat(oldReport.getCollectionId()).isEqualTo(collectionId);
    assertThat(newReport.getCollectionId()).isEqualTo(newCollectionId);
    final CombinedReportDataDto oldData = (CombinedReportDataDto) oldReport.getData();
    assertThat(oldData.getReportIds()).isNotEmpty();
    assertThat(oldData.getReportIds()).containsExactlyInAnyOrder(report1, report2);

    final CombinedReportDataDto newData = (CombinedReportDataDto) newReport.getData();
    assertThat(newData.getReportIds()).isNotEmpty();
    assertThat(newData.getReportIds()).doesNotContain(report1, report2);

    newData.getReportIds()
      .forEach(newSingleReportId -> {
        final ReportDefinitionDto newSingleReport = reportClient.getReportById(newSingleReportId);
        assertThat(newSingleReport.getCollectionId()).isEqualTo(newCollectionId);
      });
  }

  private Response updateReportRequest(final String id, final ReportType reportType) {
    if (ReportType.PROCESS.equals(reportType)) {
      return reportClient.updateSingleProcessReport(id, constructProcessReportWithFakePD());
    } else {
      return reportClient.updateDecisionReport(id, constructDecisionReportWithFakeDD());
    }
  }

  private String addEmptyReportToOptimize(final ReportType reportType, String collectionId) {
    return ReportType.PROCESS.equals(reportType)
      ? reportClient.createEmptySingleProcessReportInCollection(collectionId)
      : reportClient.createEmptySingleDecisionReportInCollection(collectionId);
  }

  private String addEmptyReportToOptimize(final ReportType reportType) {
    return ReportType.PROCESS.equals(reportType)
      ? reportClient.createEmptySingleProcessReport()
      : reportClient.createEmptySingleDecisionReport();
  }

  private String createSingleReport(final ReportType reportType) {
    return createSingleReport(reportType, null);
  }

  private String createSingleReport(final ReportType reportType, final String collectionId) {
    switch (reportType) {
      case PROCESS:
        SingleProcessReportDefinitionRequestDto processDef = constructProcessReportWithFakePD();
        return addSingleProcessReportWithDefinition(processDef.getData(), collectionId);
      case DECISION:
        SingleDecisionReportDefinitionRequestDto decisionDef = constructDecisionReportWithFakeDD();
        return addSingleDecisionReportWithDefinition(decisionDef.getData(), collectionId);
      default:
        throw new IllegalStateException("Unexpected value: " + reportType);
    }
  }

  @SneakyThrows
  private Response updateReportWithValidXml(final String id, final ReportType reportType) {
    final Response response;
    if (ReportType.PROCESS.equals(reportType)) {
      SingleProcessReportDefinitionRequestDto reportDefinitionDto = getProcessReportDefinitionDtoWithXml(
        createProcessDefinitionXmlWithName("Simple Process")
      );
      response = reportClient.updateSingleProcessReport(id, reportDefinitionDto);
    } else {
      SingleDecisionReportDefinitionRequestDto reportDefinitionDto = getDecisionReportDefinitionDtoWithXml(
        createDefaultDmnModel());
      response = reportClient.updateDecisionReport(id, reportDefinitionDto);
    }
    return response;
  }

  private SingleProcessReportDefinitionRequestDto getProcessReportDefinitionDtoWithXml(final String xml) {
    SingleProcessReportDefinitionRequestDto reportDefinitionDto = new SingleProcessReportDefinitionRequestDto();
    ProcessReportDataDto data = new ProcessReportDataDto();
    data.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    data.setProcessDefinitionVersion("1");
    data.getConfiguration().setXml(xml);
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  private SingleDecisionReportDefinitionRequestDto getDecisionReportDefinitionDtoWithXml(final DmnModelInstance dmn) {
    SingleDecisionReportDefinitionRequestDto reportDefinitionDto = new SingleDecisionReportDefinitionRequestDto();
    DecisionReportDataDto data = new DecisionReportDataDto();
    data.setDecisionDefinitionKey(DECISION_DEFINITION_KEY);
    data.setDecisionDefinitionVersion("1");
    data.getConfiguration().setXml(Dmn.convertToString(dmn));
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  private SingleProcessReportDefinitionRequestDto constructProcessReportWithFakePD() {
    SingleProcessReportDefinitionRequestDto reportDefinitionDto = new SingleProcessReportDefinitionRequestDto();
    ProcessReportDataDto data = new ProcessReportDataDto();
    data.setProcessDefinitionVersion("FAKE");
    data.setProcessDefinitionKey(DEFAULT_DEFINITION_KEY);
    data.setTenantIds(DEFAULT_TENANTS);
    data.getConfiguration().setXml("FAKE");
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  private SingleDecisionReportDefinitionRequestDto constructDecisionReportWithFakeDD() {
    SingleDecisionReportDefinitionRequestDto reportDefinitionDto = new SingleDecisionReportDefinitionRequestDto();
    DecisionReportDataDto data = new DecisionReportDataDto();
    data.setDecisionDefinitionVersion("FAKE");
    data.setDecisionDefinitionKey(DEFAULT_DEFINITION_KEY);
    data.setTenantIds(DEFAULT_TENANTS);
    data.getConfiguration().setXml("FAKE");
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  @SneakyThrows
  private String createProcessDefinitionXmlWithName(String name) {
    final BpmnModelInstance bpmnModelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .camundaVersionTag("aVersionTag")
      .name(name)
      .startEvent("startEvent_ID")
      .userTask("some_id")
      .userTask("some_other_id")
      .endEvent("endEvent_ID")
      .done();
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, bpmnModelInstance);
    return new String(xmlOutput.toByteArray(), StandardCharsets.UTF_8);
  }

}

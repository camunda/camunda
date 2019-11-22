/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.service.sharing.AbstractSharingIT;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.dto.optimize.ReportType.DECISION;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class ReportRestServiceIT extends AbstractIT {

  private static final String PROCESS_DEFINITION_XML_WITH_NAME = "bpmn/simple_withName.bpmn";
  private static final String PROCESS_DEFINITION_XML_WO_NAME = "bpmn/simple_woName.bpmn";
  private static final String DECISION_DEFINITION_XML = "dmn/invoiceBusinessDecision_withName_and_versionTag.xml";
  private static final String DECISION_DEFINITION_XML_WO_NAME = "dmn/invoiceBusinessDecision_woName.xml";
  private static final String PROCESS_DEFINITION_KEY = "simple";
  private static final String DECISION_DEFINITION_KEY = "invoiceClassification";
  private static final String RANDOM_KEY = "someRandomKey";
  private static final String RANDOM_VERSION = "someRandomVersion";
  private static final String RANDOM_STRING = "something";
  
  @Test
  public void createNewReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void createNewSingleReport(final ReportType reportType) {
    // when
    String id = addEmptyReportToOptimize(reportType);
    // then
    assertThat(id, is(notNullValue()));
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void createNewSingleReportFromDefinition(final ReportType reportType) {
    // when
    String id = addReportToOptimizeWithDefinition(reportType);
    // then
    assertThat(id, is(notNullValue()));
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
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewCombinedReport() {
    // when
    IdDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest()
      .execute(IdDto.class, 200);
    // then
    assertThat(idDto, is(notNullValue()));
  }

  @Test
  public void createNewCombinedReportFromDefinition() {
    // when
    CombinedReportDefinitionDto combinedReportDefinitionDto = new CombinedReportDefinitionDto();
    combinedReportDefinitionDto.setData(ProcessReportDataBuilderHelper.createCombinedReportData());
    IdDto idDto = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdDto.class, 200);
    // then
    assertThat(idDto, is(notNullValue()));
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
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateNonExistingReport() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest("nonExistingId", constructProcessReportWithFakePD())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(404));
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void updateReport(final ReportType reportType) {
    //given
    String id = addEmptyReportToOptimize(reportType);

    // when
    Response response = updateReportRequest(id, reportType);

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void updateReportWithXml(final ReportType reportType) {
    //given
    String id = addEmptyReportToOptimize(reportType);

    // when
    final Response response = updateReportWithValidXml(id, reportType);

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getStoredPrivateReportsExcludesNonPrivateReports() {
    //given
    String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    String privateDecisionReportId = addEmptyDecisionReport();
    String privateProcessReportId = addEmptyProcessReport();
    addEmptyProcessReport(collectionId);

    // when
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then the returned list excludes reports in collections
    assertThat(
      reports.stream().map(ReportDefinitionDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(privateDecisionReportId, privateProcessReportId)
    );
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
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getStoredReportsWithNameFromXml() {
    //given
    String idProcessReport = addEmptyProcessReport();
    updateReportWithValidXml(idProcessReport, ReportType.PROCESS);
    String idDecisionReport = addEmptyDecisionReport();
    updateReportWithValidXml(idDecisionReport, DECISION);

    // when
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports.size(), is(2));
    assertThat(
      reports.stream().map(ReportDefinitionDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(idDecisionReport, idProcessReport)
    );
    assertThat(
      reports.stream()
        .map(ReportDefinitionDto::getData)
        .map(data -> (SingleReportDataDto) data)
        .map(SingleReportDataDto::getDefinitionName)
        .collect(Collectors.toList()),

      containsInAnyOrder("Simple Process", "Invoice Classification")
    );
    reports.forEach(
      reportDefinitionDto ->
        assertThat(((SingleReportDataDto) reportDefinitionDto.getData()).getConfiguration().getXml(), is(nullValue()))
    );
  }

  @Test
  public void getStoredReportsWithNoNameFromXml() throws IOException {
    //given
    final String idProcessReport = addEmptyProcessReport();
    final SingleProcessReportDefinitionDto processReportDefinitionDto = getProcessReportDefinitionDtoWithXml(
      PROCESS_DEFINITION_XML_WO_NAME
    );
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(idProcessReport, processReportDefinitionDto)
      .execute();

    final String idDecisionReport = addEmptyDecisionReport();
    final SingleDecisionReportDefinitionDto decisionReportDefinitionDto = getDecisionReportDefinitionDtoWithXml(
      DECISION_DEFINITION_XML_WO_NAME
    );
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateSingleDecisionReportRequest(idDecisionReport, decisionReportDefinitionDto)
      .execute();

    // when
    List<ReportDefinitionDto> reports = getAllPrivateReports();

    // then
    assertThat(reports.size(), is(2));
    assertThat(
      reports.stream()
        .map(ReportDefinitionDto::getData)
        .map(data -> (SingleReportDataDto) data)
        .map(SingleReportDataDto::getDefinitionName)
        .collect(Collectors.toList()),

      containsInAnyOrder(PROCESS_DEFINITION_KEY, DECISION_DEFINITION_KEY)
    );
    reports.forEach(
      reportDefinitionDto ->
        assertThat(((SingleReportDataDto) reportDefinitionDto.getData()).getConfiguration().getXml(), is(nullValue()))
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
    assertThat(response.getStatus(), is(401));
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void getReport(final ReportType reportType) {
    //given
    String id = addEmptyReportToOptimize(reportType);

    // when
    ReportDefinitionDto report = getReport(id);

    // then the status code is okay
    assertThat(report, is(notNullValue()));
    assertThat(report.getReportType(), is(reportType));
    assertThat(report.getId(), is(id));
  }

  @Test
  public void getReportForNonExistingIdThrowsNotFoundError() {
    // when
    String response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportRequest("fooId")
      .execute(String.class, 404);

    // then the status code is okay
    assertThat(response.contains("Report does not exist."), is(true));
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
    assertThat(response.getStatus(), is(401));
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void deleteReport(final ReportType reportType) {
    //given
    String id = addEmptyReportToOptimize(reportType);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest(id)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllPrivateReports().size(), is(0));
  }

  @Test
  public void deleteNonExistingReport() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest("nonExistingId")
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void evaluateReportByIdWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEvaluateSavedReportRequest("123")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateReportById(ReportType reportType) {
    //given
    final String id;
    switch (reportType) {
      case PROCESS:
        ProcessReportDataDto processReportDataD = ProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(RANDOM_KEY)
          .setProcessDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build();
        id = addSingleProcessReportWithDefinition(processReportDataD);
        break;
      case DECISION:
        DecisionReportDataDto decisionReportData = DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(RANDOM_KEY)
          .setDecisionDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
        id = addSingleDecisionReportWithDefinition(decisionReportData);
        break;
      default:
        throw new IllegalStateException("Uncovered type: " + reportType);
    }

    // then
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void evaluateInvalidReportById() {
    //given
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(RANDOM_KEY)
      .setProcessDefinitionVersion(RANDOM_VERSION)
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    reportData.setGroupBy(new NoneGroupByDto());
    reportData.setVisualization(ProcessVisualization.NUMBER);
    String id = addSingleProcessReportWithDefinition(reportData);

    // then
    ReportEvaluationException response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .execute(ReportEvaluationException.class, 400);

    // then
    AbstractSharingIT.assertErrorFields(response);
  }

  @Test
  public void evaluateUnsavedReportWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEvaluateCombinedUnsavedReportRequest(null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateUnsavedReport(ReportType reportType) {
    //given
    final SingleReportDataDto reportDataDto;
    switch (reportType) {
      case PROCESS:
        reportDataDto = ProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(RANDOM_KEY)
          .setProcessDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build();
        break;
      case DECISION:
        reportDataDto = DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(RANDOM_KEY)
          .setDecisionDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
        break;
      default:
        throw new IllegalStateException("Uncovered type: " + reportType);
    }

    // then
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportDataDto)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void evaluateUnsavedCombinedReportWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEvaluateCombinedUnsavedReportRequest(null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void evaluateCombinedUnsavedReport() {
    // then
    CombinedReportDataDto combinedReport = ProcessReportDataBuilderHelper.createCombinedReportData();
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(combinedReport)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void nullReportsAreHandledAsEmptyList() {
    // then
    CombinedReportDataDto combinedReport = ProcessReportDataBuilderHelper.createCombinedReportData();
    combinedReport.setReports(null);

    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(combinedReport)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void copySingleReport(ReportType reportType) {
    String id = createSingleReport(reportType);

    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyReportRequest(id)
      .execute(IdDto.class, 200);

    ReportDefinitionDto oldReport = getReport(id);
    ReportDefinitionDto report = getReport(copyId.getId());
    assertThat(report.getData().toString(), is(oldReport.getData().toString()));
    assertThat(oldReport.getName() + " – Copy", is(report.getName()));
  }

  @Test
  public void copyCombinedReport() {
    CombinedReportDataDto combined = ProcessReportDataBuilderHelper.createCombinedReportData();
    IdDto id = createAndUpdateCombinedReport(combined, null);

    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyReportRequest(id.getId())
      .execute(IdDto.class, 200);

    ReportDefinitionDto oldReport = getReport(id.getId());
    ReportDefinitionDto report = getReport(copyId.getId());
    assertThat(report.getData().toString(), is(oldReport.getData().toString()));
    assertThat(oldReport.getName() + " – Copy", is(report.getName()));
  }

  @Test
  public void copyReportWithNameParameter() {
    // given
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);

    SingleProcessReportDefinitionDto single = constructProcessReportWithFakePD();
    String id = addSingleProcessReportWithDefinition(single.getData());

    final String testReportCopyName = "Hello World, I am a copied report???! :-o";

    // when
    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyReportRequest(id, collectionId)
      .addSingleQueryParam("name", testReportCopyName)
      .execute(IdDto.class, 200);

    // then
    ReportDefinitionDto oldReport = getReport(id);
    ReportDefinitionDto report = getReport(copyId.getId());
    assertThat(report.getData().toString(), is(oldReport.getData().toString()));
    assertThat(report.getName(), is(testReportCopyName));
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void copyPrivateSingleReportAndMoveToCollection(ReportType reportType) {
    // given
    String id = createSingleReport(reportType);
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(reportType.toDefinitionType());

    // when
    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyReportRequest(id, collectionId)
      .execute(IdDto.class, 200);

    // then
    ReportDefinitionDto oldReport = getReport(id);
    ReportDefinitionDto report = getReport(copyId.getId());
    assertThat(report.getData().toString(), is(oldReport.getData().toString()));
    assertThat(oldReport.getName() + " – Copy", is(report.getName()));
    assertThat(oldReport.getCollectionId(), is(nullValue()));
    assertThat(report.getCollectionId(), is(collectionId));
  }

  @Test
  public void copyPrivateCombinedReportAndMoveToCollection() {
    // given
    final String report1 = addEmptyProcessReport();
    final String report2 = addEmptyProcessReport();
    CombinedReportDataDto combined = ProcessReportDataBuilderHelper.createCombinedReportData(report1, report2);

    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);
    IdDto id = createAndUpdateCombinedReport(combined, null);

    // when
    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyReportRequest(id.getId(), collectionId)
      .execute(IdDto.class, 200);

    // then
    ReportDefinitionDto oldReport = getReport(id.getId());
    ReportDefinitionDto newReport = getReport(copyId.getId());
    assertThat(oldReport.getName() + " – Copy", is(newReport.getName()));
    assertThat(oldReport.getCollectionId(), is(nullValue()));
    assertThat(newReport.getCollectionId(), is(collectionId));

    final CombinedReportDataDto oldData = (CombinedReportDataDto) oldReport.getData();
    assertThat(oldData.getReportIds().isEmpty(), is(false));
    assertThat(oldData.getReportIds(), containsInAnyOrder(report1, report2));

    final CombinedReportDataDto newData = (CombinedReportDataDto) newReport.getData();
    assertThat(newData.getReportIds().isEmpty(), is(false));
    assertThat(newData.getReportIds(), not(containsInAnyOrder(report1, report2)));

    newData.getReportIds()
      .forEach(newSingleReportId -> {
        final ReportDefinitionDto newSingleReport = getReport(newSingleReportId);
        assertThat(newSingleReport.getCollectionId(), is(collectionId));
      });
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void copySingleReportFromCollectionToPrivateEntities(ReportType reportType) {
    // given
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(reportType.toDefinitionType());
    String id = createSingleReport(reportType, collectionId);

    // when
    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyReportRequest(id, "null")
      .execute(IdDto.class, 200);

    // then
    ReportDefinitionDto oldReport = getReport(id);
    ReportDefinitionDto report = getReport(copyId.getId());
    assertThat(report.getData().toString(), is(oldReport.getData().toString()));
    assertThat(oldReport.getName() + " – Copy", is(report.getName()));
    assertThat(oldReport.getCollectionId(), is(collectionId));
    assertThat(report.getCollectionId(), is(nullValue()));
  }

  @Test
  public void copyCombinedReportFromCollectionToPrivateEntities() {
    // given
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    final String report1 = addEmptyProcessReport(collectionId);
    final String report2 = addEmptyProcessReport(collectionId);
    CombinedReportDataDto combined = ProcessReportDataBuilderHelper.createCombinedReportData(report1, report2);
    IdDto id = createAndUpdateCombinedReport(combined, collectionId);

    // when
    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyReportRequest(id.getId(), "null")
      .execute(IdDto.class, 200);

    // then
    ReportDefinitionDto oldReport = getReport(id.getId());
    ReportDefinitionDto newReport = getReport(copyId.getId());

    assertThat(oldReport.getName() + " – Copy", is(newReport.getName()));
    assertThat(oldReport.getCollectionId(), is(collectionId));
    assertThat(newReport.getCollectionId(), is(nullValue()));

    final CombinedReportDataDto oldData = (CombinedReportDataDto) oldReport.getData();
    assertThat(oldData.getReportIds().isEmpty(), is(false));
    assertThat(oldData.getReportIds(), containsInAnyOrder(report1, report2));

    final CombinedReportDataDto newData = (CombinedReportDataDto) newReport.getData();
    assertThat(newData.getReportIds().isEmpty(), is(false));
    assertThat(newData.getReportIds(), not(containsInAnyOrder(report1, report2)));

    newData.getReportIds()
      .forEach(newSingleReportId -> {
        final ReportDefinitionDto newSingleReport = getReport(newSingleReportId);
        assertThat(newSingleReport.getCollectionId(), is(nullValue()));
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
    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyReportRequest(id, newCollectionId)
      .execute(IdDto.class, 200);

    // then
    ReportDefinitionDto oldReport = getReport(id);
    ReportDefinitionDto newReport = getReport(copyId.getId());
    assertThat(newReport.getData().toString(), is(oldReport.getData().toString()));
    assertThat(oldReport.getName() + " – Copy", is(newReport.getName()));
    assertThat(oldReport.getCollectionId(), is(collectionId));
    assertThat(newReport.getCollectionId(), is(newCollectionId));
  }

  @Test
  public void copyCombinedReportFromCollectionToDifferentCollection() {
    // given
    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    final String report1 = addEmptyProcessReport(collectionId);
    final String report2 = addEmptyProcessReport(collectionId);
    CombinedReportDataDto combined = ProcessReportDataBuilderHelper.createCombinedReportData(report1, report2);

    IdDto id = createAndUpdateCombinedReport(combined, collectionId);

    final String newCollectionId = collectionClient.createNewCollectionForAllDefinitionTypes();

    // when
    IdDto copyId = embeddedOptimizeExtension.getRequestExecutor()
      .buildCopyReportRequest(id.getId(), newCollectionId)
      .execute(IdDto.class, 200);

    // then
    ReportDefinitionDto oldReport = getReport(id.getId());
    ReportDefinitionDto newReport = getReport(copyId.getId());

    assertThat(oldReport.getName() + " – Copy", is(newReport.getName()));
    assertThat(oldReport.getCollectionId(), is(collectionId));
    assertThat(newReport.getCollectionId(), is(newCollectionId));
    final CombinedReportDataDto oldData = (CombinedReportDataDto) oldReport.getData();
    assertThat(oldData.getReportIds().isEmpty(), is(false));
    assertThat(oldData.getReportIds(), containsInAnyOrder(report1, report2));

    final CombinedReportDataDto newData = (CombinedReportDataDto) newReport.getData();
    assertThat(newData.getReportIds().isEmpty(), is(false));
    assertThat(newData.getReportIds(), not(containsInAnyOrder(report1, report2)));

    newData.getReportIds()
      .forEach(newSingleReportId -> {
        final ReportDefinitionDto newSingleReport = getReport(newSingleReportId);
        assertThat(newSingleReport.getCollectionId(), is(newCollectionId));
      });
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateReportWithoutViewById(ReportType reportType) {
    //given
    String id;
    switch (reportType) {
      case PROCESS:
        ProcessReportDataDto processReportDataDto = ProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(RANDOM_KEY)
          .setProcessDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
          .build();
        processReportDataDto.setView(null);
        id = addSingleProcessReportWithDefinition(processReportDataDto);
        break;
      case DECISION:
        DecisionReportDataDto decisionReportDataDto = DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(RANDOM_KEY)
          .setDecisionDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
        decisionReportDataDto.setView(null);
        id = addSingleDecisionReportWithDefinition(decisionReportDataDto);
        break;
      default:
        throw new IllegalStateException("Uncovered reportType: " + reportType);
    }

    // then
    ReportEvaluationException response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .execute(ReportEvaluationException.class, 400);

    // then
    AbstractSharingIT.assertErrorFields(response);
  }

  private Response updateReportRequest(final String id, final ReportType reportType) {
    if (ReportType.PROCESS.equals(reportType)) {
      return embeddedOptimizeExtension
        .getRequestExecutor()
        .buildUpdateSingleProcessReportRequest(id, constructProcessReportWithFakePD())
        .execute();
    } else {
      return embeddedOptimizeExtension
        .getRequestExecutor()
        .buildUpdateSingleDecisionReportRequest(id, constructDecisionReportWithFakeDD())
        .execute();
    }
  }

  private String addEmptyReportToOptimize(final ReportType reportType) {
    return ReportType.PROCESS.equals(reportType)
      ? addEmptyProcessReport()
      : addEmptyDecisionReport();
  }

  private ReportDefinitionDto getReport(String id) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportRequest(id)
      .execute(ReportDefinitionDto.class, 200);
  }

  private String createSingleReport(final ReportType reportType) {
    return createSingleReport(reportType, null);
  }

  private String createSingleReport(final ReportType reportType, final String collectionId) {
    switch (reportType) {
      case PROCESS:
        SingleProcessReportDefinitionDto processDef = constructProcessReportWithFakePD();
        return addSingleProcessReportWithDefinition(processDef.getData(), collectionId);
      case DECISION:
        SingleDecisionReportDefinitionDto decisionDef = constructDecisionReportWithFakeDD();
        return addSingleDecisionReportWithDefinition(decisionDef.getData(), collectionId);
      default:
        throw new IllegalStateException("Unexpected value: " + reportType);
    }
  }

  private IdDto createAndUpdateCombinedReport(final CombinedReportDataDto combined, final String collectionId) {
    CombinedReportDefinitionDto combinedReportDefinitionDto = new CombinedReportDefinitionDto(combined);
    combinedReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdDto.class, 200);
  }

  private String addReportToOptimizeWithDefinition(final ReportType reportType) {
    return addReportToOptimizeWithDefinition(reportType, null);
  }

  private String addReportToOptimizeWithDefinition(final ReportType reportType, final String collectionId) {
    switch (reportType) {
      case PROCESS:
        ProcessReportDataDto processReportDataDto = ProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(RANDOM_KEY)
          .setProcessDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build();
        return addSingleProcessReportWithDefinition(processReportDataDto, collectionId);
      case DECISION:
        DecisionReportDataDto decisionReportDataDto = DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(RANDOM_KEY)
          .setDecisionDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
        return addSingleDecisionReportWithDefinition(decisionReportDataDto, collectionId);
    }
    return null;
  }

  private String addSingleDecisionReportWithDefinition(final DecisionReportDataDto decisionReportDataDto) {
    return addSingleDecisionReportWithDefinition(decisionReportDataDto, null);
  }

  private String addSingleDecisionReportWithDefinition(final DecisionReportDataDto decisionReportDataDto,
                                                       final String collectionId) {
    SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
    singleDecisionReportDefinitionDto.setData(decisionReportDataDto);
    singleDecisionReportDefinitionDto.setId(RANDOM_STRING);
    singleDecisionReportDefinitionDto.setLastModifier(RANDOM_STRING);
    singleDecisionReportDefinitionDto.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleDecisionReportDefinitionDto.setCreated(someDate);
    singleDecisionReportDefinitionDto.setLastModified(someDate);
    singleDecisionReportDefinitionDto.setOwner(RANDOM_STRING);
    singleDecisionReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String addEmptyProcessReport() {
    return addEmptyProcessReport(null);
  }

  private String addEmptyProcessReport(final String collectionId) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String addSingleProcessReportWithDefinition(final ProcessReportDataDto processReportDataDto) {
    return addSingleProcessReportWithDefinition(processReportDataDto, null);
  }

  private String addSingleProcessReportWithDefinition(final ProcessReportDataDto processReportDataDto,
                                                      final String collectionId) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(processReportDataDto);
    singleProcessReportDefinitionDto.setId(RANDOM_STRING);
    singleProcessReportDefinitionDto.setLastModifier(RANDOM_STRING);
    singleProcessReportDefinitionDto.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleProcessReportDefinitionDto.setCreated(someDate);
    singleProcessReportDefinitionDto.setLastModified(someDate);
    singleProcessReportDefinitionDto.setOwner(RANDOM_STRING);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String addEmptyDecisionReport() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private List<ReportDefinitionDto> getAllPrivateReports() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAllPrivateReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, 200);
  }

  @SneakyThrows
  private Response updateReportWithValidXml(final String id, final ReportType reportType) {
    final Response response;
    if (ReportType.PROCESS.equals(reportType)) {
      SingleProcessReportDefinitionDto reportDefinitionDto = getProcessReportDefinitionDtoWithXml(
        PROCESS_DEFINITION_XML_WITH_NAME
      );
      response = embeddedOptimizeExtension
        .getRequestExecutor()
        .buildUpdateSingleProcessReportRequest(id, reportDefinitionDto)
        .execute();
    } else {
      SingleDecisionReportDefinitionDto reportDefinitionDto = getDecisionReportDefinitionDtoWithXml(
        DECISION_DEFINITION_XML
      );
      response = embeddedOptimizeExtension
        .getRequestExecutor()
        .buildUpdateSingleDecisionReportRequest(id, reportDefinitionDto)
        .execute();
    }
    return response;
  }

  private SingleProcessReportDefinitionDto getProcessReportDefinitionDtoWithXml(final String xml) throws IOException {
    SingleProcessReportDefinitionDto reportDefinitionDto = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto data = new ProcessReportDataDto();
    data.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    data.setProcessDefinitionVersion("1");
    data.getConfiguration().setXml(IOUtils.toString(
      getClass().getClassLoader().getResourceAsStream(xml),
      "UTF-8"
    ));
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  private SingleDecisionReportDefinitionDto getDecisionReportDefinitionDtoWithXml(final String xml) throws IOException {
    SingleDecisionReportDefinitionDto reportDefinitionDto = new SingleDecisionReportDefinitionDto();
    DecisionReportDataDto data = new DecisionReportDataDto();
    data.setDecisionDefinitionKey(DECISION_DEFINITION_KEY);
    data.setDecisionDefinitionVersion("1");
    data.getConfiguration().setXml(IOUtils.toString(
      getClass().getClassLoader().getResourceAsStream(xml),
      "UTF-8"
    ));
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  private SingleProcessReportDefinitionDto constructProcessReportWithFakePD() {
    SingleProcessReportDefinitionDto reportDefinitionDto = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto data = new ProcessReportDataDto();
    data.setProcessDefinitionVersion("FAKE");
    data.setProcessDefinitionKey(DEFAULT_DEFINITION_KEY);
    data.setTenantIds(DEFAULT_TENANTS);
    data.getConfiguration().setXml("FAKE");
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  private SingleDecisionReportDefinitionDto constructDecisionReportWithFakeDD() {
    SingleDecisionReportDefinitionDto reportDefinitionDto = new SingleDecisionReportDefinitionDto();
    DecisionReportDataDto data = new DecisionReportDataDto();
    data.setDecisionDefinitionVersion("FAKE");
    data.setDecisionDefinitionKey(DEFAULT_DEFINITION_KEY);
    data.setTenantIds(DEFAULT_TENANTS);
    data.getConfiguration().setXml("FAKE");
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }
}

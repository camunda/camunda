/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportType;
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
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;


@RunWith(JUnitParamsRunner.class)
public class ReportRestServiceIT {
  private static final String PROCESS_DEFINITION_XML_WITH_NAME = "bpmn/simple_withName.bpmn";
  private static final String PROCESS_DEFINITION_XML_WO_NAME = "bpmn/simple_woName.bpmn";
  private static final String DECISION_DEFINITION_XML = "dmn/invoiceBusinessDecision_withName_and_versionTag.xml";
  private static final String DECISION_DEFINITION_XML_WO_NAME = "dmn/invoiceBusinessDecision_woName.xml";
  private static final String PROCESS_DEFINITION_KEY = "simple";
  private static final String DECISION_DEFINITION_KEY = "invoiceClassification";

  private static Object[] processAndDecisionReportType() {
    return new Object[]{ReportType.PROCESS, ReportType.DECISION};
  }

  private static final String RANDOM_KEY = "someRandomKey";
  private static final String RANDOM_VERSION = "someRandomVersion";
  private static final String RANDOM_STRING = "something";

  public EngineIntegrationRule engineIntegrationRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineIntegrationRule).around(embeddedOptimizeRule);

  @Test
  public void createNewReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  @Parameters(method = "processAndDecisionReportType")
  public void createNewSingleReport(final ReportType reportType) {
    // when
    String id = addEmptyReportToOptimize(reportType);
    // then
    assertThat(id, is(notNullValue()));
  }

  @Test
  public void createNewCombinedReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
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
    IdDto idDto = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest()
      .execute(IdDto.class, 200);
    // then
    assertThat(idDto, is(notNullValue()));
  }

  @Test
  public void updateReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest("nonExistingId", constructProcessReportWithFakePD())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(404));
  }

  @Test
  @Parameters(method = "processAndDecisionReportType")
  public void updateReport(final ReportType reportType) {
    //given
    String id = addEmptyReportToOptimize(reportType);

    // when
    Response response = updateReportRequest(id, reportType);

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  @Parameters(method = "processAndDecisionReportType")
  public void updateReportWithXml(final ReportType reportType) {
    //given
    String id = addEmptyReportToOptimize(reportType);

    // when
    final Response response;
    response = updateReportWithValidXml(id, reportType);

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getStoredReportsWithoutAuthentication() {
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
  public void getStoredReportsWithNameFromXml() {
    //given
    String idProcessReport = addEmptyProcessReportToOptimize();
    updateReportWithValidXml(idProcessReport, ReportType.PROCESS);
    String idDecisionReport = addEmptyDecisionReportToOptimize();
    updateReportWithValidXml(idDecisionReport, ReportType.DECISION);

    // when
    List<ReportDefinitionDto> reports = getAllReports();

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
    final String idProcessReport = addEmptyProcessReportToOptimize();
    final SingleProcessReportDefinitionDto processReportDefinitionDto = getProcessReportDefinitionDtoWithXml(
      PROCESS_DEFINITION_XML_WO_NAME
    );
    embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(idProcessReport, processReportDefinitionDto)
      .execute();

    final String idDecisionReport = addEmptyDecisionReportToOptimize();
    final SingleDecisionReportDefinitionDto decisionReportDefinitionDto = getDecisionReportDefinitionDtoWithXml(
      DECISION_DEFINITION_XML_WO_NAME
    );
    embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleDecisionReportRequest(idDecisionReport, decisionReportDefinitionDto)
      .execute();

    // when
    List<ReportDefinitionDto> reports = getAllReports();

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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetReportRequest("asdf")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  @Parameters(method = "processAndDecisionReportType")
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
    String response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetReportRequest("fooId")
      .execute(String.class, 404);

    // then the status code is okay
    assertThat(response.contains("Report does not exist."), is(true));
  }

  @Test
  public void deleteReportWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildDeleteReportRequest("1124")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  @Parameters(method = "processAndDecisionReportType")
  public void deleteReport(final ReportType reportType) {
    //given
    String id = addEmptyReportToOptimize(reportType);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteReportRequest(id)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllReports().size(), is(0));
  }

  @Test
  public void deleteNonExistingReport() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteReportRequest("nonExistingId")
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void evaluateReportByIdWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEvaluateSavedReportRequest("123")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  @Parameters(method = "processAndDecisionReportType")
  public void evaluateReportById(ReportType reportType) {
    //given
    final String id;
    switch (reportType) {
      case PROCESS:
        ProcessReportDataDto reportData = ProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(RANDOM_KEY)
          .setProcessDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build();
        id = createAndStoreDefaultProcessReportDefinition(reportData);
        break;
      case DECISION:
        DecisionReportDataDto decisionReportData = DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(RANDOM_KEY)
          .setDecisionDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
        id = createAndStoreDefaultDecisionReportDefinition(decisionReportData);
        break;
      default:
        throw new IllegalStateException("Uncovered type: " + reportType);
    }

    // then
    Response response = embeddedOptimizeRule
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
    String id = createAndStoreDefaultProcessReportDefinition(reportData);

    // then
    ReportEvaluationException response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .execute(ReportEvaluationException.class, 500);

    // then
    AbstractSharingIT.assertErrorFields(response);
  }

  @Test
  public void evaluateUnsavedReportWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEvaluateCombinedUnsavedReportRequest(null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  @Parameters(method = "processAndDecisionReportType")
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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportDataDto)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void evaluateUnsavedCombinedReportWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeRule
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
    CombinedReportDataDto combinedReport = ProcessReportDataBuilderHelper.createCombinedReport();
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(combinedReport)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void nullReportsAreHandledAsEmptyList() {
    // then
    CombinedReportDataDto combinedReport = ProcessReportDataBuilderHelper.createCombinedReport();
    combinedReport.setReports(null);

    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(combinedReport)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void copySingleReport() {
    SingleProcessReportDefinitionDto single = constructProcessReportWithFakePD();
    String id = createAndStoreDefaultProcessReportDefinition(single.getData());
    engineIntegrationRule.addUser("john", "john");
    engineIntegrationRule.grantAllAuthorizations("john");

    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyReportRequest(id)
      .withUserAuthentication("john", "john")
      .execute(IdDto.class, 200);

    ReportDefinitionDto oldReport = getReport(id);
    ReportDefinitionDto report = getReport(copyId.getId());
    assertThat(report.getData().toString(), is(oldReport.getData().toString()));
    assertThat(oldReport.getName() + " – Copy", is(report.getName()));
    assertThat(report.getLastModifier(), is("john"));
  }

  @Test
  public void copyCombinedReport() {
    CombinedReportDataDto combined = ProcessReportDataBuilderHelper.createCombinedReport();

    engineIntegrationRule.addUser("john", "john");
    engineIntegrationRule.grantAllAuthorizations("john");

    IdDto id = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest()
      .execute(IdDto.class, 200);

    embeddedOptimizeRule.getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(id.getId(), new CombinedReportDefinitionDto(combined), true)
      .execute();

    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyReportRequest(id.getId())
      .withUserAuthentication("john", "john")
      .execute(IdDto.class, 200);

    ReportDefinitionDto oldReport = getReport(id.getId());
    ReportDefinitionDto report = getReport(copyId.getId());
    assertThat(report.getData().toString(), is(oldReport.getData().toString()));
    assertThat(oldReport.getName() + " – Copy", is(report.getName()));
    assertThat(report.getLastModifier(), is("john"));
  }

  @Test
  public void copyDecisionReport() {
    DecisionReportDataDto decisionReportData = DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(RANDOM_KEY)
      .setDecisionDefinitionVersion(RANDOM_VERSION)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
    String id = createAndStoreDefaultDecisionReportDefinition(decisionReportData);

    engineIntegrationRule.addUser("john", "john");
    engineIntegrationRule.grantAllAuthorizations("john");

    IdDto copyId = embeddedOptimizeRule.getRequestExecutor()
      .buildCopyReportRequest(id)
      .withUserAuthentication("john", "john")
      .execute(IdDto.class, 200);

    ReportDefinitionDto oldReport = getReport(id);
    ReportDefinitionDto report = getReport(copyId.getId());
    assertThat(report.getData().toString(), is(oldReport.getData().toString()));
    assertThat(oldReport.getName() + " – Copy", is(report.getName()));
    assertThat(report.getLastModifier(), is("john"));
  }

  @Test
  @Parameters(method = "processAndDecisionReportType")
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
        id = createAndStoreDefaultProcessReportDefinition(processReportDataDto);
        break;
      case DECISION:
        DecisionReportDataDto decisionReportDataDto = DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(RANDOM_KEY)
          .setDecisionDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
        decisionReportDataDto.setView(null);
        id = createAndStoreDefaultDecisionReportDefinition(decisionReportDataDto);
        break;
      default:
        throw new IllegalStateException("Uncovered reportType: " + reportType);
    }

    // then
    ReportEvaluationException response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .execute(ReportEvaluationException.class, 500);

    // then
    AbstractSharingIT.assertErrorFields(response);
  }

  private String addEmptyReportToOptimize(final ReportType reportType) {
    return ReportType.PROCESS.equals(reportType)
      ? addEmptyProcessReportToOptimize()
      : addEmptyDecisionReportToOptimize();
  }

  private ReportDefinitionDto getReport(String id) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetReportRequest(id)
      .execute(ReportDefinitionDto.class, 200);
  }

  private String createAndStoreDefaultProcessReportDefinition(ProcessReportDataDto processReportDataDto) {
    String id = addEmptyProcessReportToOptimize();
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(processReportDataDto);
    report.setId(id);
    report.setLastModifier(RANDOM_STRING);
    report.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner(RANDOM_STRING);
    updateSingleProcessReport(id, report);
    return id;
  }

  private String createAndStoreDefaultDecisionReportDefinition(DecisionReportDataDto decisionReportDataDto) {
    String id = addEmptyDecisionReportToOptimize();
    SingleDecisionReportDefinitionDto report = new SingleDecisionReportDefinitionDto();
    report.setData(decisionReportDataDto);
    report.setId(id);
    report.setLastModifier(RANDOM_STRING);
    report.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner(RANDOM_STRING);
    updateSingleDecisionReport(id, report);
    return id;
  }

  private void updateSingleProcessReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private void updateSingleDecisionReport(String id, SingleDecisionReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleDecisionReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private String addEmptyProcessReportToOptimize() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String addEmptyDecisionReportToOptimize() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private List<ReportDefinitionDto> getAllReports() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllReportsRequest()
      .executeAndReturnList(ReportDefinitionDto.class, 200);
  }

  @SneakyThrows
  private Response updateReportWithValidXml(final String id, final ReportType reportType) {
    final Response response;
    if (ReportType.PROCESS.equals(reportType)) {
      SingleProcessReportDefinitionDto reportDefinitionDto = getProcessReportDefinitionDtoWithXml(
        PROCESS_DEFINITION_XML_WITH_NAME
      );
      response = embeddedOptimizeRule
        .getRequestExecutor()
        .buildUpdateSingleProcessReportRequest(id, reportDefinitionDto)
        .execute();
    } else {
      SingleDecisionReportDefinitionDto reportDefinitionDto = getDecisionReportDefinitionDtoWithXml(
        DECISION_DEFINITION_XML
      );
      response = embeddedOptimizeRule
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


  private Response updateReportRequest(final String id, final ReportType reportType) {
    if (ReportType.PROCESS.equals(reportType)) {
      return embeddedOptimizeRule
        .getRequestExecutor()
        .buildUpdateSingleProcessReportRequest(id, constructProcessReportWithFakePD())
        .execute();
    } else {
      return embeddedOptimizeRule
        .getRequestExecutor()
        .buildUpdateSingleDecisionReportRequest(id, constructDecisionReportWithFakeDD())
        .execute();
    }
  }

  private SingleProcessReportDefinitionDto constructProcessReportWithFakePD() {
    SingleProcessReportDefinitionDto reportDefinitionDto = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto data = new ProcessReportDataDto();
    data.setProcessDefinitionVersion("FAKE");
    data.setProcessDefinitionKey("FAKE");
    data.getConfiguration().setXml("FAKE");
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  private SingleDecisionReportDefinitionDto constructDecisionReportWithFakeDD() {
    SingleDecisionReportDefinitionDto reportDefinitionDto = new SingleDecisionReportDefinitionDto();
    DecisionReportDataDto data = new DecisionReportDataDto();
    data.setDecisionDefinitionVersion("FAKE");
    data.setDecisionDefinitionKey("FAKE");
    data.getConfiguration().setXml("FAKE");
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.service.sharing.AbstractSharingIT;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static javax.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;

public class ReportEvaluationRestServiceIT extends AbstractReportRestServiceIT {

  @Test
  public void evaluateReportByIdWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEvaluateSavedReportRequest("123")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateReportById(ReportType reportType) {
    //given
    final String id = addReportToOptimizeWithDefinitionAndRandomXml(reportType);

    // then
    reportClient.evaluateRawReportById(id);
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateReportById_usingGetEndpoint(ReportType reportType) {
    //given
    final String id = addReportToOptimizeWithDefinitionAndRandomXml(reportType);

    // then
    reportClient.evaluateRawReportByIdGETRequest(id);
  }

  @Test
  public void evaluateReportById_additionalFiltersAreApplied() {
    // given
    BpmnModelInstance processModel = createBpmnModel();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModel(processModel);
    final String reportId = createOptimizeReportForProcess(processModel, processInstanceEngineDto);

    // then the instance is part of evaluation result
    assertThat(reportClient.evaluateRawReportById(reportId).getResult().getInstanceCount()).isEqualTo(1);

    // then instance is no longer part of evaluated result when filter used for future start date
    assertInstanceCountForReportEvaluationWithFilter(
      reportId,
      createFixedDateFilter(OffsetDateTime.now().plusSeconds(1), null),
      0L
    );

    // then instance is part of evaluated result when filter used for future end date
    assertInstanceCountForReportEvaluationWithFilter(
      reportId,
      createFixedDateFilter(null, OffsetDateTime.now().plusSeconds(1)),
      1L
    );

    // then instance is part of evaluated result when filter used for running instances
    assertInstanceCountForReportEvaluationWithFilter(reportId, runningInstancesOnlyFilter(), 1L);

    // then instance is part of evaluated result when filter used for completed instances
    assertInstanceCountForReportEvaluationWithFilter(reportId, completedInstancesOnlyFilter(), 0L);
  }

  @Test
  public void evaluateReportByIdWithAdditionalFilters_filtersCombinedWithAlreadyExistingFiltersOnReport() {
    // given a report with a running instances filter
    BpmnModelInstance processModel = createBpmnModel();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModel(processModel);
    final String reportId = createOptimizeReportForProcessUsingFilters(
      processModel,
      processInstanceEngineDto,
      runningInstancesOnlyFilter()
    );

    // then the instance is part of evaluation result
    assertThat(reportClient.evaluateRawReportById(reportId).getResult().getInstanceCount()).isEqualTo(1);

    // then instance is no longer part of evaluated result when additional completed filter added
    assertInstanceCountForReportEvaluationWithFilter(
      reportId,
      completedInstancesOnlyFilter(),
      0L
    );
  }

  @Test
  public void evaluateReportByIdWithAdditionalFilters_filtersExistOnReportThatAreSameAsAdditional() {
    // given
    BpmnModelInstance processModel = createBpmnModel();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModel(processModel);
    final String reportId = createOptimizeReportForProcessUsingFilters(
      processModel,
      processInstanceEngineDto,
      runningInstancesOnlyFilter()
    );

    // then the instance is part of evaluation result
    assertThat(reportClient.evaluateRawReportById(reportId).getResult().getInstanceCount()).isEqualTo(1);

    // then instance is still part of evaluated result when identical filter added
    assertInstanceCountForReportEvaluationWithFilter(
      reportId,
      runningInstancesOnlyFilter(),
      1L
    );
  }

  @Test
  public void evaluateReportByIdWithAdditionalFilters_filtersIgnoredIfDecisionReport() {
    // given
    final DmnModelInstance decisionModel = createSimpleDmnModel("someKey");
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      deployAndStartDecisionInstanceForModel(decisionModel);

    final String reportId = createOptimizeReportForDecisionDefinition(decisionModel, decisionDefinitionEngineDto);

    // then the instance is part of evaluation result when evaluated
    assertThat(reportClient.evaluateRawReportById(reportId).getResult().getInstanceCount()).isEqualTo(1);

    // then the instance is still part of evaluation result when evaluated with future start date filter
    assertInstanceCountForReportEvaluationWithFilter(
      reportId,
      createFixedDateFilter(OffsetDateTime.now().plusSeconds(1), null),
      1L
    );
  }

  private String createOptimizeReportForDecisionDefinition(final DmnModelInstance decisionModel,
                                                           final DecisionDefinitionEngineDto decisionDefinitionEngineDto) {
    DecisionReportDataDto decisionReportDataDto = DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(decisionDefinitionEngineDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionEngineDto.getVersionAsString())
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
    decisionReportDataDto.getConfiguration().setXml(Dmn.convertToString(decisionModel));
    return addSingleDecisionReportWithDefinition(decisionReportDataDto, null);
  }

  private DecisionDefinitionEngineDto deployAndStartDecisionInstanceForModel(final DmnModelInstance decisionModel) {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      engineIntegrationExtension.deployAndStartDecisionDefinition(
        decisionModel);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return decisionDefinitionEngineDto;
  }

  private List<ProcessFilterDto<?>> createFixedDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedStartDate().start(startDate).end(endDate).add().buildList();
  }

  @Test
  public void evaluateInvalidReportById() {
    //given
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
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
      .execute(ReportEvaluationException.class, Response.Status.BAD_REQUEST.getStatusCode());

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
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateUnsavedReport(ReportType reportType) {
    //given
    final SingleReportDataDto reportDataDto;
    switch (reportType) {
      case PROCESS:
        reportDataDto = TemplatedProcessReportDataBuilder
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

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportDataDto);

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateUnsavedReportWithoutVersionsAndTenantsDoesNotFail(ReportType reportType) {
    //given
    final SingleReportDataDto reportDataDto = createReportWithoutVersionsAndTenants(reportType);

    // then
    Response response = reportClient.evaluateReportAndReturnResponse(reportDataDto);

    // then status is OK
    assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
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
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void evaluateCombinedUnsavedReport() {
    // then
    CombinedReportDataDto combinedReport = ProcessReportDataBuilderHelper.createCombinedReportData();

    reportClient.evaluateUnsavedCombined(combinedReport);
  }

  @Test
  public void nullReportsAreHandledAsEmptyList() {
    // then
    CombinedReportDataDto combinedReport = ProcessReportDataBuilderHelper.createCombinedReportData();
    combinedReport.setReports(null);

    reportClient.evaluateUnsavedCombined(combinedReport);
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateReportWithoutViewById(ReportType reportType) {
    //given
    String id;
    switch (reportType) {
      case PROCESS:
        ProcessReportDataDto processReportDataDto = TemplatedProcessReportDataBuilder
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
        id = addSingleDecisionReportWithDefinition(decisionReportDataDto, null);
        break;
      default:
        throw new IllegalStateException("Uncovered reportType: " + reportType);
    }

    // then
    ReportEvaluationException response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .execute(ReportEvaluationException.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    AbstractSharingIT.assertErrorFields(response);
  }

  private SingleReportDataDto createReportWithoutVersionsAndTenants(final ReportType reportType) {
    switch (reportType) {
      case PROCESS:
        return TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(RANDOM_KEY)
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build();
      case DECISION:
        return DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(RANDOM_KEY)
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
      default:
        throw new IllegalStateException("Uncovered type: " + reportType);
    }
  }

  private String createOptimizeReportForProcessUsingFilters(final BpmnModelInstance processModel,
                                                            final ProcessInstanceEngineDto processInstanceEngineDto,
                                                            final List<ProcessFilterDto<?>> filters) {
    final TemplatedProcessReportDataBuilder reportBuilder = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.RAW_DATA);
    Optional.ofNullable(filters).ifPresent(reportBuilder::setFilter);
    final ProcessReportDataDto processReportDataDto = reportBuilder.build();
    processReportDataDto.getConfiguration().setXml(toXml(processModel));
    return addSingleProcessReportWithDefinition(processReportDataDto);
  }

  private String createOptimizeReportForProcess(final BpmnModelInstance processModel,
                                                final ProcessInstanceEngineDto processInstanceEngineDto) {
    return createOptimizeReportForProcessUsingFilters(processModel, processInstanceEngineDto, null);
  }

  private ProcessInstanceEngineDto deployAndStartInstanceForModel(final BpmnModelInstance processModel) {
    final ProcessInstanceEngineDto instance = engineIntegrationExtension.deployAndStartProcess(processModel);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return instance;
  }

  private String toXml(final BpmnModelInstance processModel) {
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, processModel);
    return new String(xmlOutput.toByteArray(), StandardCharsets.UTF_8);
  }

  private BpmnModelInstance createBpmnModel() {
    return Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
  }

  private void assertInstanceCountForReportEvaluationWithFilter(final String reportId,
                                                                final List<ProcessFilterDto<?>> filters,
                                                                final Long expectedInstanceCount) {
    ReportEvaluationFilterDto filterDto = new ReportEvaluationFilterDto();
    filterDto.setFilter(filters);
    assertThat(reportClient.evaluateRawReportByIdWithFilters(reportId, filterDto)
                 .getResult().getInstanceCount())
      .isEqualTo(expectedInstanceCount);
  }

  private List<ProcessFilterDto<?>> runningInstancesOnlyFilter() {
    return ProcessFilterBuilder.filter().runningInstancesOnly().add().buildList();
  }

  private List<ProcessFilterDto<?>> completedInstancesOnlyFilter() {
    return ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList();
  }

}

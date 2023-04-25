/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.util.ProcessReportDataBuilderHelper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportType.DECISION;
import static org.camunda.optimize.dto.optimize.ReportType.PROCESS;

public class ReportDescriptionsRestServiceIT extends AbstractReportRestServiceIT {

  @ParameterizedTest
  @MethodSource("reportTypeAndInvalidDescription")
  public void createNewSingleReportWithInvalidDescription(final ReportType reportType,
                                                          final String description) {
    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier("1");

    // when
    Response response;
    switch (reportType) {
      case PROCESS:
        final ProcessReportDataDto processReportData = ProcessReportDataDto.builder().definitions(definitions).build();
        final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
          createSingleProcessReportDefinitionRequestDto(processReportData, description, null);
        response = embeddedOptimizeExtension.getRequestExecutor()
          .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
          .execute();
        break;
      case DECISION:
        final DecisionReportDataDto decisionReportData = DecisionReportDataDto.builder().definitions(definitions).build();
        SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
          createSingleDecisionReportDefinitionRequestDto(decisionReportData, description, null);
        response = embeddedOptimizeExtension.getRequestExecutor()
          .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
          .execute();
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported report type: " + reportType);
    }

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("reportTypeAndValidDescription")
  public void createNewSingleReportWithValidDescription(final ReportType reportType,
                                                        final String description) {
    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier("1");

    // when
    String savedDescription;
    switch (reportType) {
      case PROCESS:
        final ProcessReportDataDto processReportData = ProcessReportDataDto.builder().definitions(definitions).build();
        final String processReportId = addSingleProcessReportWithDefinition(processReportData, description, null);
        savedDescription = reportClient.getReportById(processReportId).getDescription();
        break;
      case DECISION:
        final DecisionReportDataDto decisionReportData = DecisionReportDataDto.builder().definitions(definitions).build();
        final String decisionReportId = addSingleDecisionReportWithDefinition(decisionReportData, description, null);
        savedDescription = reportClient.getReportById(decisionReportId).getDescription();
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported report type: " + reportType);
    }

    // then
    assertThat(savedDescription).isEqualTo(description);
  }

  @ParameterizedTest
  @MethodSource("invalidDescription")
  public void createNewCombinedReportWithInvalidDescription(final String description) {
    // given
    CombinedReportDefinitionRequestDto combinedReportDefinitionDto = new CombinedReportDefinitionRequestDto();
    combinedReportDefinitionDto.setData(ProcessReportDataBuilderHelper.createCombinedReportData());
    combinedReportDefinitionDto.setDescription(description);

    // when
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("validDescription")
  public void createNewCombinedReportWithValidDescription(final String description) {
    // given
    CombinedReportDefinitionRequestDto combinedReportDefinitionDto = new CombinedReportDefinitionRequestDto();
    combinedReportDefinitionDto.setData(ProcessReportDataBuilderHelper.createCombinedReportData());
    combinedReportDefinitionDto.setDescription(description);

    // when
    final String reportId = reportClient.createNewCombinedReport(combinedReportDefinitionDto);
    final ReportDefinitionDto<?> savedReport = reportClient.getReportById(reportId);

    // then
    assertThat(savedReport.getDescription()).isEqualTo(description);
  }

  @ParameterizedTest
  @MethodSource("invalidDescription")
  public void updateSingleProcessReportWithInvalidDescription(final String description) {
    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier("1");
    final ProcessReportDataDto processReportData = ProcessReportDataDto.builder().definitions(definitions).build();
    final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      createSingleProcessReportDefinitionRequestDto(processReportData, getValidDescription(), null);
    final String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when
    final SingleProcessReportDefinitionRequestDto processReportDto = reportClient.getSingleProcessReportById(reportId);
    processReportDto.setDescription(description);
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(reportId, processReportDto, true)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("invalidDescription")
  public void updateSingleDecisionReportWithInvalidDescription(final String description) {
    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier("1");
    final DecisionReportDataDto decisionReportData = DecisionReportDataDto.builder().definitions(definitions).build();
    SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
      createSingleDecisionReportDefinitionRequestDto(decisionReportData, getValidDescription(), null);
    final String reportId = reportClient.createSingleDecisionReport(singleDecisionReportDefinitionDto);

    // when
    final SingleDecisionReportDefinitionRequestDto decisionReportDto = reportClient.getSingleDecisionReportById(reportId);
    decisionReportDto.setDescription(description);
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateSingleDecisionReportRequest(reportId, decisionReportDto, true)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("invalidDescription")
  public void updateCombinedReportWithInvalidDescription(final String description) {
    // given
    CombinedReportDefinitionRequestDto combinedReportDefinitionDto = new CombinedReportDefinitionRequestDto();
    combinedReportDefinitionDto.setData(ProcessReportDataBuilderHelper.createCombinedReportData());
    final String originalDescription = getValidDescription();
    combinedReportDefinitionDto.setDescription(originalDescription);
    final String reportId = reportClient.createNewCombinedReport(combinedReportDefinitionDto);
    final CombinedReportDefinitionRequestDto savedReport = reportClient.getCombinedProcessReportById(reportId);
    assertThat(savedReport.getDescription()).isEqualTo(originalDescription);

    // when
    savedReport.setDescription(description);
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(reportId, savedReport)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("validDescription")
  public void updateSingleProcessReportWithValidDescription(final String description) {
    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier("1");
    final ProcessReportDataDto processReportData = ProcessReportDataDto.builder().definitions(definitions).build();
    final String originalDescription = getValidDescription();
    final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      createSingleProcessReportDefinitionRequestDto(processReportData, originalDescription, null);
    final String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
    final SingleProcessReportDefinitionRequestDto savedReport = reportClient.getSingleProcessReportById(reportId);
    assertThat(savedReport.getDescription()).isEqualTo(originalDescription);

    // when
    savedReport.setDescription(description);
    reportClient.updateSingleProcessReport(reportId, savedReport, true);

    // then
    assertThat(reportClient.getSingleProcessReportById(reportId).getDescription()).isEqualTo(description);
  }

  @ParameterizedTest
  @MethodSource("validDescription")
  public void updateSingleDecisionReportWithValidDescription(final String description) {
    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier("1");
    final DecisionReportDataDto decisionReportData = DecisionReportDataDto.builder().definitions(definitions).build();
    final String originalDescription = getValidDescription();
    final SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
      createSingleDecisionReportDefinitionRequestDto(decisionReportData, originalDescription, null);
    final String reportId = reportClient.createSingleDecisionReport(singleDecisionReportDefinitionDto);
    final SingleDecisionReportDefinitionRequestDto savedReport = reportClient.getSingleDecisionReportById(reportId);
    assertThat(savedReport.getDescription()).isEqualTo(originalDescription);

    // when
    savedReport.setDescription(description);
    reportClient.updateDecisionReport(reportId, savedReport);

    // then
    assertThat(reportClient.getSingleDecisionReportById(reportId).getDescription()).isEqualTo(description);
  }

  @ParameterizedTest
  @MethodSource("validDescription")
  public void updateCombinedReportWithValidDescription(final String description) {
    // given
    CombinedReportDefinitionRequestDto combinedReportDefinitionDto = new CombinedReportDefinitionRequestDto();
    combinedReportDefinitionDto.setData(ProcessReportDataBuilderHelper.createCombinedReportData());
    final String originalDescription = getValidDescription();
    combinedReportDefinitionDto.setDescription(originalDescription);
    final String reportId = reportClient.createNewCombinedReport(combinedReportDefinitionDto);
    final CombinedReportDefinitionRequestDto savedReport = reportClient.getCombinedProcessReportById(reportId);
    assertThat(savedReport.getDescription()).isEqualTo(originalDescription);

    // when
    savedReport.setDescription(description);
    reportClient.updateCombinedReport(reportId, savedReport);

    // then
    assertThat(reportClient.getCombinedProcessReportById(reportId).getDescription()).isEqualTo(description);
  }

  private static Stream<Arguments> reportTypeAndInvalidDescription() {
    return invalidDescription().flatMap(oneDescriptionPerReportType());
  }

  private static Stream<Arguments> reportTypeAndValidDescription() {
    return validDescription().flatMap(oneDescriptionPerReportType());
  }

  private static Stream<String> invalidDescription() {
    return Stream.of("", RandomStringUtils.randomAlphabetic(450));
  }

  private static Stream<String> validDescription() {
    return Stream.of(null, RandomStringUtils.randomAlphabetic(300));
  }

  private static Function<String, Stream<? extends Arguments>> oneDescriptionPerReportType() {
    return description -> Stream.of(
      Arguments.of(PROCESS, description),
      Arguments.of(DECISION, description)
    );
  }

  private static String getValidDescription() {
    return RandomStringUtils.randomAlphabetic(100);
  }

}

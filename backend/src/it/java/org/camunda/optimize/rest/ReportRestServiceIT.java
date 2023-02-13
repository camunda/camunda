/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.util.ProcessReportDataBuilderHelper;
import org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;
import static org.camunda.optimize.dto.optimize.ReportType.DECISION;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.PERCENTILE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.SUM;
import static org.camunda.optimize.rest.RestTestUtil.getOffsetDiffInHours;
import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ALERT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.REPORT_SHARE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
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

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void createNewSingleReportWithMultipleDefinitions(final ReportType reportType) {
    // given
    final List<ReportDataDefinitionDto> definitions = Arrays.asList(
      new ReportDataDefinitionDto(
        "1", RANDOM_KEY, RANDOM_STRING, RANDOM_STRING, Collections.singletonList(ALL_VERSIONS), DEFAULT_TENANT_IDS
      ),
      new ReportDataDefinitionDto(
        "2",
        RANDOM_KEY + 2,
        RANDOM_STRING + 2,
        RANDOM_STRING,
        Collections.singletonList(ALL_VERSIONS),
        DEFAULT_TENANT_IDS
      )
    );

    // when
    final String id;
    switch (reportType) {
      case PROCESS:
        id = addSingleProcessReportWithDefinition(ProcessReportDataDto.builder().definitions(definitions).build());
        break;
      case DECISION:
        id = addSingleDecisionReportWithDefinition(DecisionReportDataDto.builder().definitions(definitions).build());
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported report type: " + reportType);
    }

    // then
    assertThat(id).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void createNewSingleReportWithDefinitionWithoutIdentifierFails(final ReportType reportType) {
    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier(null);

    // when

    Response response;
    switch (reportType) {
      case PROCESS:
        response = embeddedOptimizeExtension
          .getRequestExecutor()
          .buildCreateSingleProcessReportRequest(new SingleProcessReportDefinitionRequestDto(
            ProcessReportDataDto.builder().definitions(definitions).build()
          ))
          .execute();
        break;
      case DECISION:
        response = embeddedOptimizeExtension
          .getRequestExecutor()
          .buildCreateSingleDecisionReportRequest(new SingleDecisionReportDefinitionRequestDto(
            DecisionReportDataDto.builder().definitions(definitions).build()
          ))
          .execute();
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported report type: " + reportType);
    }

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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

  private static Stream<AggregationDto> invalidAggregationTypes() {
    return Stream.of(
      new AggregationDto(PERCENTILE, 101.),
      new AggregationDto(PERCENTILE, -1.),
      new AggregationDto(PERCENTILE, null),
      new AggregationDto(AVERAGE, 5.),
      new AggregationDto(MAX, 5.),
      new AggregationDto(MIN, 5.),
      new AggregationDto(SUM, 5.)
    );
  }

  @ParameterizedTest
  @MethodSource("invalidAggregationTypes")
  public void createNewSingleProcessReportWithFiltersAppliedToDefaultsToAll(final AggregationDto aggregationDto) {
    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier("1");
    final ProcessReportDataDto reportDataDto = ProcessReportDataDto.builder().definitions(definitions).build();
    reportDataDto.getConfiguration().setAggregationTypes(aggregationDto);
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportDataDto);
    singleProcessReportDefinitionDto.setId(RANDOM_STRING);
    singleProcessReportDefinitionDto.setLastModifier(RANDOM_STRING);
    singleProcessReportDefinitionDto.setName(RANDOM_STRING);
    singleProcessReportDefinitionDto.setCreated(OffsetDateTime.now());
    singleProcessReportDefinitionDto.setLastModified(OffsetDateTime.now());
    singleProcessReportDefinitionDto.setOwner(RANDOM_STRING);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createNewSingleProcessReportWithInvalidAggregationValueIsRejected() {
    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier("1");
    final ProcessReportDataDto reportDataDto = ProcessReportDataDto.builder().definitions(definitions).build();
    reportDataDto.setFilter(ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList());

    // when
    final String id = addSingleProcessReportWithDefinition(reportDataDto);
    final SingleProcessReportDefinitionRequestDto reportById = reportClient.getSingleProcessReportById(id);

    // then
    assertThat(reportById.getData().getFilter())
      .singleElement()
      .satisfies(filterDto -> assertThat(filterDto.getAppliedTo()).containsExactly(APPLIED_TO_ALL_DEFINITIONS));
  }

  @Test
  public void createNewSingleProcessReportWithFiltersAppliedToSetToProvidedDefinition() {
    // given
    final String definitionIdentifier = "1";
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier(definitionIdentifier);
    final ProcessReportDataDto reportDataDto = ProcessReportDataDto.builder().definitions(definitions).build();
    reportDataDto.setFilter(
      ProcessFilterBuilder.filter().completedInstancesOnly().appliedTo(definitionIdentifier).add().buildList()
    );

    // when
    final String id = addSingleProcessReportWithDefinition(reportDataDto);
    final SingleProcessReportDefinitionRequestDto reportById = reportClient.getSingleProcessReportById(id);

    // then
    assertThat(reportById.getData().getFilter())
      .singleElement()
      .satisfies(filterDto -> assertThat(filterDto.getAppliedTo()).containsExactly(definitionIdentifier));
  }

  @Test
  public void createNewSingleProcessReportWithFiltersAppliedToSetToInvalidIdFails() {
    // given
    final String definitionIdentifier = "1";
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier(definitionIdentifier);
    final ProcessReportDataDto reportDataDto = ProcessReportDataDto.builder().definitions(definitions).build();
    reportDataDto.setFilter(
      ProcessFilterBuilder.filter().completedInstancesOnly().appliedTo("invalid").add().buildList()
    );

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCreateSingleProcessReportRequest(new SingleProcessReportDefinitionRequestDto(reportDataDto))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createNewSingleProcessReportWithEmptyFiltersAppliedToFails() {
    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier("1");
    final ProcessReportDataDto reportDataDto = ProcessReportDataDto.builder().definitions(definitions).build();
    reportDataDto.setFilter(
      ProcessFilterBuilder.filter().completedInstancesOnly().appliedTo(Collections.emptyList()).add().buildList()
    );

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCreateSingleProcessReportRequest(new SingleProcessReportDefinitionRequestDto(reportDataDto))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createNewSingleDecisionReportWithFiltersAppliedToDefaultsToAll() {
    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier("1");
    final DecisionReportDataDto reportDataDto = DecisionReportDataDto.builder().definitions(definitions).build();
    reportDataDto.getFilter()
      .add(DecisionFilterUtilHelper.createRelativeEvaluationDateFilter(1L, DateUnit.SECONDS));

    // when
    final String id = addSingleDecisionReportWithDefinition(reportDataDto);
    final SingleDecisionReportDefinitionRequestDto reportById = reportClient.getSingleDecisionReportById(id);

    // then
    assertThat(reportById.getData().getFilter())
      .singleElement()
      .satisfies(filterDto -> assertThat(filterDto.getAppliedTo()).containsExactly(APPLIED_TO_ALL_DEFINITIONS));
  }

  @Test
  public void createNewSingleDecisionReportWithFiltersAppliedToSetToProvidedDefinition() {
    // given
    final String definitionIdentifier = "1";
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier(definitionIdentifier);
    final DecisionReportDataDto reportDataDto = DecisionReportDataDto.builder().definitions(definitions).build();
    final EvaluationDateFilterDto filterDto =
      DecisionFilterUtilHelper.createRelativeEvaluationDateFilter(1L, DateUnit.SECONDS);
    filterDto.setAppliedTo(List.of(definitionIdentifier));
    reportDataDto.getFilter().add(filterDto);

    // when
    final String id = addSingleDecisionReportWithDefinition(reportDataDto);
    final SingleDecisionReportDefinitionRequestDto reportById = reportClient.getSingleDecisionReportById(id);

    // then
    assertThat(reportById.getData().getFilter())
      .singleElement()
      .satisfies(filter -> assertThat(filter.getAppliedTo()).containsExactly(definitionIdentifier));
  }

  @Test
  public void createNewSingleDecisionReportWithFiltersAppliedToSetToInvalidIdFails() {
    // given
    final String definitionIdentifier = "1";
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier(definitionIdentifier);
    final DecisionReportDataDto reportDataDto = DecisionReportDataDto.builder().definitions(definitions).build();
    final EvaluationDateFilterDto filterDto =
      DecisionFilterUtilHelper.createRelativeEvaluationDateFilter(1L, DateUnit.SECONDS);
    filterDto.setAppliedTo(List.of("invalid"));
    reportDataDto.getFilter().add(filterDto);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(new SingleDecisionReportDefinitionRequestDto(reportDataDto))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createNewSingleDecisionReportWithEmptyFiltersAppliedToFails() {
    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier("1");
    final DecisionReportDataDto reportDataDto = DecisionReportDataDto.builder().definitions(definitions).build();
    final EvaluationDateFilterDto filterDto =
      DecisionFilterUtilHelper.createRelativeEvaluationDateFilter(1L, DateUnit.SECONDS);
    filterDto.setAppliedTo(Collections.emptyList());
    reportDataDto.getFilter().add(filterDto);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(new SingleDecisionReportDefinitionRequestDto(reportDataDto))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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
    // given
    String id = addEmptyReportToOptimize(reportType);

    // when
    Response response = updateReportRequest(id, reportType);

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void updateReportWithoutDefinitionIdentifierFails(final ReportType reportType) {
    // given
    final String id = addEmptyReportToOptimize(reportType);

    // given
    final List<ReportDataDefinitionDto> definitions = createSingleDefinitionListWithIdentifier(null);

    // when
    Response response;
    switch (reportType) {
      case PROCESS:
        response = embeddedOptimizeExtension
          .getRequestExecutor()
          .buildUpdateSingleReportRequest(
            id,
            new SingleProcessReportDefinitionRequestDto(ProcessReportDataDto.builder().definitions(definitions).build())
          )
          .execute();
        break;
      case DECISION:
        response = embeddedOptimizeExtension
          .getRequestExecutor()
          .buildUpdateSingleReportRequest(
            id,
            new SingleDecisionReportDefinitionRequestDto(
              DecisionReportDataDto.builder().definitions(definitions).build()
            )
          )
          .execute();
        break;
      default:
        throw new OptimizeIntegrationTestException("Unsupported report type: " + reportType);
    }

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateSingleProcessReportWithFiltersAppliedToSetToProvidedDefinition() {
    // given
    final String reportId = addReportToOptimizeWithDefinitionAndRandomXml(ReportType.PROCESS);
    final SingleProcessReportDefinitionRequestDto reportDefinition = reportClient.getSingleProcessReportById(reportId);
    final String definitionIdentifier = reportDefinition.getData().getDefinitions().get(0).getIdentifier();
    reportDefinition.getData().setFilter(
      ProcessFilterBuilder.filter().completedInstancesOnly().appliedTo(definitionIdentifier).add().buildList()
    );

    // when
    final Response response = reportClient.updateSingleProcessReport(reportId, reportDefinition);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void updateSingleProcessReportWithFiltersAppliedToSetToInvalidIdFails() {
    // given
    final String reportId = addReportToOptimizeWithDefinitionAndRandomXml(ReportType.PROCESS);
    final SingleProcessReportDefinitionRequestDto reportDefinition = reportClient.getSingleProcessReportById(reportId);
    reportDefinition.getData().setFilter(
      ProcessFilterBuilder.filter().completedInstancesOnly().appliedTo("invalid").add().buildList()
    );

    // when
    final Response response = reportClient.updateSingleProcessReport(reportId, reportDefinition);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateSingleProcessReportWithEmptyFiltersAppliedToFails() {
    // given
    final String reportId = addReportToOptimizeWithDefinitionAndRandomXml(ReportType.PROCESS);
    final SingleProcessReportDefinitionRequestDto reportDefinition = reportClient.getSingleProcessReportById(reportId);
    reportDefinition.getData().setFilter(
      ProcessFilterBuilder.filter().completedInstancesOnly().appliedTo(Collections.emptyList()).add().buildList()
    );

    // when
    final Response response = reportClient.updateSingleProcessReport(reportId, reportDefinition);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateSingleDecisionReportWithFiltersAppliedToSetToProvidedDefinition() {
    // given
    final String reportId = addReportToOptimizeWithDefinitionAndRandomXml(DECISION);
    final SingleDecisionReportDefinitionRequestDto reportDefinition =
      reportClient.getSingleDecisionReportById(reportId);
    final String definitionIdentifier = reportDefinition.getData().getDefinitions().get(0).getIdentifier();
    final EvaluationDateFilterDto filterDto =
      DecisionFilterUtilHelper.createRelativeEvaluationDateFilter(1L, DateUnit.SECONDS);
    filterDto.setAppliedTo(List.of(definitionIdentifier));
    reportDefinition.getData().getFilter().add(filterDto);

    // when
    final Response response = reportClient.updateDecisionReport(reportId, reportDefinition);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void updateSingleDecisionReportWithFiltersAppliedToSetToInvalidIdFails() {
    // given
    final String reportId = addReportToOptimizeWithDefinitionAndRandomXml(DECISION);
    final SingleDecisionReportDefinitionRequestDto reportDefinition =
      reportClient.getSingleDecisionReportById(reportId);
    final EvaluationDateFilterDto filterDto =
      DecisionFilterUtilHelper.createRelativeEvaluationDateFilter(1L, DateUnit.SECONDS);
    filterDto.setAppliedTo(List.of("invalid"));
    reportDefinition.getData().getFilter().add(filterDto);

    // when
    final Response response = reportClient.updateDecisionReport(reportId, reportDefinition);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateSingleDecisionReportWithEmptyFiltersAppliedToFails() {
    // given
    final String reportId = addReportToOptimizeWithDefinitionAndRandomXml(DECISION);
    final SingleDecisionReportDefinitionRequestDto reportDefinition =
      reportClient.getSingleDecisionReportById(reportId);
    final EvaluationDateFilterDto filterDto =
      DecisionFilterUtilHelper.createRelativeEvaluationDateFilter(1L, DateUnit.SECONDS);
    filterDto.setAppliedTo(Collections.emptyList());
    reportDefinition.getData().getFilter().add(filterDto);

    // when
    final Response response = reportClient.updateDecisionReport(reportId, reportDefinition);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void updateReportWithXml(final ReportType reportType) {
    // given
    String id = addEmptyReportToOptimize(reportType);

    // when
    final Response response = updateReportWithValidXml(id, reportType);

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void getStoredPrivateReports_excludesNonPrivateReports() {
    // given
    String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    String privateDecisionReportId = reportClient.createEmptySingleDecisionReport();
    String privateProcessReportId = reportClient.createEmptySingleProcessReport();
    reportClient.createEmptySingleProcessReportInCollection(collectionId);

    // when
    List<AuthorizedReportDefinitionResponseDto> reports = reportClient.getAllReportsAsUser();

    // then the returned list excludes reports in collections and excludes the management reports
    assertThat(
      reports.stream()
        .map(AuthorizedReportDefinitionResponseDto::getDefinitionDto)
        .map(ReportDefinitionDto::getId)
        .collect(Collectors.toList()))
      .containsExactlyInAnyOrder(privateDecisionReportId, privateProcessReportId);
  }

  @Test
  public void getStoredPrivateReports_adoptTimezoneFromHeader() {
    // given
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
    // given
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
    // given
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
    // given
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
    // given
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
    // given
    String id = addEmptyReportToOptimize(reportType);

    // when
    reportClient.deleteReport(id);

    // then
    assertThat(reportClient.getAllReportsAsUser()).isEmpty();
  }

  @Test
  public void managementReportCannotBeDeleted() {
    // given
    embeddedOptimizeExtension.getManagementDashboardService().init();
    final String reportId = findManagementReportId();

    // when
    final Response response = reportClient.deleteReport(reportId, true);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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
    // given
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
    // given
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
    // given
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

  @Test
  public void copyManagementReportDoesNotWork() {
    // given
    embeddedOptimizeExtension.getManagementDashboardService().init();
    final String reportId = findManagementReportId();

    // when
    Response response = reportClient.copyReportToCollection(reportId, null);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void copyManagementReportIntoCollectionDoesNotWork() {
    // given
    embeddedOptimizeExtension.getManagementDashboardService().init();
    final String reportId = findManagementReportId();
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);

    // when
    Response response = reportClient.copyReportToCollection(reportId, collectionId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void copySingleReport(ReportType reportType) {
    // given
    String id = createSingleReport(reportType);

    // when
    Response response = reportClient.copyReportToCollection(id, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    IdResponseDto copyId = response.readEntity(IdResponseDto.class);

    // then
    ReportDefinitionDto oldReport = reportClient.getReportById(id);
    ReportDefinitionDto report = reportClient.getReportById(copyId.getId());
    assertThat(report.getData()).hasToString(oldReport.getData().toString());
    assertThat(oldReport.getName() + " – Copy").isEqualTo(report.getName());
  }

  @Test
  public void copyCombinedReport() {
    // given
    String id = reportClient.createCombinedReport(null, new ArrayList<>());

    // when
    Response response = reportClient.copyReportToCollection(id, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    IdResponseDto copyId = response.readEntity(IdResponseDto.class);

    // then
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

  @Test
  public void managementReportCannotBeCreated() {
    // given
    final SingleProcessReportDefinitionRequestDto reportDefinition =
      new SingleProcessReportDefinitionRequestDto();
    reportDefinition.getData().setManagementReport(true);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCreateSingleProcessReportRequest(reportDefinition)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }


  @Test
  public void managementReportCannotBeUpdated() {
    // given
    embeddedOptimizeExtension.getManagementDashboardService().init();
    final String reportId = findManagementReportId();
    final SingleProcessReportDefinitionRequestDto updatedReport = new SingleProcessReportDefinitionRequestDto();
    updatedReport.getData().setManagementReport(true);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(reportId, updatedReport)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void instantReportCannotBeCreated() {
    // given
    final SingleProcessReportDefinitionRequestDto reportDefinition =
      new SingleProcessReportDefinitionRequestDto();
    reportDefinition.getData().setInstantPreviewReport(true);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCreateSingleProcessReportRequest(reportDefinition)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void instantReportCannotBeUpdated() {
    // given
    String processDefKey = "aProceass";
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(processDefKey));
    importAllEngineEntitiesFromScratch();

    // when
    Optional<String> instantReportId = dashboardClient.getInstantPreviewDashboard(
      processDefKey,
      "template1.json"
    ).getReportIds().stream().findFirst();
    assertThat(instantReportId).isPresent();
    final SingleProcessReportDefinitionRequestDto updatedReport = new SingleProcessReportDefinitionRequestDto();
    updatedReport.getData().setInstantPreviewReport(true);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(instantReportId.get(), new SingleProcessReportDefinitionRequestDto())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private List<ReportDataDefinitionDto> createSingleDefinitionListWithIdentifier(final String definitionIdentifier) {
    return List.of(new ReportDataDefinitionDto(
      definitionIdentifier,
      RANDOM_KEY,
      RANDOM_STRING,
      RANDOM_STRING,
      Collections.singletonList(ALL_VERSIONS),
      DEFAULT_TENANT_IDS
    ));
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

  private String findManagementReportId() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      SingleProcessReportDefinitionRequestDto.class
    )
      .stream()
      .filter(reportDef -> reportDef.getData().isManagementReport())
      .findFirst()
      .map(ReportDefinitionDto::getId)
      .orElseThrow(() -> new OptimizeIntegrationTestException("No Management Report Found"));
  }

}

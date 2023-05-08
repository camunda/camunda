/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedSingleReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.service.sharing.AbstractSharingIT;
import org.camunda.optimize.service.util.ProcessReportDataBuilderHelper;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.CONTAINS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_CONTAINS;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_2;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagramWithAssignees;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagramWithCandidateGroups;

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
    // given
    final String reportId = addReportToOptimizeWithDefinitionAndRandomXml(reportType);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void evaluateReportById_additionalFiltersAreApplied() {
    // given
    BpmnModelInstance processModel = BpmnModels.getSingleUserTaskDiagram();
    final String variableName = "var1";
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModelWithVariables(
      processModel,
      ImmutableMap.of(variableName, "value")
    );
    final String reportId = createOptimizeReportForProcess(processModel, processInstanceEngineDto);

    // when
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when future start date filter applied
    Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createFixedDateFilter(OffsetDateTime.now().plusDays(1), null))
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);

    // when historic end date filter applied
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createFixedDateFilter(null, OffsetDateTime.now().minusYears(100L)))
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);

    // when variable filter applied for existent value
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createStringVariableFilter(variableName, "value")
      )
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);

    // when variable filter applied for non-existent value
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createStringVariableFilter(variableName, "someOtherValue")
      )
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);

    // when completed instances filter applied
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(completedInstancesOnlyFilter())
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);

    // when the instance gets completed and a running instance filter applied
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(runningInstancesOnlyFilter())
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);

    // when contains matching filter applied
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createContainsFilterForValues(variableName, "val"))
    );

    // then instance is part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);

    // when contains is not matching filter applied
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createContainsFilterForValues(variableName, "notMatch"))
    );

    // then instance is part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);

    // when not contains does not match given value
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createNotContainsFilterForValues(variableName, "notMatch"))
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);

    // when not contains does match given value
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createNotContainsFilterForValues(variableName, "val"))
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);
  }

  @Test
  public void evaluateReportById_additionalAssigneeFiltersAreApplied() {
    // given
    final BpmnModelInstance userTaskModel = getDoubleUserTaskDiagramWithAssignees(DEFAULT_USERNAME, "otherUserId");
    final ProcessInstanceEngineDto instance = deployAndStartInstanceForModel(userTaskModel);
    engineIntegrationExtension.completeUserTaskWithoutClaim(instance.getId());
    engineIntegrationExtension.completeUserTaskWithoutClaim(instance.getId());
    final String reportId = createUserTaskReport(userTaskModel, instance);
    importAllEngineEntitiesFromScratch();

    // when filtering for usertasks with the assignee
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createAssigneeFilter(MembershipFilterOperator.IN, DEFAULT_USERNAME))
    ).getResult();

    // then only the usertask with the assignee is included
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getFirstMeasureData()).extracting(MapResultEntryDto::getKey).containsOnly(USER_TASK_1);

    // when filtering for usertasks without the assignee
    result = reportClient.evaluateMapReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createAssigneeFilter(MembershipFilterOperator.NOT_IN, DEFAULT_USERNAME))
    ).getResult();

    // then only the usertask without the assignee is included
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getFirstMeasureData()).extracting(MapResultEntryDto::getKey).containsOnly(USER_TASK_2);
  }

  @Test
  public void evaluateReportById_additionalCandidateGroupFiltersAreApplied() {
    // given
    final String candidateGroupId = "candidateGroupId";
    final BpmnModelInstance userTaskModel =
      getDoubleUserTaskDiagramWithCandidateGroups(candidateGroupId, "otherGroupId");
    final ProcessInstanceEngineDto instance = deployAndStartInstanceForModel(userTaskModel);
    engineIntegrationExtension.completeUserTaskWithoutClaim(instance.getId());
    engineIntegrationExtension.completeUserTaskWithoutClaim(instance.getId());
    final String reportId = createUserTaskReport(userTaskModel, instance);
    importAllEngineEntitiesFromScratch();

    // when filtering for usertasks with the candidate group
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createCandidateGroupFilter(MembershipFilterOperator.IN, candidateGroupId))
    ).getResult();

    // then only the usertask with the candidate group is included
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getFirstMeasureData()).extracting(MapResultEntryDto::getKey).containsOnly(USER_TASK_1);

    // when filtering for usertasks without the candidate group
    result = reportClient.evaluateMapReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createCandidateGroupFilter(MembershipFilterOperator.NOT_IN, candidateGroupId))
    ).getResult();

    // then only the usertask without the candidate group is included
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getFirstMeasureData()).extracting(MapResultEntryDto::getKey).containsOnly(USER_TASK_2);
  }

  @Test
  public void evaluateReportById_additionalAssigneeFiltersAreCombinedWithIncompatibleReportFilter() {
    // given
    final String otherUserId = "otherUserId";
    final BpmnModelInstance userTaskModel = getDoubleUserTaskDiagramWithAssignees(DEFAULT_USERNAME, otherUserId);
    final ProcessInstanceEngineDto instance = deployAndStartInstanceForModel(userTaskModel);
    engineIntegrationExtension.completeUserTaskWithoutClaim(instance.getId());
    engineIntegrationExtension.completeUserTaskWithoutClaim(instance.getId());
    final String reportId =
      createUserTaskReport(userTaskModel, instance, createAssigneeFilter(MembershipFilterOperator.IN, otherUserId));
    importAllEngineEntitiesFromScratch();

    // when
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createAssigneeFilter(MembershipFilterOperator.IN, DEFAULT_USERNAME))
    ).getResult();

    // then the additional and report filters are combined with "and" and hence returns no results
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getFirstMeasureData()).isEmpty();
  }

  @Test
  public void evaluateReportById_additionalCandidateFiltersAreCombinedWithIncompatibleReportFilter() {
    // given
    final String candidateGroupId = "candidateGroupId";
    final String otherGroupId = "otherGroupId";
    final BpmnModelInstance userTaskModel =
      getDoubleUserTaskDiagramWithCandidateGroups(candidateGroupId, otherGroupId);
    final ProcessInstanceEngineDto instance = deployAndStartInstanceForModel(userTaskModel);
    engineIntegrationExtension.completeUserTaskWithoutClaim(instance.getId());
    engineIntegrationExtension.completeUserTaskWithoutClaim(instance.getId());
    final String reportId =
      createUserTaskReport(userTaskModel, instance, createCandidateGroupFilter(MembershipFilterOperator.IN, otherGroupId));
    importAllEngineEntitiesFromScratch();

    // when
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createCandidateGroupFilter(MembershipFilterOperator.IN, candidateGroupId))
    ).getResult();

    // then the additional and report filters are combined with "and" and hence return no results
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
    assertThat(result.getFirstMeasureData()).isEmpty();
  }

  @Test
  public void evaluateReportByIdWithAdditionalFilters_filtersCombinedWithAlreadyExistingFiltersOnReport() {
    // given a report with a running instances filter
    BpmnModelInstance processModel = BpmnModels.getSingleUserTaskDiagram();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModel(processModel);
    final String reportId = createReportForProcessUsingFilters(
      processModel,
      processInstanceEngineDto,
      runningInstancesOnlyFilter()
    );

    // when
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when completed instances filter added
    final Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(completedInstancesOnlyFilter())
    );

    // then instance is no longer part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);
  }

  @Test
  public void evaluateReportById_emptyFiltersListDoesNotImpactExistingFilters() {
    // given a report with a running instances filter
    BpmnModelInstance processModel = BpmnModels.getSingleUserTaskDiagram();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModel(processModel);
    final String reportId = createReportForProcessUsingFilters(
      processModel,
      processInstanceEngineDto,
      runningInstancesOnlyFilter()
    );

    // when
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when empty filter list added
    final Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(Collections.emptyList())
    );

    // then instance is still part of evaluated result when identical filter added
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);
  }

  @Test
  public void evaluateReportByIdWithAdditionalFilters_filtersExistOnReportThatAreSameAsAdditional() {
    // given
    BpmnModelInstance processModel = BpmnModels.getSingleUserTaskDiagram();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModel(processModel);
    final String reportId = createReportForProcessUsingFilters(
      processModel,
      processInstanceEngineDto,
      runningInstancesOnlyFilter()
    );

    // when
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when additional identical filter added
    final Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(runningInstancesOnlyFilter())
    );

    // then instance is still part of evaluated result when identical filter added
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);
  }

  @Test
  public void evaluateReportByIdWithAdditionalFilters_filtersIgnoredIfDecisionReport() {
    // given
    final DmnModelInstance decisionModel = createSimpleDmnModel("someKey");
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      deployAndStartDecisionInstanceForModel(decisionModel);

    final String reportId = createOptimizeReportForDecisionDefinition(decisionModel, decisionDefinitionEngineDto);

    // when
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when additional filter added
    final Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createFixedDateFilter(OffsetDateTime.now().plusSeconds(1), null))
    );

    // then the instance is still part of evaluation result when evaluated with future start date filter
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);
  }

  @Test
  public void evaluateReportById_variableFiltersWithNameThatDoesNotExistForReportAreIgnored() {
    // given
    BpmnModelInstance processModel = BpmnModels.getSingleUserTaskDiagram();
    final String variableName = "var1";
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModelWithVariables(
      processModel,
      ImmutableMap.of(variableName, "someValue")
    );
    final String reportId = createOptimizeReportForProcess(processModel, processInstanceEngineDto);

    // when no filter is used
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when filter for given name added
    Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createStringVariableFilter(variableName, "someValue"))
    );

    // then instance is part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);

    // when filter for unknown variable name added
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createStringVariableFilter("someOtherVariableName", "someValue"))
    );

    // then filter gets ignored and instance is part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);

    // when known and unknown variable filter used in combination
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(
        Stream.concat(
          createStringVariableFilter(variableName, "someValue").stream(),
          createStringVariableFilter("someOtherVariableName", "someValue").stream()
        ).collect(Collectors.toList())
      )
    );

    // then filter gets ignored and instance is part of evaluated result as the value matches
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);

    // when known and unknown variable filter used in combination and value does not match
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(
        Stream.concat(
          createStringVariableFilter(variableName, "someOtherValue").stream(),
          createStringVariableFilter("someOtherVariableName", "someValue").stream()
        ).collect(Collectors.toList())
      )
    );

    // then filter gets ignored and instance is part of evaluated result as the value matches
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);
  }

  @Test
  public void evaluateReportById_variableFiltersWithTypeThatDoesNotExistForReportAreIgnored() {
    // given deployed instance with long type variable
    BpmnModelInstance processModel = BpmnModels.getSingleUserTaskDiagram();
    final String variableName = "var1";
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModelWithVariables(
      processModel,
      ImmutableMap.of(variableName, 5L)
    );
    final String reportId = createOptimizeReportForProcess(processModel, processInstanceEngineDto);

    // when no filter is used
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when long variable filter for given variable name used
    Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createLongVariableFilter(variableName, 3L))
    );

    // then result is filtered out by filter
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);

    // when string variable filter for given variable name used
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createStringVariableFilter(variableName, "someValue"))
    );

    // then filter is ignored as variable is of wrong type for report so instance is still part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);
  }

  @Test
  public void evaluateInvalidReportById() {
    // given
    ProcessReportDataDto reportData = createProcessReportData(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE);
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
    // given
    final SingleReportDataDto reportDataDto = createSingleReportDataForType(reportType);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportDataDto)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateUnsavedReport_reflectOwnerAndModifierNames_ownerHasNoAuthSideEffects(ReportType reportType) {
    // given
    final SingleReportDataDto reportDataDto = createSingleReportDataForType(reportType);
    final SingleReportDefinitionDto<?> reportDefinitionDto;
    final AuthorizedSingleReportEvaluationResponseDto<?, ?> result;
    switch (reportType) {
      case PROCESS:
        reportDefinitionDto = new SingleProcessReportDefinitionRequestDto((ProcessReportDataDto) reportDataDto);
        break;
      case DECISION:
        reportDefinitionDto =
          new SingleDecisionReportDefinitionRequestDto((DecisionReportDataDto) reportDataDto);
        break;
      default:
        throw new IllegalStateException("Uncovered type: " + reportType);
    }

    // owner different from current user should not cause the evaluation to be rejected
    reportDefinitionDto.setOwner("Sauron");
    reportDefinitionDto.setLastModifier("Frodo Beutlin");

    // when
    result = ReportType.PROCESS.equals(reportType)
      ? reportClient.evaluateProcessReport(reportDefinitionDto)
      : reportClient.evaluateDecisionReport(reportDefinitionDto);

    // then
    assertThat(result.getCurrentUserRole()).isEqualTo(RoleType.EDITOR);
    // on response, unsaved reports have no owner or last modifier
    assertThat(result.getReportDefinition().getOwner()).isNull();
    assertThat(result.getReportDefinition().getLastModifier()).isNull();
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateUnsavedReportWithoutVersionsAndTenantsDoesNotFail(ReportType reportType) {
    // given
    final SingleReportDataDto reportDataDto = createReportWithoutVersionsAndTenants(reportType);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportDataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void evaluateUnsavedProcessReport_filterAppliedToValidationFails() {
    // given
    final ProcessReportDataDto reportDataDto = createProcessReportData();
    reportDataDto.setFilter(
      ProcessFilterBuilder.filter().completedInstancesOnly().appliedTo(Collections.emptyList()).add().buildList()
    );

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportDataDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void evaluateUnsavedDecisionReport_filterAppliedToValidationFails() {
    // given
    final DecisionReportDataDto reportDataDto = createDecisionReportData();
    final EvaluationDateFilterDto filterDto =
      DecisionFilterUtilHelper.createRelativeEvaluationDateFilter(1L, DateUnit.SECONDS);
    filterDto.setAppliedTo(List.of("invalid"));
    reportDataDto.getFilter().add(filterDto);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportDataDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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
  public void evaluateCombinedUnsavedReport_returnsOk() {
    // given
    CombinedReportDataDto combinedReport = ProcessReportDataBuilderHelper.createCombinedReportData();

    // when
    final Response response = evaluateUnsavedCombinedReport(combinedReport);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void nullReportsAreHandledAsEmptyList() {
    // given
    CombinedReportDataDto combinedReport = ProcessReportDataBuilderHelper.createCombinedReportData();
    combinedReport.setReports(null);

    // when
    final Response response = evaluateUnsavedCombinedReport(combinedReport);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateReportWithoutViewById(ReportType reportType) {
    // given
    String id;
    switch (reportType) {
      case PROCESS:
        ProcessReportDataDto processReportDataDto = createProcessReportData(ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE);
        processReportDataDto.setView(null);
        id = addSingleProcessReportWithDefinition(processReportDataDto);
        break;
      case DECISION:
        DecisionReportDataDto decisionReportDataDto = createDecisionReportData();
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

  @ParameterizedTest
  @MethodSource("nonPaginateableReportTypes")
  public void evaluateNonPaginateableUnsavedReportWithPaginationParametersReturnsError(SingleReportDataDto reportDataDto) {
    // given
    PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setOffset(10);
    paginationDto.setLimit(10);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequestWithPagination(reportDataDto, paginationDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("nonPaginateableReportTypes")
  public void evaluateNonPaginateableSavedReportWithPaginationParametersReturnsError(SingleReportDataDto reportDataDto) {
    // given
    final String reportId = saveReport(reportDataDto);
    PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setOffset(10);
    paginationDto.setLimit(10);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId, paginationDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private String saveReport(final SingleReportDataDto reportDataDto) {
    final String reportId;
    if (reportDataDto instanceof ProcessReportDataDto) {
      reportId = reportClient.createSingleProcessReport((ProcessReportDataDto) reportDataDto);
    } else {
      reportId = reportClient.createSingleDecisionReport((DecisionReportDataDto) reportDataDto);
    }
    return reportId;
  }

  private static Stream<SingleReportDataDto> nonPaginateableReportTypes() {
    return Stream.concat(
      Stream.of(DecisionReportDataType.values())
        .filter(type -> !DecisionReportDataType.RAW_DATA.equals(type))
        .map(type -> DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(RANDOM_KEY)
          .setDecisionDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(type)
          .build()),
      Stream.of(ProcessReportDataType.values())
        .filter(type -> !ProcessReportDataType.RAW_DATA.equals(type))
        .map(ReportEvaluationRestServiceIT::createProcessReportData)
    );
  }

  private SingleReportDataDto createSingleReportDataForType(final ReportType reportType) {
    final SingleReportDataDto reportDataDto;
    switch (reportType) {
      case PROCESS:
        reportDataDto = createProcessReportData(ProcessReportDataType.RAW_DATA);
        break;
      case DECISION:
        reportDataDto = createDecisionReportData();
        break;
      default:
        throw new IllegalStateException("Uncovered type: " + reportType);
    }
    return reportDataDto;
  }

  private Response evaluateUnsavedCombinedReport(CombinedReportDataDto reportDataDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      .execute();
  }

  private Response evaluateSavedReport(final String reportId) {
    return evaluateSavedReport(reportId, null);
  }

  private Response evaluateSavedReport(final String reportId,
                                       final AdditionalProcessReportEvaluationFilterDto filters) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId, filters)
      .execute();
  }

  private void assertExpectedEvaluationInstanceCount(final Response response, final long expectedCount) {
    assertThat(extractReportResponse(response).getResult().getInstanceCount()).isEqualTo(expectedCount);
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<RawDataInstanceDto>> extractReportResponse(Response response) {
    String jsonString = response.readEntity(String.class);
    try {
      return embeddedOptimizeExtension
        .getObjectMapper()
        .readValue(
          jsonString,
          new TypeReference<>() {
          }
        );
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
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
    importAllEngineEntitiesFromScratch();
    return decisionDefinitionEngineDto;
  }

  private List<ProcessFilterDto<?>> createFixedDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedInstanceStartDate().start(startDate).end(endDate).add().buildList();
  }

  private List<ProcessFilterDto<?>> createLongVariableFilter(String variableName, Long variableValue) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .longType()
      .name(variableName)
      .operator(IN)
      .values(Collections.singletonList(String.valueOf(variableValue)))
      .add()
      .buildList();
  }

  private List<ProcessFilterDto<?>> createStringVariableFilter(String variableName, String variableValue) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .stringType()
      .name(variableName)
      .values(Collections.singletonList(variableValue))
      .operator(IN)
      .add()
      .buildList();
  }

  private List<ProcessFilterDto<?>> createAssigneeFilter(final MembershipFilterOperator operator,
                                                         final String assigneeId) {
    return ProcessFilterBuilder.filter()
      .assignee()
      .filterLevel(FilterApplicationLevel.VIEW)
      .operator(operator)
      .id(assigneeId)
      .add()
      .buildList();
  }

  private List<ProcessFilterDto<?>> createCandidateGroupFilter(final MembershipFilterOperator operator,
                                                               final String assigneeId) {
    return ProcessFilterBuilder.filter()
      .candidateGroups()
      .filterLevel(FilterApplicationLevel.VIEW)
      .operator(operator)
      .id(assigneeId)
      .add()
      .buildList();
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

  private String createReportForProcessUsingFilters(final BpmnModelInstance processModel,
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

  private String createUserTaskReport(final BpmnModelInstance processModel,
                                      final ProcessInstanceEngineDto processInstanceEngineDto) {
    return createUserTaskReport(processModel, processInstanceEngineDto, null);
  }

  private String createUserTaskReport(final BpmnModelInstance processModel,
                                      final ProcessInstanceEngineDto processInstanceEngineDto,
                                      final List<ProcessFilterDto<?>> filters) {
    final TemplatedProcessReportDataBuilder reportBuilder = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK);
    Optional.ofNullable(filters).ifPresent(reportBuilder::setFilter);
    final ProcessReportDataDto processReportDataDto = reportBuilder.build();
    processReportDataDto.getConfiguration().setXml(toXml(processModel));
    return addSingleProcessReportWithDefinition(processReportDataDto);
  }

  private String createOptimizeReportForProcess(final BpmnModelInstance processModel,
                                                final ProcessInstanceEngineDto processInstanceEngineDto) {
    return createReportForProcessUsingFilters(processModel, processInstanceEngineDto, null);
  }

  private ProcessInstanceEngineDto deployAndStartInstanceForModel(final BpmnModelInstance processModel) {
    return deployAndStartInstanceForModelWithVariables(processModel, Collections.emptyMap());
  }

  private ProcessInstanceEngineDto deployAndStartInstanceForModelWithVariables(final BpmnModelInstance processModel,
                                                                               final Map<String, Object> variables) {
    final ProcessInstanceEngineDto instance = engineIntegrationExtension.deployAndStartProcessWithVariables(
      processModel,
      variables
    );
    importAllEngineEntitiesFromScratch();
    return instance;
  }

  private String toXml(final BpmnModelInstance processModel) {
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, processModel);
    return xmlOutput.toString(StandardCharsets.UTF_8);
  }

  private List<ProcessFilterDto<?>> runningInstancesOnlyFilter() {
    return ProcessFilterBuilder.filter().runningInstancesOnly().add().buildList();
  }

  private List<ProcessFilterDto<?>> completedInstancesOnlyFilter() {
    return ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList();
  }

  private List<ProcessFilterDto<?>> createContainsFilterForValues(final String variableName,
                                                                  final String... valuesToFilterFor) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .name(variableName)
      .stringType()
      .values(Arrays.asList(valuesToFilterFor))
      .operator(CONTAINS)
      .add()
      .buildList();
  }

  private List<ProcessFilterDto<?>> createNotContainsFilterForValues(final String variableName,
                                                                     final String... valuesToFilterFor) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .name(variableName)
      .stringType()
      .values(Arrays.asList(valuesToFilterFor))
      .operator(NOT_CONTAINS)
      .add()
      .buildList();
  }

  private static DecisionReportDataDto createDecisionReportData() {
    return DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(RANDOM_KEY)
      .setDecisionDefinitionVersion(RANDOM_VERSION)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
  }

  private static ProcessReportDataDto createProcessReportData() {
    return createProcessReportData(ProcessReportDataType.RAW_DATA);
  }

  private static ProcessReportDataDto createProcessReportData(final ProcessReportDataType reportDataType) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(RANDOM_KEY)
      .setProcessDefinitionVersion(RANDOM_VERSION)
      .setReportDataType(reportDataType)
      .build();
  }

}

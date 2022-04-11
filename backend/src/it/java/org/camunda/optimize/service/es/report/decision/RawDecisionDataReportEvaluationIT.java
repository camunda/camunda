/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.decision;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.report.command.modules.view.decision.DecisionViewRawData;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_LIMIT;
import static org.camunda.optimize.dto.optimize.ReportConstants.PAGINATION_DEFAULT_OFFSET;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_CATEGORY_ID;
import static org.camunda.optimize.util.DmnModels.OUTPUT_AUDIT_ID;
import static org.camunda.optimize.util.DmnModels.OUTPUT_BEVERAGES;
import static org.camunda.optimize.util.DmnModels.OUTPUT_CLASSIFICATION_ID;
import static org.camunda.optimize.util.DmnModels.createDecideDishDecisionDefinition;
import static org.camunda.optimize.util.DmnModels.createDefaultDmnModel;

public class RawDecisionDataReportEvaluationIT extends AbstractDecisionDefinitionIT {

  @Test
  public void reportEvaluation() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion = decisionDefinitionDto.getVersionAsString();

    final HashMap<String, InputVariableEntry> inputs = createInputs(200.0, "Misc");
    final HashMap<String, OutputVariableEntry> expectedOutputs = createOutputs(
      false, "day-to-day expense"
    );
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), inputs);

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), decisionDefinitionVersion);
    final AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      evaluateRawReportWithDefaultPagination(reportData);

    // then
    final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).isNotNull().hasSize(1);

    RawDataDecisionInstanceDto rawDataDecisionInstanceDto = result.getData().get(0);
    assertThat(rawDataDecisionInstanceDto.getDecisionDefinitionKey()).isEqualTo(decisionDefinitionDto.getKey());
    assertThat(rawDataDecisionInstanceDto.getDecisionDefinitionId()).isEqualTo(decisionDefinitionDto.getId());
    assertThat(rawDataDecisionInstanceDto.getDecisionInstanceId()).isNotNull();
    assertThat(rawDataDecisionInstanceDto.getEngineName()).isNotNull();
    assertThat(rawDataDecisionInstanceDto.getEvaluationDateTime()).isNotNull();
    assertThat(rawDataDecisionInstanceDto.getProcessInstanceId()).isNull();

    final Map<String, InputVariableEntry> receivedInputVariables = rawDataDecisionInstanceDto.getInputVariables();
    assertInputVariablesMatchExcepted(inputs, receivedInputVariables);

    final Map<String, OutputVariableEntry> receivedOutputVariables = rawDataDecisionInstanceDto.getOutputVariables();
    assertOutputVariablesMatchExcepted(expectedOutputs, receivedOutputVariables);

    final DecisionReportDataDto resultDataDto = evaluationResult.getReportDefinition().getData();
    assertThat(resultDataDto.getDecisionDefinitionKey()).isEqualTo(decisionDefinitionDto.getKey());
    assertThat(resultDataDto.getDecisionDefinitionVersions()).containsOnly(decisionDefinitionVersion);
    assertThat(resultDataDto.getView())
      .isNotNull()
      .extracting(DecisionViewDto::getProperties)
      .asList()
      .containsExactly(ViewProperty.RAW_DATA);
  }

  @Test
  public void reportEvaluationAllVersions() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();

    final HashMap<String, InputVariableEntry> inputs = createInputs(200.0, "Misc");
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), inputs);

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);
    final AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      evaluateRawReportWithDefaultPagination(reportData);

    // then
    final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).isNotNull().hasSize(1);

    assertThat(evaluationResult.getReportDefinition().getData().getDecisionDefinitionVersions())
      .containsExactly(ALL_VERSIONS);
  }

  @Test
  public void reportEvaluationForSpecificKeyIsNotAffectedByOtherDecisionDefinitionInstances() {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = deployDishDecisionDefinition();
    startDishDecisionInstance(decisionDefinitionEngineDto);

    reportEvaluation();
  }

  @Test
  public void reportEvaluationForNoInstances() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto =
      engineIntegrationExtension.deployDecisionDefinition(createDefaultDmnModel("aUniqueDefinitionKey"));
    importAllEngineEntitiesFromScratch();

    final DecisionReportDataDto reportData = createReport(
      decisionDefinitionDto.getKey(),
      decisionDefinitionDto.getVersionAsString()
    );

    // when
    final AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      reportClient.evaluateDecisionRawReport(reportData, null);
    final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();

    // then
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getPagination()).isNotNull();
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Collections.singletonList(tenantId1);
    final String decisionDefinitionKey = deployAndStartMultiTenantDefinition(
      Arrays.asList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReport(decisionDefinitionKey, ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
      evaluateRawReportWithDefaultPagination(reportData)
        .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
    assertThat(result.getData())
      .extracting(RawDataDecisionInstanceDto::getTenantId)
      .containsAnyElementsOf(selectedTenants);
  }

  @Test
  public void reportEvaluationMultipleOutputs() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployDishDecisionDefinition();
    startDishDecisionInstance(decisionDefinitionDto);

    final HashMap<String, OutputVariableEntry> expectedOutputs = new HashMap<String, OutputVariableEntry>() {{
      put(
        OUTPUT_BEVERAGES,
        new OutputVariableEntry(
          // very good beer choice!
          OUTPUT_BEVERAGES, "Beverages", VariableType.STRING, "Aecht Schlenkerla Rauchbier"
        )
      );
    }};

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);
    final AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      evaluateRawReportWithDefaultPagination(reportData);

    // then
    final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).isNotNull().hasSize(1);

    RawDataDecisionInstanceDto rawDataDecisionInstanceDto = result.getData().get(0);

    final Map<String, OutputVariableEntry> receivedOutputVariables = rawDataDecisionInstanceDto.getOutputVariables();
    assertOutputVariablesMatchExcepted(expectedOutputs, receivedOutputVariables);
  }

  @Test
  public void resultShouldBeOrderedByDescendingEvaluationDateByDefault() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);
    final AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      evaluateRawReportWithDefaultPagination(reportData);

    // then
    final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull().hasSize(5);

    // The given list should be sorted in descending evaluationDateTime order
    assertThat(result.getData())
      .isSortedAccordingTo(
        Comparator.comparing(RawDataDecisionInstanceDto::getEvaluationDateTime).reversed()
      );
  }

  @Test
  public void resultShouldBeOrderedByIdProperty() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);
    reportData.getConfiguration()
      .setSorting(new ReportSortingDto(DecisionInstanceIndex.DECISION_INSTANCE_ID, SortOrder.ASC));
    final AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      evaluateRawReportWithDefaultPagination(reportData);

    // then
    final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull().hasSize(5);

    // The given list should be sorted in ascending decisionInstanceId order
    assertThat(result.getData())
      .isSortedAccordingTo(
        Comparator.comparing(RawDataDecisionInstanceDto::getDecisionInstanceId)
      );
  }

  @Test
  public void resultShouldBeOrderedByEvaluationDatePropertyAsc() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);
    reportData.getConfiguration()
      .setSorting(new ReportSortingDto(DecisionInstanceIndex.EVALUATION_DATE_TIME, SortOrder.ASC));
    final AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      evaluateRawReportWithDefaultPagination(reportData);

    // then
    final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull().hasSize(5);

    // The given list should be sorted in ascending evaluationDateTime order
    assertThat(result.getData())
      .isSortedAccordingTo(
        Comparator.comparing(RawDataDecisionInstanceDto::getEvaluationDateTime)
      );
  }

  @Test
  public void resultShouldBeOrderedByInputVariable() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    // use of values like 20 and 100 to ensure ordering is done numeric and not alphanumeric
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(20.0, "Misc"));
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(200.0, "Misc"));
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(300.0, "Misc"));
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(400.0, "Misc"));
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(1000.0, "Misc"));

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);
    reportData.getConfiguration().setSorting(
      new ReportSortingDto(DecisionViewRawData.INPUT_VARIABLE_PREFIX + INPUT_AMOUNT_ID, SortOrder.ASC)
    );
    final AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      evaluateRawReportWithDefaultPagination(reportData);

    // then
    final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull().hasSize(5);

    // The given list should be sorted in ascending inputVariable:amount order
    assertThat(result.getData())
      .isSortedAccordingTo(Comparator.comparingDouble(
        instance -> Double.parseDouble((String) instance.getInputVariables()
          .get(INPUT_AMOUNT_ID)
          .getValue())));
  }

  @Test
  public void resultShouldBeOrderedByOutputVariable() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    // results in audit false
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(10.0, "Misc"));
    // results in audit true
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(2000.0, "Misc"));
    // results in audit false
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(100.0, "Misc"));
    // results in audit true
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(3000.0, "Misc"));
    // results in audit false
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(200.0, "Misc"));

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);
    reportData.getConfiguration().setSorting(
      new ReportSortingDto(DecisionViewRawData.OUTPUT_VARIABLE_PREFIX + OUTPUT_AUDIT_ID, SortOrder.ASC)
    );
    final AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      evaluateRawReportWithDefaultPagination(reportData);

    // then
    final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull().hasSize(5);

    // The given list should be sorted in ascending outputVariable:audit order
    assertThat(result.getData())
      .isSortedAccordingTo(Comparator.comparing(instance -> Boolean.valueOf((String) instance.getOutputVariables()
        .get(OUTPUT_AUDIT_ID)
        .getFirstValue())));
  }

  @Test
  public void addVariablesToEntriesEvenIfNoValueExistsForVariable() {
    // given a decision instance with non null variable value and one instance with null variable value
    final String outputVarName = "outputVarName";
    final String inputVarName = "inputVarName";
    final Double doubleVarValue = 1.0;

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleOutputDecisionDefinition(
      outputVarName,
      inputVarName,
      String.valueOf(doubleVarValue),
      DecisionTypeRef.DOUBLE
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, null)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, doubleVarValue)
    );

    importAllEngineEntitiesFromScratch();

    // when we get the first result as a page
    DecisionReportDataDto reportData = createReport(
      decisionDefinitionDto.getKey(),
      decisionDefinitionDto.getVersionAsString()
    );
    final PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setOffset(0);
    paginationDto.setLimit(1);
    AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      reportClient.evaluateDecisionRawReport(reportData, paginationDto);

    // then the first result has a value for the input and output
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getFirstMeasureData().get(0))
      .satisfies(rawDataInstance -> {
        assertThat(rawDataInstance.getInputVariables().entrySet()).singleElement()
          .satisfies(inputVar -> assertThat(inputVar.getValue().getValue()).isEqualTo(String.valueOf(doubleVarValue)));
        assertThat(rawDataInstance.getOutputVariables().entrySet()).singleElement()
          .satisfies(outputVar -> assertThat(outputVar.getValue().getValues())
            .containsExactly(String.valueOf(doubleVarValue)));
      });

    // when we get the second result as a page
    paginationDto.setOffset(1);
    paginationDto.setLimit(1);
    evaluationResult = reportClient.evaluateDecisionRawReport(reportData, paginationDto);

    // then the second result still has variables shown despite no value
    result = evaluationResult.getResult();
    assertThat(result.getFirstMeasureData().get(0))
      .satisfies(rawDataInstance -> {
        assertThat(rawDataInstance.getInputVariables().entrySet()).singleElement()
          .satisfies(inputVar -> assertThat(inputVar.getValue().getValue()).isEqualTo(""));
        assertThat(rawDataInstance.getOutputVariables().entrySet()).singleElement()
          .satisfies(outputVar -> assertThat(outputVar.getValue().getValues()).isEmpty());
      });
  }

  @Test
  public void addNewVariablesToIncludedColumnsByDefault() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(100.0, "Misc"));

    importAllEngineEntitiesFromScratch();

    // when we have a report with included and excluded columns
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);
    reportData.getConfiguration().getTableColumns().getExcludedColumns().add(INPUT_PREFIX + INPUT_AMOUNT_ID);
    reportData.getConfiguration().getTableColumns().getIncludedColumns().add(OUTPUT_PREFIX + OUTPUT_AUDIT_ID);
    reportData.getConfiguration().getTableColumns().setIncludeNewVariables(true);
    final AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      evaluateRawReportWithDefaultPagination(reportData);

    // then the new input and output vars are added in alphabetical order to the included columns
    assertThat(evaluationResult.getReportDefinition()
                 .getData()
                 .getConfiguration()
                 .getTableColumns()
                 .getExcludedColumns())
      .contains(INPUT_PREFIX + INPUT_AMOUNT_ID);
    assertThat(evaluationResult.getReportDefinition()
                 .getData()
                 .getConfiguration()
                 .getTableColumns()
                 .getIncludedColumns())
      .contains(
        INPUT_PREFIX + INPUT_CATEGORY_ID,
        OUTPUT_PREFIX + OUTPUT_AUDIT_ID,
        OUTPUT_PREFIX + OUTPUT_CLASSIFICATION_ID
      );
  }

  @Test
  public void addNewVariablesToExcludedColumns() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(100.0, "Misc"));

    importAllEngineEntitiesFromScratch();

    // when we have a report with some included and excluded columns and includeNewVars set false
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);
    reportData.getConfiguration().getTableColumns().getExcludedColumns().add(INPUT_PREFIX + INPUT_AMOUNT_ID);
    reportData.getConfiguration().getTableColumns().getIncludedColumns().add(OUTPUT_PREFIX + OUTPUT_AUDIT_ID);
    reportData.getConfiguration().getTableColumns().setIncludeNewVariables(false);
    final AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      evaluateRawReportWithDefaultPagination(reportData);

    // then the new input and output vars are added in alphabetical order to the excluded columns
    assertThat(evaluationResult.getReportDefinition()
                 .getData()
                 .getConfiguration()
                 .getTableColumns()
                 .getExcludedColumns())
      .contains(
        INPUT_PREFIX + INPUT_AMOUNT_ID,
        INPUT_PREFIX + INPUT_CATEGORY_ID,
        OUTPUT_PREFIX + OUTPUT_CLASSIFICATION_ID
      );
    assertThat(evaluationResult.getReportDefinition()
                 .getData()
                 .getConfiguration()
                 .getTableColumns()
                 .getIncludedColumns())
      .contains(OUTPUT_PREFIX + OUTPUT_AUDIT_ID);
  }

  @Test
  public void removeNonExistentVariableColumns() {
    // given
    final String nonExistentInputVariable = INPUT_PREFIX + "nonExistentVariable";
    final String nonExistentOutputVariable = OUTPUT_PREFIX + "nonExistentVariable";
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(100.0, "Misc"));

    importAllEngineEntitiesFromScratch();

    // when we have a report with variables columns that no longer exist in the instance data
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);
    reportData.getConfiguration().getTableColumns().getExcludedColumns().add(nonExistentInputVariable);
    reportData.getConfiguration().getTableColumns().getIncludedColumns().add(nonExistentOutputVariable);
    final AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      evaluateRawReportWithDefaultPagination(reportData);

    // then the nonexistent variable columns are removed from the column configurations
    final List<String> allColumns = evaluationResult.getReportDefinition()
      .getData()
      .getConfiguration()
      .getTableColumns()
      .getExcludedColumns();
    allColumns.addAll(evaluationResult.getReportDefinition()
                        .getData()
                        .getConfiguration()
                        .getTableColumns()
                        .getIncludedColumns());

    assertThat(allColumns).doesNotContain(nonExistentInputVariable, nonExistentOutputVariable);
  }

  @Test
  public void reportEvaluation_pageThroughResults() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    importAllEngineEntitiesFromScratch();
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);

    // These are the expected instances using their default sort
    final List<String> expectedInstanceIds = elasticSearchIntegrationTestExtension.getAllDecisionInstances().stream()
      .sorted(Comparator.comparing(DecisionInstanceDto::getEvaluationDateTime).reversed())
      .map(DecisionInstanceDto::getDecisionInstanceId)
      .collect(Collectors.toList());

    // when we request the first page of results
    final PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setOffset(0);
    paginationDto.setLimit(2);
    AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      reportClient.evaluateDecisionRawReport(reportData, paginationDto);

    // then we get the first page of results
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull().hasSize(2)
      .extracting(RawDataDecisionInstanceDto::getDecisionInstanceId)
      .containsExactly(expectedInstanceIds.get(0), expectedInstanceIds.get(1));
    assertThat(result.getPagination()).isEqualTo(PaginationDto.fromPaginationRequest(paginationDto));

    // when we request the next page of results
    paginationDto.setOffset(2);
    paginationDto.setLimit(2);
    evaluationResult = reportClient.evaluateDecisionRawReport(reportData, paginationDto);

    // then we get the second page of results
    result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull().hasSize(2)
      .extracting(RawDataDecisionInstanceDto::getDecisionInstanceId)
      .containsExactly(expectedInstanceIds.get(2), expectedInstanceIds.get(3));
    assertThat(result.getPagination()).isEqualTo(PaginationDto.fromPaginationRequest(paginationDto));

    // when we request the next page of results
    paginationDto.setOffset(4);
    paginationDto.setLimit(2);
    evaluationResult = reportClient.evaluateDecisionRawReport(reportData, paginationDto);

    // then we get the last page of results, which contains less results than the limit
    result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull().hasSize(1)
      .extracting(RawDataDecisionInstanceDto::getDecisionInstanceId)
      .containsExactly(expectedInstanceIds.get(4));
    assertThat(result.getPagination()).isEqualTo(PaginationDto.fromPaginationRequest(paginationDto));
  }

  @Test
  public void reportEvaluation_negativeOffset() {
    // given
    DecisionReportDataDto reportData = createReport("definitionKey", ALL_VERSIONS);

    // when
    final PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setLimit(20);
    paginationDto.setOffset(-1);

    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequestWithPagination(reportData, paginationDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, MAX_RESPONSE_SIZE_LIMIT + 1})
  public void reportEvaluation_limitOutOfAcceptableBounds(int limit) {
    // given
    DecisionReportDataDto reportData = createReport("definitionKey", ALL_VERSIONS);

    // when
    final PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setOffset(0);
    paginationDto.setLimit(limit);

    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequestWithPagination(reportData, paginationDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void reportEvaluation_limitedToZeroResults() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    importAllEngineEntitiesFromScratch();
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);

    // when
    final PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setOffset(0);
    paginationDto.setLimit(0);

    AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      reportClient.evaluateDecisionRawReport(reportData, paginationDto);

    // then
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getData()).isNotNull().isEmpty();
    assertThat(result.getPagination()).isEqualTo(PaginationDto.fromPaginationRequest(paginationDto));
  }

  @Test
  public void reportEvaluation_offsetGreaterThanMaxResultReturnsEmptyData() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    importAllEngineEntitiesFromScratch();
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);

    // when
    final PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setLimit(20);
    paginationDto.setOffset(10);

    AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      reportClient.evaluateDecisionRawReport(reportData, paginationDto);

    // then
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getData()).isNotNull().isEmpty();
    assertThat(result.getPagination()).isEqualTo(PaginationDto.fromPaginationRequest(paginationDto));
  }

  @Test
  public void reportEvaluation_defaultOffset() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    importAllEngineEntitiesFromScratch();
    final String expectedDecisionInstanceId = elasticSearchIntegrationTestExtension.getAllDecisionInstances().stream()
      .sorted(Comparator.comparing(DecisionInstanceDto::getEvaluationDateTime).reversed())
      .map(DecisionInstanceDto::getDecisionInstanceId)
      .findFirst().orElseThrow(OptimizeIntegrationTestException::new);

    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);

    // when
    final PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setLimit(1);

    AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      reportClient.evaluateDecisionRawReport(reportData, paginationDto);

    // then
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getData()).hasSize(1)
      .extracting(RawDataDecisionInstanceDto::getDecisionInstanceId)
      .containsExactly(expectedDecisionInstanceId);
    assertThat(result.getPagination().getLimit()).isEqualTo(1);
    assertThat(result.getPagination().getOffset()).isEqualTo(PAGINATION_DEFAULT_OFFSET);
  }

  @Test
  public void reportEvaluation_defaultLimit() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    IntStream.range(0, 30)
      .forEach(i -> engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId()));

    importAllEngineEntitiesFromScratch();
    DecisionReportDataDto reportData = createReport(decisionDefinitionDto.getKey(), ALL_VERSIONS);

    // when
    final PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setOffset(0);

    AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      reportClient.evaluateDecisionRawReport(reportData, paginationDto);

    // then
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(30L);
    assertThat(result.getData()).hasSize(20);
    assertThat(result.getPagination().getLimit()).isEqualTo(PAGINATION_DEFAULT_LIMIT);
    assertThat(result.getPagination().getOffset()).isZero();
  }

  @Test
  public void reportEvaluation_pagedThroughSavedReportResults() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    importAllEngineEntitiesFromScratch();
    final String reportId = reportClient.createSingleDecisionReport(createReport(
      decisionDefinitionDto.getKey(),
      ALL_VERSIONS
    ));

    // These are the expected instances using their default sort
    final List<String> expectedInstanceIds = elasticSearchIntegrationTestExtension.getAllDecisionInstances().stream()
      .sorted(Comparator.comparing(DecisionInstanceDto::getEvaluationDateTime).reversed())
      .map(DecisionInstanceDto::getDecisionInstanceId)
      .collect(Collectors.toList());

    // when we request the first page of results
    final PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setOffset(0);
    paginationDto.setLimit(2);
    AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluationResult =
      evaluateSavedRawDataDecisionReport(reportId, paginationDto);

    // then we get the first page of results
    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull().hasSize(2)
      .extracting(RawDataDecisionInstanceDto::getDecisionInstanceId)
      .containsExactly(expectedInstanceIds.get(0), expectedInstanceIds.get(1));
    assertThat(result.getPagination()).isEqualTo(PaginationDto.fromPaginationRequest(paginationDto));

    // when we request the next page of results
    paginationDto.setOffset(2);
    paginationDto.setLimit(2);
    evaluationResult = evaluateSavedRawDataDecisionReport(reportId, paginationDto);

    // then we get the second page of results
    result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull().hasSize(2)
      .extracting(RawDataDecisionInstanceDto::getDecisionInstanceId)
      .containsExactly(expectedInstanceIds.get(2), expectedInstanceIds.get(3));
    assertThat(result.getPagination()).isEqualTo(PaginationDto.fromPaginationRequest(paginationDto));

    // when we request the next page of results
    paginationDto.setOffset(4);
    paginationDto.setLimit(2);
    evaluationResult = evaluateSavedRawDataDecisionReport(reportId, paginationDto);

    // then we get the last page of results, which contains less results than the limit
    result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull().hasSize(1)
      .extracting(RawDataDecisionInstanceDto::getDecisionInstanceId)
      .containsExactly(expectedInstanceIds.get(4));
    assertThat(result.getPagination()).isEqualTo(PaginationDto.fromPaginationRequest(paginationDto));
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluateSavedRawDataDecisionReport(
    final String reportId, final PaginationRequestDto paginationDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId, paginationDto)
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>>>() {
      });
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluateRawReportWithDefaultPagination(
    DecisionReportDataDto reportData) {
    PaginationRequestDto paginationDto = new PaginationRequestDto();
    paginationDto.setOffset(0);
    paginationDto.setLimit(20);
    return reportClient.evaluateDecisionRawReport(reportData, paginationDto);
  }

  private void assertInputVariablesMatchExcepted(final HashMap<String, InputVariableEntry> expectedVariables,
                                                 final Map<String, InputVariableEntry> actualVariables) {
    assertThat(actualVariables).hasSize(expectedVariables.size());
    assertThat(actualVariables.keySet()).isEqualTo(expectedVariables.keySet());
    expectedVariables.forEach((variableId, variableEntry) -> {
      final InputVariableEntry variable = actualVariables.get(variableId);
      assertThat(variable.getId()).isEqualTo(variableEntry.getId());
      assertThat(variable.getName()).isEqualTo(variableEntry.getName());
      assertThat(variable.getType()).isEqualTo(variableEntry.getType());
      assertThat(variable.getValue()).isEqualTo(String.valueOf(variableEntry.getValue()));
    });
  }

  private void assertOutputVariablesMatchExcepted(final HashMap<String, OutputVariableEntry> expectedVariables,
                                                  final Map<String, OutputVariableEntry> actualVariables) {
    assertThat(actualVariables).hasSize(expectedVariables.size());
    assertThat(
      actualVariables.keySet()).isEqualTo(expectedVariables.keySet());
    expectedVariables.forEach((variableId, variableEntry) -> {
      final OutputVariableEntry variable = actualVariables.get(variableId);
      assertThat(variable.getId()).isEqualTo(variableEntry.getId());
      assertThat(variable.getName()).isEqualTo(variableEntry.getName());
      assertThat(variable.getType()).isEqualTo(variableEntry.getType());
      assertThat(variable.getValues())
        .containsExactlyInAnyOrder(variableEntry.getValues().stream().map(String::valueOf).toArray());
    });
  }

  private DecisionDefinitionEngineDto deployDishDecisionDefinition() {
    return engineIntegrationExtension.deployDecisionDefinition(createDecideDishDecisionDefinition());
  }

  private void startDishDecisionInstance(final DecisionDefinitionEngineDto decisionDefinitionEngineDto) {
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionEngineDto.getId(),
      new HashMap<String, Object>() {{
        put("season", "Fall");
        put("guestCount", 1);
        put("guestsWithChildren", true);
      }}
    );
  }

  private HashMap<String, OutputVariableEntry> createOutputs(final boolean auditValue,
                                                             final String classificationValue) {
    return new HashMap<String, OutputVariableEntry>() {{
      put(OUTPUT_AUDIT_ID, new OutputVariableEntry(OUTPUT_AUDIT_ID, "Audit", VariableType.BOOLEAN, auditValue));
      put(
        OUTPUT_CLASSIFICATION_ID,
        new OutputVariableEntry(OUTPUT_CLASSIFICATION_ID, "Classification", VariableType.STRING, classificationValue)
      );
    }};
  }

  private DecisionReportDataDto createReport(final String decisionDefinitionKey,
                                             final String decisionDefinitionVersion) {
    return DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(decisionDefinitionKey)
      .setDecisionDefinitionVersion(decisionDefinitionVersion)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
  }

}

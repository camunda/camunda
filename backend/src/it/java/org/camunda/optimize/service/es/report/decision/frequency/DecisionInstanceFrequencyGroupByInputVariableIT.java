/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.decision.frequency;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createNumericInputVariableFilter;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_CATEGORY_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_INVOICE_DATE_ID;
import static org.camunda.optimize.util.DmnModels.createDecisionDefinitionWithDate;
import static org.camunda.optimize.util.DmnModels.createDefaultDmnModel;

public class DecisionInstanceFrequencyGroupByInputVariableIT extends AbstractDecisionDefinitionIT {

  @Test
  public void reportEvaluationSingleBucketSpecificVersionGroupByNumberInputVariable() {
    // given
    final String amountValue = "100.0";
    final Double amountValueAsDouble = Double.valueOf(amountValue);
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1, decisionDefinitionVersion1, INPUT_AMOUNT_ID, VariableType.DOUBLE
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData().stream().mapToDouble(MapResultEntryDto::getValue).sum()).isEqualTo(2L);
  }

  @Test
  public void reportEvaluationMultiBucketSpecificVersionGroupByDateInputVariable() {
    // given
    final String dateGroupKey = "2018-01-01T00:00:00.000+0100";
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension
      .deployDecisionDefinition(createDecisionDefinitionWithDate());
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(),
      createInputsWithDate(100.0, dateGroupKey)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(),
      createInputsWithDate(100.0, dateGroupKey)
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1, decisionDefinitionVersion1, INPUT_INVOICE_DATE_ID, VariableType.DATE
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(result.getFirstMeasureData().get(0).getKey()).isEqualTo(dateGroupKey);
    assertThat(result.getFirstMeasureData().get(0).getValue()).isEqualTo(2.);
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupByNumberInputVariable() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1, decisionDefinitionDto1.getVersionAsString(), INPUT_AMOUNT_ID, VariableType.DOUBLE
    ).getResult();

    // then the result includes all instance data
    assertThat(result.getInstanceCount()).isEqualTo(6L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData.stream().mapToDouble(MapResultEntryDto::getValue).sum()).isEqualTo(6L);
  }

  @Test
  public void reportEvaluationMultiBucketsGroupByNumberInputVariable_customBuckets() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1,
      decisionDefinitionDto1.getVersionAsString(),
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE,
      10.0,
      100.0
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(6L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(3);
    assertThat(resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList()))
      .containsExactly("10.00", "110.00", "210.00");
    assertThat(resultData.get(0).getValue()).isEqualTo(3.);
    assertThat(resultData.get(1).getValue()).isEqualTo(2.);
    assertThat(resultData.get(2).getValue()).isEqualTo(1.);
  }

  @Test
  public void reportEvaluationMultiBucketGroupByNumberInputVariable_invalidBaseline_returnsEmptyResult() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(10.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(20.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when an offset larger than the max. variable value (20) is used
    final List<MapResultEntryDto> resultData = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1,
      decisionDefinitionDto1.getVersionAsString(),
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE,
      30.0,
      5.0
    ).getResult().getFirstMeasureData();

    // then the result is empty
    assertThat(resultData).isNotNull();
    assertThat(resultData).isEmpty();
  }

  @Test
  public void reportEvaluationMultiBucket_negativeVariableValues_defaultBaselineWorks() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(-1.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(-5.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when there is no baseline set
    final List<MapResultEntryDto> resultData = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1,
      decisionDefinitionDto1.getVersionAsString(),
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE
    ).getResult().getFirstMeasureData();

    // then the result includes all instances
    assertThat(resultData).isNotNull();
    assertThat(resultData.stream().mapToDouble(MapResultEntryDto::getValue).sum()).isEqualTo(2.0);
  }

  @Test
  public void reportEvaluationMultiBucket_doubleVariable_bucketKeysHaveTwoDecimalPlaces() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(1.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(5.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when there is no baseline set
    final List<MapResultEntryDto> resultData = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1,
      decisionDefinitionDto1.getVersionAsString(),
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE
    ).getResult().getFirstMeasureData();

    // then the result includes all instances
    assertThat(resultData).isNotNull();
    assertThat(resultData)
      .extracting(MapResultEntryDto::getKey)
      .allMatch(key -> key.length() - key.indexOf(".") - 1 == 2); // key should have two chars after the decimal
  }

  @Test
  public void reportEvaluationMultiBucket_numberVariable_notTooManyAutomaticBuckets() {
    // given
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(9100000000000000000., "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(-9200000000000000000., "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final List<MapResultEntryDto> resultData = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1,
      decisionDefinitionDto1.getVersionAsString(),
      INPUT_AMOUNT_ID,
      VariableType.DOUBLE
    ).getResult().getFirstMeasureData();

    // then the amount of buckets does not exceed NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION
    // (a precaution to avoid too many buckets for distributed reports)
    assertThat(resultData)
      .isNotNull()
      .isNotEmpty()
      .hasSizeLessThanOrEqualTo(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
  }

  @Test
  public void testCustomOrderOnNumberResultKeyIsApplied() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .setVariableId(INPUT_AMOUNT_ID)
      .setVariableType(VariableType.DOUBLE)
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then the result covers all instances and is sorted
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData.stream().mapToDouble(MapResultEntryDto::getValue).sum()).isEqualTo(6L);
    final List<String> resultDataKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultDataKeys).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  public void testCustomOrderOnNumberResultValueIsApplied() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .setVariableId(INPUT_AMOUNT_ID)
      .setVariableType(VariableType.DOUBLE)
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then the result includes all instance data and is sorted
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData.stream().mapToDouble(MapResultEntryDto::getValue).sum()).isEqualTo(6L);
    final List<Double> bucketValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(bucketValues).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupByNumberInputVariableFilterByValue() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .setVariableId(INPUT_AMOUNT_ID)
      .setVariableType(VariableType.DOUBLE)
      .setFilter(Lists.newArrayList(createNumericInputVariableFilter(
        INPUT_AMOUNT_ID, IN, "200.0"
      )))
      .build();

    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData.stream().mapToDouble(MapResultEntryDto::getValue).sum()).isEqualTo(2L);
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByNumberInputVariable() {
    // given
    final String amountValue = "100.00";
    final Double amountValueAsDouble = Double.valueOf(amountValue);
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );

    // different version
    engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, INPUT_AMOUNT_ID, VariableType.DOUBLE
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(4L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(result.getFirstMeasureData().get(0).getKey()).isEqualTo(amountValue);
    assertThat(result.getFirstMeasureData().get(0).getValue()).isEqualTo(4.);
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByStringInputVariable() {
    // given
    final String categoryValue = "Misc";
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, categoryValue)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, categoryValue)
    );

    // different version
    engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, categoryValue)
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, categoryValue)
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, INPUT_CATEGORY_ID, VariableType.STRING
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(4L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(result.getFirstMeasureData().get(0).getKey()).isEqualTo(categoryValue);
    assertThat(result.getFirstMeasureData().get(0).getValue()).isEqualTo(4.);
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByNumberInputVariableOtherDefinitionsHaveNoSideEffect() {
    // given
    final String amountValue = "100.00";
    final Double amountValueAsDouble = Double.valueOf(amountValue);
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(amountValueAsDouble, "Misc")
    );

    // same decision definition with same inputs but different key
    final DecisionDefinitionEngineDto otherDecisionDefinition = deployDefaultDecisionDefinitionWithDifferentKey(
      "otherKey");
    startDecisionInstanceWithInputVars(
      otherDecisionDefinition.getId(), createInputs(amountValueAsDouble, "Misc")
    );
    startDecisionInstanceWithInputVars(
      otherDecisionDefinition.getId(), createInputs(amountValueAsDouble, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, INPUT_AMOUNT_ID, VariableType.DOUBLE
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(result.getFirstMeasureData().get(0).getKey()).isEqualTo(amountValue);
    assertThat(result.getFirstMeasureData().get(0).getValue()).isEqualTo(2.);
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Lists.newArrayList(tenantId1);
    final String decisionDefinitionKey = deployAndStartMultiTenantDefinition(
      Lists.newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionKey)
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setTenantIds(selectedTenants)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .setVariableId(INPUT_AMOUNT_ID)
      .setVariableType(VariableType.DOUBLE)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void testVariableNameIsAvailable() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());


    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> result =
      evaluateDecisionInstanceFrequencyByInputVariable(
        decisionDefinitionDto1, decisionDefinitionVersion1, INPUT_AMOUNT_ID, "amount", VariableType.DOUBLE
      );

    // then
    final DecisionGroupByVariableValueDto value = (DecisionGroupByVariableValueDto)
      result.getReportDefinition().getData().getGroupBy().getValue();
    assertThat(value.getName()).isPresent();
    assertThat(value.getName().get()).isEqualTo("amount");
  }

  @Test
  public void dateVariableGroupByWithOneInstance() {
    // given
    final String inputClauseId = "inputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleInputDecisionDefinition(
      inputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );

    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    engineIntegrationExtension.startDecisionInstance(definition.getId(), ImmutableMap.of(camInputVariable, now));

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      definition, definition.getVersionAsString(), inputClauseId, null, VariableType.DATE
    ).getResult();

    // then
    final String expectedKey = embeddedOptimizeExtension.formatToHistogramBucketKey(
      now.atZoneSimilarLocal(ZoneId.systemDefault()).toOffsetDateTime(),
      ChronoUnit.MONTHS
    );
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(result.getFirstMeasureData().get(0).getValue()).isEqualTo(1.);
    assertThat(result.getFirstMeasureData().get(0).getKey()).isEqualTo(expectedKey);
  }

  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void dateVariableGroupByWorksWithAllStaticUnits(final AggregateByDateUnit unit) {
    // given
    final int numberOfInstances = 3;
    final String inputClauseId = "inputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleInputDecisionDefinition(
      inputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    OffsetDateTime dateVariableValue = OffsetDateTime.parse("2020-06-15T00:00:00+02:00");

    for (int i = 0; i < numberOfInstances; i++) {
      dateVariableValue = dateVariableValue.plus(1, chronoUnit);
      Map<String, Object> variables = ImmutableMap.of(camInputVariable, dateVariableValue);
      engineIntegrationExtension.startDecisionInstance(definition.getId(), variables);
    }

    importAllEngineEntitiesFromScratch();

    // when
    final List<MapResultEntryDto> resultData = evaluateDecisionInstanceFrequencyByInputVariable(
      definition, definition.getVersionAsString(), inputClauseId, null, VariableType.DATE, unit
    ).getResult().getFirstMeasureData();

    // then
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(numberOfInstances);
    dateVariableValue = dateVariableValue.minus(numberOfInstances - 1, chronoUnit); // reset to first variable value
    for (int i = 0; i < numberOfInstances; i++) {
      final String expectedBucketKey = embeddedOptimizeExtension.formatToHistogramBucketKey(
        dateVariableValue.plus(chronoUnit.getDuration().multipliedBy(i)),
        chronoUnit
      );
      assertThat(resultData.get(i).getValue()).isEqualTo(1);
      assertThat(resultData.get(i).getKey()).isEqualTo(expectedBucketKey);
    }
  }

  @SneakyThrows
  @Test
  public void dateVariableGroupByWithAutomaticIntervals() {
    // given
    final int numberOfInstances = 3;
    final String inputClauseId = "inputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleInputDecisionDefinition(
      inputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );
    OffsetDateTime dateVariableValue = OffsetDateTime.now();

    for (int i = 0; i < numberOfInstances; i++) {
      dateVariableValue = dateVariableValue.plusMinutes(1);
      Map<String, Object> variables =
        ImmutableMap.of(camInputVariable, dateVariableValue);
      engineIntegrationExtension.startDecisionInstance(definition.getId(), variables);
    }

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      definition, definition.getVersionAsString(), inputClauseId, null, VariableType.DATE, AggregateByDateUnit.AUTOMATIC
    ).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);

    // the bucket span covers the earliest and the latest date variable values
    DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
    final OffsetDateTime startOfFirstBucket = OffsetDateTime.from(formatter.parse(resultData.get(0).getKey()));
    final OffsetDateTime startOfLastBucket = OffsetDateTime
      .from(formatter.parse(resultData.get(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1).getKey()));
    final OffsetDateTime firstTruncatedDateVariableValue = dateVariableValue.truncatedTo(ChronoUnit.MILLIS);
    final OffsetDateTime lastTruncatedDateVariableValue =
      dateVariableValue.minusMinutes(numberOfInstances).truncatedTo(ChronoUnit.MILLIS);

    assertThat(startOfFirstBucket).isBeforeOrEqualTo(firstTruncatedDateVariableValue);
    assertThat(startOfLastBucket).isAfterOrEqualTo(lastTruncatedDateVariableValue);
  }

  @SneakyThrows
  @Test
  public void dateVariableGroupByWithAutomaticIntervals_MissingInstancesReturnsEmptyResult() {
    // given
    final String inputClauseId = "inputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleInputDecisionDefinition(
      inputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      definition, definition.getVersionAsString(), inputClauseId, null, VariableType.DATE, AggregateByDateUnit.AUTOMATIC
    ).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull().isEmpty();
  }

  @Test
  public void dateVariableGroupByWithSeveralInstances() {
    // given
    final String inputClauseId = "inputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleInputDecisionDefinition(
      inputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );

    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();

    engineIntegrationExtension.startDecisionInstance(definition.getId(), ImmutableMap.of(camInputVariable, now));
    engineIntegrationExtension.startDecisionInstance(
      definition.getId(),
      ImmutableMap.of(camInputVariable, now.minusDays(1L))
    );
    engineIntegrationExtension.startDecisionInstance(
      definition.getId(),
      ImmutableMap.of(camInputVariable, now.minusDays(1L))
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      definition, definition.getVersionAsString(), inputClauseId, null, VariableType.DATE
    ).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertThat(resultData.get(resultData.size() - 1).getValue()).isEqualTo(1.);
    assertThat(resultData.get(0).getValue()).isEqualTo(2.);
  }

  @Test
  public void dateVariablesAreSortedAscByDefault() {
    // given
    final String inputClauseId = "inputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleInputDecisionDefinition(
      inputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );

    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    engineIntegrationExtension.startDecisionInstance(definition.getId(), ImmutableMap.of(camInputVariable, now));
    engineIntegrationExtension.startDecisionInstance(
      definition.getId(),
      ImmutableMap.of(camInputVariable, now.minusSeconds(1L))
    );
    engineIntegrationExtension.startDecisionInstance(
      definition.getId(),
      ImmutableMap.of(camInputVariable, now.minusSeconds(6L))
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      definition, definition.getVersionAsString(), inputClauseId, null, VariableType.DATE
    ).getResult();


    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void missingVariablesAggregationForUndefinedAndNullInputVariables_string() {
    // given
    final String inputClauseId = "TestyTest";
    final String camInputVariable = "putIn";

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleInputDecisionDefinition(
      inputClauseId,
      camInputVariable,
      DecisionTypeRef.STRING
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "testValidMatch")
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "whateverElse")
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(camInputVariable, null)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(camInputVariable, new EngineVariableValue(null, "String"))
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto, decisionDefinitionDto.getVersionAsString(), inputClauseId, null, VariableType.STRING
    ).getResult();


    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "testValidMatch").get().getValue()).isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "whateverElse").get().getValue()).isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "missing").get().getValue()).isEqualTo(2.);
  }

  @Test
  public void missingVariablesAggregationNullVariableOfTypeDouble_sortingByKeyDoesNotFail() {
    // given one decision instance with non null variable and one with null variable
    final String inputVariableName = "inputVariableName";
    final String outputVariableName = "outPutVariableName";
    final Double doubleVarValue = 1.0;

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleInputDecisionDefinition(
      inputVariableName,
      outputVariableName,
      DecisionTypeRef.DOUBLE
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(outputVariableName, doubleVarValue)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(outputVariableName, null)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(outputVariableName, new EngineVariableValue(null, "Double"))
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto, decisionDefinitionDto.getVersionAsString(), inputVariableName, null, VariableType.DOUBLE
    ).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "1.00").get().getValue()).isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "missing").get().getValue()).isEqualTo(2.);
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .build();
    reportData.getView().setProperties((ViewProperty) null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .build();
    reportData.getGroupBy().setType(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private DecisionDefinitionEngineDto deployDefaultDecisionDefinitionWithDifferentKey(final String key) {
    final DmnModelInstance dmnModelInstance = createDefaultDmnModel();
    dmnModelInstance.getDefinitions().getDrgElements().stream()
      .findFirst()
      .ifPresent(drgElement -> drgElement.setId(key));
    return engineIntegrationExtension.deployDecisionDefinition(dmnModelInstance);
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByInputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final VariableType variableType) {
    return evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto,
      decisionDefinitionVersion,
      variableId,
      null,
      variableType
    );
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByInputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final VariableType variableType,
    final Double baseline,
    final Double numberVariableBucketSize) {
    return evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto,
      decisionDefinitionVersion,
      variableId,
      null,
      variableType,
      AggregateByDateUnit.AUTOMATIC,
      baseline,
      numberVariableBucketSize
    );
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByInputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final String variableName,
    final VariableType variableType) {
    return evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto,
      decisionDefinitionVersion,
      variableId,
      variableName,
      variableType,
      AggregateByDateUnit.AUTOMATIC,
      null,
      null
    );
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByInputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final String variableName,
    final VariableType variableType,
    final AggregateByDateUnit dateUnit) {
    return evaluateDecisionInstanceFrequencyByInputVariable(
      decisionDefinitionDto,
      decisionDefinitionVersion,
      variableId,
      variableName,
      variableType,
      dateUnit,
      null,
      null
    );
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByInputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final String variableName,
    final VariableType variableType,
    final AggregateByDateUnit dateUnit,
    final Double baseline,
    final Double numberVariableBucketSize) {
    DecisionReportDataDto reportData = createReportDataDto(
      decisionDefinitionDto,
      decisionDefinitionVersion,
      variableId,
      variableName,
      variableType
    );
    reportData.getConfiguration().setGroupByDateVariableUnit(dateUnit);
    reportData.getConfiguration().getCustomBucket().setActive(true);
    reportData.getConfiguration().getCustomBucket().setBaseline(baseline);
    reportData.getConfiguration().getCustomBucket().setBucketSize(numberVariableBucketSize);
    return reportClient.evaluateMapReport(reportData);
  }

  private DecisionReportDataDto createReportDataDto(final DecisionDefinitionEngineDto decisionDefinitionDto,
                                                    final String decisionDefinitionVersion,
                                                    final String variableId,
                                                    final String variableName,
                                                    final VariableType variableType) {
    return DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_INPUT_VARIABLE)
      .setVariableId(variableId)
      .setVariableName(variableName)
      .setVariableType(variableType)
      .build();
  }
}

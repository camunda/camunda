/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.decision.frequency;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
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
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.camunda.optimize.test.util.decision.DmnModelGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createBooleanOutputVariableFilter;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.DmnModels.OUTPUT_AUDIT_ID;
import static org.camunda.optimize.util.DmnModels.OUTPUT_CLASSIFICATION_ID;

public class DecisionInstanceFrequencyGroupByOutputVariableIT extends AbstractDecisionDefinitionIT {

  @Test
  public void reportEvaluationSingleBucketSpecificVersion_GroupByStringOutputVariable() {
    // given
    final String expectedClassificationOutputValue = "day-to-day expense";
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto1, decisionDefinitionVersion1, OUTPUT_CLASSIFICATION_ID, VariableType.STRING
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo(expectedClassificationOutputValue);
    assertThat(resultData.get(0).getValue()).isEqualTo(2.);
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersion_GroupByBooleanOutputVariable() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // audit = false
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // audit = true
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto1, decisionDefinitionVersion1, OUTPUT_AUDIT_ID, VariableType.BOOLEAN
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(4L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(2);
    assertThat(resultData.get(0).getKey()).isEqualTo("false");
    assertThat(resultData.get(0).getValue()).isEqualTo(3.);
    assertThat(resultData.get(1).getKey()).isEqualTo("true");
    assertThat(resultData.get(1).getValue()).isEqualTo(1.);
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersion_GroupByNumberOutputVariable() {
    // given
    final String outputVarName = "outputVarName";
    final String inputVarName = "inputVarName";

    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deploySimpleOutputDecisionDefinition(
      outputVarName,
      inputVarName,
      "-",
      DecisionTypeRef.DOUBLE
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto1.getId(),
      Collections.singletonMap(inputVarName, 100.0)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto1.getId(),
      Collections.singletonMap(inputVarName, 200.0)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto1.getId(),
      Collections.singletonMap(inputVarName, 200.0)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto1.getId(),
      Collections.singletonMap(inputVarName, 300.0)
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto1,
      decisionDefinitionDto1.getVersionAsString(),
      outputVarName,
      null,
      VariableType.DOUBLE
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(4L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData.stream().mapToDouble(MapResultEntryDto::getValue).sum()).isEqualTo(4L);
  }

  @Test
  public void reportEvaluationMultiBuckets_GroupByNumberOutputVariable_customBuckets() {
    // given
    final String outputVarName = "outputVarName";
    final String inputVarName = "inputVarName";

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleOutputDecisionDefinition(
      outputVarName,
      inputVarName,
      "-",
      DecisionTypeRef.DOUBLE
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, 100.0)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, 200.0)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, 200.0)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, 300.0)
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto,
      decisionDefinitionDto.getVersionAsString(),
      outputVarName,
      null,
      VariableType.DOUBLE,
      10.0,
      100.0
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(4L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(3);
    assertThat(resultData.stream().map(MapResultEntryDto::getKey).collect(toList()))
      .containsExactly("10.00", "110.00", "210.00");
    assertThat(resultData.get(0).getValue()).isEqualTo(1L);
    assertThat(resultData.get(1).getValue()).isEqualTo(2L);
    assertThat(resultData.get(2).getValue()).isEqualTo(1L);
  }

  @Test
  public void reportEvaluationMultiBuckets_GroupByNumberOutputVariable_invalidBaseline_returnsEmptyResult() {
    // given
    final String outputVarName = "outputVarName";
    final String inputVarName = "inputVarName";

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleOutputDecisionDefinition(
      outputVarName,
      inputVarName,
      "-",
      DecisionTypeRef.DOUBLE
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, 10.0)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, 20.0)
    );

    importAllEngineEntitiesFromScratch();

    // when an offset larger than the max. variable value (20) is used
    final List<MapResultEntryDto> resultData = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto,
      decisionDefinitionDto.getVersionAsString(),
      outputVarName,
      null,
      VariableType.DOUBLE,
      30.0,
      5.0
    ).getResult().getFirstMeasureData();

    // then the result is empty
    assertThat(resultData).isNotNull();
    assertThat(resultData).isEmpty();
  }

  @Test
  public void multipleBuckets_negativeNumberVariable_defaultBaselineWorks() {
    // given
    final String outputVarName = "outputVarName";
    final String inputVarName = "inputVarName";

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleOutputDecisionDefinition(
      outputVarName,
      inputVarName,
      "-",
      DecisionTypeRef.INTEGER
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, -1)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, -5)
    );

    importAllEngineEntitiesFromScratch();

    // when there is no baseline set
    final List<MapResultEntryDto> resultData = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto,
      decisionDefinitionDto.getVersionAsString(),
      outputVarName,
      null,
      VariableType.INTEGER
    ).getResult().getFirstMeasureData();

    // then the result includes all instances
    assertThat(resultData).isNotNull();
    assertThat(resultData.stream().mapToDouble(MapResultEntryDto::getValue).sum()).isEqualTo(2.0);
  }

  @Test
  public void reportEvaluationMultiBucket_doubleVariable_bucketKeysHaveTwoDecimalPlaces() {
    // given
    final String outputVarName = "outputVarName";
    final String inputVarName = "inputVarName";

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleOutputDecisionDefinition(
      outputVarName,
      inputVarName,
      "-",
      DecisionTypeRef.DOUBLE
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, 1.0)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, 5.0)
    );

    importAllEngineEntitiesFromScratch();

    // when there is no baseline set
    final List<MapResultEntryDto> resultData = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto,
      decisionDefinitionDto.getVersionAsString(),
      outputVarName,
      null,
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
    // given
    final String outputVarName = "outputVarName";
    final String inputVarName = "inputVarName";

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleOutputDecisionDefinition(
      outputVarName,
      inputVarName,
      "-",
      DecisionTypeRef.DOUBLE
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, 9100000000000000000.)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, -9200000000000000000.)
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<MapResultEntryDto> resultData = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto,
      decisionDefinitionDto.getVersionAsString(),
      outputVarName,
      null,
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
  public void reportEvaluationMultiBucketsSpecificVersion_GroupByBooleanOutputVariable_FilterByVariable() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // audit = false
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // audit = true
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );


    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .setVariableId(OUTPUT_AUDIT_ID)
      .setVariableType(VariableType.BOOLEAN)
      .setFilter(Lists.newArrayList(createBooleanOutputVariableFilter(
        OUTPUT_AUDIT_ID, Collections.singletonList(true)
      )))
      .build();
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo("true");
    assertThat(resultData.get(0).getValue()).isEqualTo(1.);
  }

  @Test
  public void reportEvaluationSingleBucketAllVersions_GroupByStringOutputVariable() {
    // given
    final String expectedClassificationOutputValue = "day-to-day expense";
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    // different version
    engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, OUTPUT_CLASSIFICATION_ID, VariableType.STRING
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(4L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo(expectedClassificationOutputValue);
    assertThat(resultData.get(0).getValue()).isEqualTo(4.);
  }

  @Test
  public void reportEvaluationSingleBucketAllVersions_GroupByBooleanOutputVariable() {
    // given
    final String auditValue = "false";
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    // different version
    engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, OUTPUT_AUDIT_ID, VariableType.BOOLEAN
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(4L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo(auditValue);
    assertThat(resultData.get(0).getValue()).isEqualTo(4.);
  }

  @Test
  public void reportEvaluationSingleBucketAllVersions_GroupByBooleanInputVariable_OtherDefinitionsHaveNoSideEffect() {
    // given
    final String auditValue = "false";
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    // different decision definition
    final DecisionDefinitionEngineDto otherDecisionDefinition = deployDecisionDefinitionWithDifferentKey("otherKey");
    startDecisionInstanceWithInputVars(
      otherDecisionDefinition.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      otherDecisionDefinition.getId(), createInputs(100.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, OUTPUT_AUDIT_ID, VariableType.BOOLEAN
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo(auditValue);
    assertThat(resultData.get(0).getValue()).isEqualTo(2.);
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
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .setVariableId(OUTPUT_CLASSIFICATION_ID)
      .setVariableType(VariableType.STRING)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void testCustomOrderOnStringOutputResultKeyIsApplied() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // classification: day-to-day expense
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    // classification: budget
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(250.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(900.0, "Misc")
    );
    // classification: exceptional
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(25000.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .setVariableId(OUTPUT_CLASSIFICATION_ID)
      .setVariableType(VariableType.STRING)
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  public void testCustomOrderOnStringResultValueIsApplied() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // classification: day-to-day expense
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    // classification: budget
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(250.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(900.0, "Misc")
    );
    // classification: exceptional
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(25000.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .setVariableId(OUTPUT_CLASSIFICATION_ID)
      .setVariableType(VariableType.STRING)
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<Double> bucketValues = resultData.stream().map(MapResultEntryDto::getValue).collect(toList());
    assertThat(bucketValues).isSortedAccordingTo(Comparator.naturalOrder());
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
      evaluateDecisionInstanceFrequencyByOutputVariable(
        decisionDefinitionDto1, decisionDefinitionVersion1, OUTPUT_AUDIT_ID, "audit", VariableType.BOOLEAN
      );

    // then
    final DecisionGroupByVariableValueDto value = (DecisionGroupByVariableValueDto)
      result.getReportDefinition().getData().getGroupBy().getValue();
    assertThat(value.getName()).isPresent();
    assertThat(value.getName().get()).isEqualTo("audit");
  }

  @Test
  public void dateVariableGroupByWithOneInstance() {
    // given
    final String outputClauseId = "outputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleDecisionDefinition(
      outputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );

    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    engineIntegrationExtension.startDecisionInstance(definition.getId(), ImmutableMap.of(camInputVariable, now));

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      definition, definition.getVersionAsString(), outputClauseId, null, VariableType.DATE
    ).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(result.getFirstMeasureData().get(0).getValue()).isEqualTo(1.);
  }

  @Test
  public void dateVariableGroupByWithSeveralInstances() {
    // given
    final String outputClauseId = "outputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleDecisionDefinition(
      outputClauseId,
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      definition, definition.getVersionAsString(), outputClauseId, null, VariableType.DATE
    ).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertThat(resultData.get(0).getValue()).isEqualTo(2.);
    assertThat(resultData.get(resultData.size() - 1).getValue()).isEqualTo(1.);
  }

  @Test
  public void dateVariablesAreSortedAscByDefault() {
    // given
    final String outputClauseId = "outputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleDecisionDefinition(
      outputClauseId,
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      definition, definition.getVersionAsString(), outputClauseId, null, VariableType.DATE
    ).getResult();


    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void dateVariableGroupByWorksWithAllStaticUnits(final AggregateByDateUnit unit) {
    // given
    final int numberOfInstances = 3;
    final String outputClauseId = "outputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleDecisionDefinition(
      outputClauseId,
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
    final List<MapResultEntryDto> resultData = evaluateDecisionInstanceFrequencyByOutputVariable(
      definition, definition.getVersionAsString(), outputClauseId, null, VariableType.DATE, unit
    ).getResult().getFirstMeasureData();

    // then
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(numberOfInstances);
    // start at variable value of first instance due to default ascending order
    dateVariableValue = dateVariableValue.minus(numberOfInstances - 1, chronoUnit);
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
    final String outputClauseId = "outputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleDecisionDefinition(
      outputClauseId,
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      definition, definition.getVersionAsString(), outputClauseId, null, VariableType.DATE, AggregateByDateUnit.AUTOMATIC
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
    final String outputClauseId = "outputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleDecisionDefinition(
      outputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      definition, definition.getVersionAsString(), outputClauseId, null, VariableType.DATE, AggregateByDateUnit.AUTOMATIC
    ).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).isEmpty();
  }

  @Test
  public void missingVariablesAggregationForUndefinedAndNullOutputVariables() {
    // given
    final String outputClauseId = "Donald";
    final String camInputVariable = "input";

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleOutputDecisionDefinition(
      outputClauseId,
      camInputVariable,
      "testValidMatch",
      DecisionTypeRef.STRING
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "testValidMatch")
    );
    engineDatabaseExtension.setDecisionOutputStringVariableValueToNull(outputClauseId, "testValidMatch");

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "testValidMatch")
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(camInputVariable, null)
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto, decisionDefinitionDto.getVersionAsString(), outputClauseId, null, VariableType.STRING
    ).getResult();


    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "testValidMatch").get().getValue()).isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "missing").get().getValue()).isEqualTo(2.);
  }

  @Test
  public void missingVariablesAggregationForNullVariableOfTypeDouble_sortingByKeyDoesNotFail() {
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

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto, decisionDefinitionDto.getVersionAsString(), outputVarName, null, VariableType.DOUBLE
    ).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "1.00").get().getValue()).isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "missing").get().getValue()).isEqualTo(1.);
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
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
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .build();
    reportData.getGroupBy().setType(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByOutputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final VariableType variableType) {
    return evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto, decisionDefinitionVersion, variableId, null, variableType
    );
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByOutputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final String variableName,
    final VariableType variableType) {
    DecisionReportDataDto reportData = createReportDataDto(
      decisionDefinitionDto,
      decisionDefinitionVersion,
      variableId,
      variableName,
      variableType
    );
    return reportClient.evaluateMapReport(reportData);
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByOutputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final String variableName,
    final VariableType variableType,
    final AggregateByDateUnit unit) {
    return evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto,
      decisionDefinitionVersion,
      variableId,
      variableName,
      variableType,
      unit,
      null,
      null
    );
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByOutputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final String variableName,
    final VariableType variableType,
    final Double baseline,
    final Double numberVariableBucketSize) {
    return evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto,
      decisionDefinitionVersion,
      variableId,
      variableName,
      variableType,
      AggregateByDateUnit.AUTOMATIC,
      baseline,
      numberVariableBucketSize
    );
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByOutputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final String variableName,
    final VariableType variableType,
    final AggregateByDateUnit unit,
    final Double baseline,
    final Double numberVariableBucketSize) {
    DecisionReportDataDto reportData = createReportDataDto(
      decisionDefinitionDto,
      decisionDefinitionVersion,
      variableId,
      variableName,
      variableType
    );
    reportData.getConfiguration().setGroupByDateVariableUnit(unit);
    reportData.getConfiguration().getCustomBucket().setActive(true);
    reportData.getConfiguration().getCustomBucket().setBucketSize(numberVariableBucketSize);
    reportData.getConfiguration().getCustomBucket().setBaseline(baseline);
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
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .setVariableId(variableId)
      .setVariableName(variableName)
      .setVariableType(variableType)
      .build();
  }

  private DecisionDefinitionEngineDto deploySimpleDecisionDefinition(final String outputClauseId,
                                                                     final String camInputVariable,
                                                                     final DecisionTypeRef type) {
    final DmnModelGenerator dmnModelGenerator = DmnModelGenerator.create()
      .decision()
      .addInput("input", camInputVariable, type)
      .addOutput("output", outputClauseId, camInputVariable, type)
      .rule()
      .addStringInputEntry(camInputVariable)
      .addStringOutputEntry(camInputVariable)
      .buildRule()
      .buildDecision();
    return engineIntegrationExtension.deployDecisionDefinition(dmnModelGenerator.build());
  }
}

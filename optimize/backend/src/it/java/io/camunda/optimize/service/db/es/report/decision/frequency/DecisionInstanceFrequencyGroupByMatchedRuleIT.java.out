/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.report.decision.frequency;
//
// import static io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
// import static io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createNumericInputVariableFilter;
// import static io.camunda.optimize.util.DmnModels.BEVERAGES_RULE_1_ID;
// import static io.camunda.optimize.util.DmnModels.BEVERAGES_RULE_2_ID;
// import static io.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;
// import static io.camunda.optimize.util.DmnModels.INPUT_GUEST_WITH_CHILDREN_ID;
// import static io.camunda.optimize.util.DmnModels.INPUT_NUMBER_OF_GUESTS_ID;
// import static io.camunda.optimize.util.DmnModels.INPUT_SEASON_ID;
// import static io.camunda.optimize.util.DmnModels.INVOICE_RULE_1_ID;
// import static io.camunda.optimize.util.DmnModels.INVOICE_RULE_2_ID;
// import static io.camunda.optimize.util.DmnModels.INVOICE_RULE_3_ID;
// import static io.camunda.optimize.util.DmnModels.INVOICE_RULE_4_ID;
// import static io.camunda.optimize.util.DmnModels.createDecideDishDecisionDefinition;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.google.common.collect.Lists;
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.ReportConstants;
// import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
// import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
// import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
// import io.camunda.optimize.dto.optimize.query.variable.VariableType;
// import
// io.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.service.db.es.report.decision.AbstractDecisionDefinitionIT;
// import io.camunda.optimize.service.db.es.report.util.MapResultUtil;
// import io.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
// import io.camunda.optimize.test.util.decision.DecisionReportDataType;
// import jakarta.ws.rs.core.Response;
// import java.util.Comparator;
// import java.util.HashMap;
// import java.util.List;
// import java.util.stream.Collectors;
// import org.junit.jupiter.api.Test;
//
// public class DecisionInstanceFrequencyGroupByMatchedRuleIT extends AbstractDecisionDefinitionIT {
//
//   @Test
//   public void reportEvaluationMultiBucketsSpecificVersionGroupByMatchedRule() {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto1 =
//         engineIntegrationExtension.deployDecisionDefinition();
//     final String decisionDefinitionVersion1 =
// String.valueOf(decisionDefinitionDto1.getVersion());
//     // rule 1
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(100.0,
// "Misc"));
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(200.0,
// "Misc"));
//     // rule 2
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(300.0,
// "Misc"));
//     // rule 3
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc"));
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc"));
//     // rule 4
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses"));
//
//     // different version
//     DecisionDefinitionEngineDto decisionDefinitionDto2 =
//         engineIntegrationExtension.deployAndStartDecisionDefinition();
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         evaluateDecisionInstanceFrequencyByMatchedRule(
//                 decisionDefinitionDto1, decisionDefinitionVersion1)
//             .getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(6L);
//
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(4);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_1_ID)
//                 .get()
//                 .getValue())
//         .isEqualTo(2.);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_2_ID)
//                 .get()
//                 .getValue())
//         .isEqualTo(1.);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_3_ID)
//                 .get()
//                 .getValue())
//         .isEqualTo(2.);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_4_ID)
//                 .get()
//                 .getValue())
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void testCustomOrderOnMatchedRuleKeyIsApplied() {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto1 =
//         engineIntegrationExtension.deployDecisionDefinition();
//     final String decisionDefinitionVersion1 =
// String.valueOf(decisionDefinitionDto1.getVersion());
//     // rule 1
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(100.0,
// "Misc"));
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(200.0,
// "Misc"));
//     // rule 2
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(300.0,
// "Misc"));
//     // rule 3
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc"));
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc"));
//     // rule 4
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses"));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final DecisionReportDataDto reportData =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
//             .setDecisionDefinitionVersion(decisionDefinitionVersion1)
//             .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
//             .build();
//     reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
//     assertThat(resultData).hasSize(4);
//     final List<String> resultKeys =
//         resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
//     assertThat(resultKeys).isSortedAccordingTo(Comparator.reverseOrder());
//   }
//
//   @Test
//   public void testCustomOrderOnMatchedRuleValueIsApplied() {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto1 =
//         engineIntegrationExtension.deployDecisionDefinition();
//     final String decisionDefinitionVersion1 =
// String.valueOf(decisionDefinitionDto1.getVersion());
//     // rule 1
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(100.0,
// "Misc"));
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(200.0,
// "Misc"));
//     // rule 2
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(300.0,
// "Misc"));
//     // rule 3
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc"));
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc"));
//     // rule 4
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses"));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final DecisionReportDataDto reportData =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
//             .setDecisionDefinitionVersion(decisionDefinitionVersion1)
//             .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
//             .build();
//     reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
//     assertThat(resultData).hasSize(4);
//     final List<Double> bucketValues =
//         resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
//     assertThat(bucketValues).isSortedAccordingTo(Comparator.naturalOrder());
//   }
//
//   @Test
//   public void reportEvaluationMultiBucketsAllVersionsGroupByMatchedRule() {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto1 =
//         engineIntegrationExtension.deployDecisionDefinition();
//     // rule 1
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(100.0,
// "Misc"));
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(200.0,
// "Misc"));
//     // rule 2
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(300.0,
// "Misc"));
//     // rule 3
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc"));
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createInputs(3000.0, "Misc"));
//     // rule 4
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createInputs(3000.0, "Travel Expenses"));
//
//     // different version
//     DecisionDefinitionEngineDto decisionDefinitionDto2 =
//         engineIntegrationExtension.deployDecisionDefinition();
//
//     // rule 1
//     startDecisionInstanceWithInputVars(decisionDefinitionDto2.getId(), createInputs(100.0,
// "Misc"));
//     startDecisionInstanceWithInputVars(decisionDefinitionDto2.getId(), createInputs(200.0,
// "Misc"));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         evaluateDecisionInstanceFrequencyByMatchedRule(
//                 decisionDefinitionDto1, ReportConstants.ALL_VERSIONS)
//             .getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(8L);
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(4);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_1_ID)
//                 .get()
//                 .getValue())
//         .isEqualTo(4.);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_2_ID)
//                 .get()
//                 .getValue())
//         .isEqualTo(1.);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_3_ID)
//                 .get()
//                 .getValue())
//         .isEqualTo(2.);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), INVOICE_RULE_4_ID)
//                 .get()
//                 .getValue())
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void reportEvaluationMultiBucketsSpecificVersionGroupByMatchedRuleFilterByInput() {
//     // given
//     final double inputVariableValueToFilterFor = 300.0;
//     DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition();
//     // rule 1
//     startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(100.0,
// "Misc"));
//     startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(200.0,
// "Misc"));
//     // rule 2
//     startDecisionInstanceWithInputVars(decisionDefinitionDto.getId(), createInputs(300.0,
// "Misc"));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
//             .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
//             .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
//             .setFilter(
//                 createNumericInputVariableFilter(
//                     INPUT_AMOUNT_ID,
//                     FilterOperator.GREATER_THAN_EQUALS,
//                     String.valueOf(inputVariableValueToFilterFor)))
//             .build();
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(1L);
//     final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//     assertThat(resultData).hasSize(1);
//     assertThat(resultData.get(0).getKey()).isEqualTo(INVOICE_RULE_2_ID);
//     assertThat(resultData.get(0).getValue()).isEqualTo(1.);
//   }
//
//   @Test
//   public void reportEvaluationOnCollectPolicyMultiBucketsAllVersionsGroupByMatchedRule() {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto1 = deployDishDecisionDefinition();
//     // triggers rule 1
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createDishInputs("Winter", 8, true));
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createDishInputs("Winter", 8, true));
//
//     // triggers rule 2
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto1.getId(), createDishInputs("Winter", 8, false));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         evaluateDecisionInstanceFrequencyByMatchedRule(
//                 decisionDefinitionDto1, ReportConstants.ALL_VERSIONS)
//             .getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(3L);
//     assertThat(result.getFirstMeasureData()).isNotNull();
//     assertThat(result.getFirstMeasureData()).hasSize(2);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), BEVERAGES_RULE_1_ID)
//                 .get()
//                 .getValue())
//         .isEqualTo(2.);
//     assertThat(
//             MapResultUtil.getEntryForKey(result.getFirstMeasureData(), BEVERAGES_RULE_2_ID)
//                 .get()
//                 .getValue())
//         .isEqualTo(1.);
//   }
//
//   @Test
//   public void
//       reportEvaluationSingleBucketAllVersionsGroupByMatchedRuleOtherDefinitionsHaveNoSideEffect()
// {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto1 =
//         engineIntegrationExtension.deployDecisionDefinition();
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(100.0,
// "Misc"));
//     startDecisionInstanceWithInputVars(decisionDefinitionDto1.getId(), createInputs(100.0,
// "Misc"));
//
//     // other decision definition
//     DecisionDefinitionEngineDto decisionDefinitionDto2 = deployDishDecisionDefinition();
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto2.getId(), createDishInputs("Winter", 8, true));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final ReportResultResponseDto<List<MapResultEntryDto>> result =
//         evaluateDecisionInstanceFrequencyByMatchedRule(
//                 decisionDefinitionDto1, ReportConstants.ALL_VERSIONS)
//             .getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(2L);
//     final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
//     assertThat(resultData).isNotNull();
//
// assertThat(resultData).hasSize(1).extracting(MapResultEntryDto::getValue).containsExactly(2.);
//   }
//
//   @Test
//   public void reportEvaluationSingleBucketFilteredBySingleTenant() {
//     // given
//     final String tenantId1 = "tenantId1";
//     final String tenantId2 = "tenantId2";
//     final List<String> selectedTenants = Lists.newArrayList(tenantId1);
//     final String decisionDefinitionKey =
//         deployAndStartMultiTenantDefinition(Lists.newArrayList(null, tenantId1, tenantId2));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey(decisionDefinitionKey)
//             .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
//             .setTenantIds(selectedTenants)
//             .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
//             .build();
//     ReportResultResponseDto<List<MapResultEntryDto>> result =
//         reportClient.evaluateMapReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
//   }
//
//   @Test
//   public void optimizeExceptionOnViewPropertyIsNull() {
//     // given
//     DecisionReportDataDto reportData =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey("key")
//             .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
//             .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
//             .build();
//     reportData.getView().setProperties((ViewProperty) null);
//
//     // when
//     Response response = reportClient.evaluateReportAndReturnResponse(reportData);
//
//     // then
//     assertThat(response.getStatus())
//         .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
//   }
//
//   @Test
//   public void optimizeExceptionOnGroupByTypeIsNull() {
//     // given
//     DecisionReportDataDto reportData =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey("key")
//             .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
//             .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
//             .build();
//     reportData.getGroupBy().setType(null);
//
//     // when
//     Response response = reportClient.evaluateReportAndReturnResponse(reportData);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
//   }
//
//   private DecisionDefinitionEngineDto deployDishDecisionDefinition() {
//     return engineIntegrationExtension.deployDecisionDefinition(
//         createDecideDishDecisionDefinition());
//   }
//
//   private HashMap<String, InputVariableEntry> createDishInputs(
//       final String season, final Integer guestCount, final boolean withChildren) {
//     return new HashMap<>() {
//       {
//         put(
//             INPUT_SEASON_ID,
//             new InputVariableEntry(INPUT_SEASON_ID, "Season", VariableType.STRING, season));
//         put(
//             INPUT_NUMBER_OF_GUESTS_ID,
//             new InputVariableEntry(
//                 INPUT_NUMBER_OF_GUESTS_ID, "Number of Guests", VariableType.INTEGER,
// guestCount));
//         put(
//             INPUT_GUEST_WITH_CHILDREN_ID,
//             new InputVariableEntry(
//                 INPUT_GUEST_WITH_CHILDREN_ID,
//                 "Guests with children?",
//                 VariableType.BOOLEAN,
//                 withChildren));
//       }
//     };
//   }
//
//   private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>>
//       evaluateDecisionInstanceFrequencyByMatchedRule(
//           final DecisionDefinitionEngineDto decisionDefinitionDto,
//           final String decisionDefinitionVersion) {
//     DecisionReportDataDto reportData =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
//             .setDecisionDefinitionVersion(decisionDefinitionVersion)
//             .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
//             .build();
//     return reportClient.evaluateMapReport(reportData);
//   }
// }

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.filter.decision.variable;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createDateInputVariableFilter;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createFixedDateInputVariableFilter;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRelativeDateInputVariableFilter;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRollingDateInputVariableFilter;
// import static io.camunda.optimize.util.DmnModels.INPUT_INVOICE_DATE_ID;
// import static io.camunda.optimize.util.DmnModels.createDecisionDefinitionWithDate;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.google.common.collect.Lists;
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
// import
// io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.FixedDateFilterDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RelativeDateFilterDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
// import io.camunda.optimize.dto.optimize.query.variable.VariableType;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.exception.OptimizeIntegrationTestException;
// import io.camunda.optimize.service.db.es.report.decision.AbstractDecisionDefinitionIT;
// import io.camunda.optimize.service.security.util.LocalDateUtil;
// import io.camunda.optimize.test.it.extension.EngineVariableValue;
// import io.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
// import io.camunda.optimize.test.util.decision.DecisionReportDataType;
// import io.camunda.optimize.test.util.decision.DecisionTypeRef;
// import java.time.OffsetDateTime;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.List;
// import java.util.function.Supplier;
// import java.util.stream.Stream;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.Arguments;
// import org.junit.jupiter.params.provider.MethodSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class DecisionDateVariableFilterIT extends AbstractDecisionDefinitionIT {
//
//   @BeforeEach
//   public void setup() {
//     LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2019-06-15T12:00:00+02:00"));
//   }
//
//   private static Stream<Arguments> nullFilterScenarios() {
//     return Stream.concat(
//         Arrays.stream(DateFilterType.values())
//             .flatMap(
//                 dateFilterType ->
//                     Stream.of(
//                         // filter for null/undefined only, includeUndefined=true
//                         Arguments.of(
//                             "Include Null/Undefined for type " + dateFilterType,
//                             createSupplier(
//                                 () ->
//                                     createDateFilterDataDto(dateFilterType)
//                                         .setIncludeUndefined(true)),
//                             2L),
//                         // filter for defined only, excludeUndefined=true
//                         Arguments.of(
//                             "Exclude Null/Undefined for type " + dateFilterType,
//                             createSupplier(
//                                 () ->
//                                     createDateFilterDataDto(dateFilterType)
//                                         .setExcludeUndefined(true)),
//                             3L))),
//         Stream.of(
//             // filter for particular values and includeUndefined=true
//             Arguments.of(
//                 "Include value and Null/Undefined for type " + DateFilterType.FIXED,
//                 createSupplier(
//                     () ->
//                         new FixedDateFilterDataDto(
//                                 LocalDateUtil.getCurrentDateTime(),
//                                 LocalDateUtil.getCurrentDateTime().plusMinutes(1))
//                             .setIncludeUndefined(true)),
//                 4L),
//             Arguments.of(
//                 "Include value and Null/Undefined for type " + DateFilterType.ROLLING,
//                 createSupplier(
//                     () ->
//                         new RollingDateFilterDataDto(
//                                 new RollingDateFilterStartDto(0L, DateUnit.MINUTES))
//                             .setIncludeUndefined(true)),
//                 3L),
//             Arguments.of(
//                 "Include value and Null/Undefined for type " + DateFilterType.RELATIVE,
//                 createSupplier(
//                     () ->
//                         new RelativeDateFilterDataDto(
//                                 new RelativeDateFilterStartDto(0L, DateUnit.MINUTES))
//                             .setIncludeUndefined(true)),
//                 3L)));
//   }
//
//   @ParameterizedTest(name = "{0}")
//   @MethodSource("nullFilterScenarios")
//   public void dateFilterSupportsNullValueInclusionAndExclusion(
//       final String scenarioName,
//       final Supplier<DateFilterDataDto<?>> dateFilterSupplier,
//       final long expectedInstanceCount) {
//     // given
//     final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
//     final String inputClauseId = "dateVar";
//     final String dateVarName = inputClauseId;
//     final DecisionDefinitionEngineDto decisionDefinitionDto =
//         deploySimpleInputDecisionDefinition(inputClauseId, dateVarName, DecisionTypeRef.STRING);
//
//     // instance where the variable is not defined
//     engineIntegrationExtension.startDecisionInstance(
//         decisionDefinitionDto.getId(), Collections.singletonMap(dateVarName, null));
//     // instance where the variable has the value null
//     engineIntegrationExtension.startDecisionInstance(
//         decisionDefinitionDto.getId(),
//         Collections.singletonMap(
//             dateVarName, new EngineVariableValue(null, VariableType.DATE.getId())));
//     engineIntegrationExtension.startDecisionInstance(
//         decisionDefinitionDto.getId(),
//         Collections.singletonMap(dateVarName, toDateString(now.minusMinutes(1))));
//     engineIntegrationExtension.startDecisionInstance(
//         decisionDefinitionDto.getId(), Collections.singletonMap(dateVarName, toDateString(now)));
//     engineIntegrationExtension.startDecisionInstance(
//         decisionDefinitionDto.getId(),
//         Collections.singletonMap(dateVarName, toDateString(now.plusMinutes(1))));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(
//         Lists.newArrayList(createDateInputVariableFilter(inputClauseId,
// dateFilterSupplier.get())));
//     final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
//   }
//
//   @Test
//   public void resultFilterByGreaterThanDateInputVariable() {
//     // given
//     final OffsetDateTime dateTimeInputFilterStart =
//         OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
//     final String inputVariableIdToFilterOn = INPUT_INVOICE_DATE_ID;
//
//     final DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition(createDecisionDefinitionWithDate());
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(), createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00"));
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(), createInputsWithDate(200.0, "2019-06-06T00:00:00+00:00"));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(
//         Lists.newArrayList(
//             createFixedDateInputVariableFilter(
//                 inputVariableIdToFilterOn, dateTimeInputFilterStart, null)));
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(1L);
//     assertThat(result.getData()).hasSize(1);
//
//     assertThat(
//             (String)
//                 result
//                     .getData()
//                     .get(0)
//                     .getInputVariables()
//                     .get(inputVariableIdToFilterOn)
//                     .getValue())
//         .startsWith("2019-06-06T00:00:00");
//   }
//
//   @Test
//   public void resultFilterByLessThanDateInputVariable() {
//     // given
//     final OffsetDateTime dateTimeInputFilterEnd =
// OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
//     final String inputVariableIdToFilterOn = INPUT_INVOICE_DATE_ID;
//
//     final DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition(createDecisionDefinitionWithDate());
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(), createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00"));
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(), createInputsWithDate(200.0, "2019-06-06T00:00:00+00:00"));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(
//         Lists.newArrayList(
//             createFixedDateInputVariableFilter(
//                 inputVariableIdToFilterOn, null, dateTimeInputFilterEnd)));
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(1L);
//     assertThat(result.getData()).hasSize(1);
//
//     assertThat(
//             (String)
//                 result
//                     .getData()
//                     .get(0)
//                     .getInputVariables()
//                     .get(inputVariableIdToFilterOn)
//                     .getValue())
//         .startsWith("2018-01-01T00:00:00");
//   }
//
//   @Test
//   public void resultFilterByDateRangeInputVariable() {
//     // given
//     final OffsetDateTime dateTimeInputFilterStart =
//         OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
//     final OffsetDateTime dateTimeInputFilterEnd =
// OffsetDateTime.parse("2019-02-01T00:00:00+00:00");
//     final String inputVariableIdToFilterOn = INPUT_INVOICE_DATE_ID;
//
//     final DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition(createDecisionDefinitionWithDate());
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(), createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00"));
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(), createInputsWithDate(200.0, "2019-01-01T01:00:00+00:00"));
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(), createInputsWithDate(300.0, "2019-06-06T00:00:00+00:00"));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(
//         Lists.newArrayList(
//             createFixedDateInputVariableFilter(
//                 inputVariableIdToFilterOn, dateTimeInputFilterStart, dateTimeInputFilterEnd)));
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(1L);
//     assertThat(result.getData()).hasSize(1);
//
//     assertThat(
//             (String)
//                 result
//                     .getData()
//                     .get(0)
//                     .getInputVariables()
//                     .get(inputVariableIdToFilterOn)
//                     .getValue())
//         .startsWith("2019-01-01T01:00:00");
//   }
//
//   @Test
//   public void resultFilterByRollingDateInputVariable() {
//     // given
//     final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
//     final DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition(createDecisionDefinitionWithDate());
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(), createInputsWithDate(100.0, toDateString(now)));
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(), createInputsWithDate(100.0,
// toDateString(now.minusDays(2))));
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(), createInputsWithDate(100.0,
// toDateString(now.minusDays(3))));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(
//         Lists.newArrayList(
//             createRollingDateInputVariableFilter(INPUT_INVOICE_DATE_ID, 1L, DateUnit.DAYS)));
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result1 =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     reportData.setFilter(
//         Lists.newArrayList(
//             createRollingDateInputVariableFilter(INPUT_INVOICE_DATE_ID, 3L, DateUnit.DAYS)));
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result2 =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result1.getInstanceCount()).isEqualTo(1L);
//     assertThat(result2.getInstanceCount()).isEqualTo(3L);
//   }
//
//   @Test
//   public void resultFilterByRelativeDateInputVariable() {
//     // given
//     final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
//     final DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition(createDecisionDefinitionWithDate());
//     startDecisionInstanceWithInputVars(
//         decisionDefinitionDto.getId(), createInputsWithDate(100.0, toDateString(now)));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(
//         Lists.newArrayList(
//             createRelativeDateInputVariableFilter(INPUT_INVOICE_DATE_ID, 0L, DateUnit.DAYS)));
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result1 =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // now move the day
//     LocalDateUtil.setCurrentTime(now.plusDays(1L));
//     final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result2 =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     reportData.setFilter(
//         Lists.newArrayList(
//             createRelativeDateInputVariableFilter(INPUT_INVOICE_DATE_ID, 1L, DateUnit.DAYS)));
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result3 =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result1.getInstanceCount()).isEqualTo(1L);
//     assertThat(result2.getInstanceCount()).isEqualTo(0L);
//     assertThat(result3.getInstanceCount()).isEqualTo(1L);
//   }
//
//   private String toDateString(final OffsetDateTime now) {
//     return now.format(embeddedOptimizeExtension.getDateTimeFormatter());
//   }
//
//   private DecisionReportDataDto createReportWithAllVersion(
//       DecisionDefinitionEngineDto decisionDefinitionDto) {
//     return DecisionReportDataBuilder.create()
//         .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
//         .setDecisionDefinitionVersion(ALL_VERSIONS)
//         .setReportDataType(DecisionReportDataType.RAW_DATA)
//         .build();
//   }
//
//   private static Supplier<DateFilterDataDto<?>> createSupplier(
//       final Supplier<DateFilterDataDto<?>> supplier) {
//     return supplier;
//   }
//
//   private static DateFilterDataDto<?> createDateFilterDataDto(final DateFilterType
// dateFilterType) {
//     switch (dateFilterType) {
//       case FIXED:
//         return new FixedDateFilterDataDto();
//       case ROLLING:
//         return new RollingDateFilterDataDto();
//       case RELATIVE:
//         return new RelativeDateFilterDataDto();
//       default:
//         throw new OptimizeIntegrationTestException("Unsupported dateFilter type:" +
// dateFilterType);
//     }
//   }
// }

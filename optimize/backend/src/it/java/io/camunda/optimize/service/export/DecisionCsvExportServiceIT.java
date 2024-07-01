/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.export;
//
// import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
// import static io.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
// import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
// import io.camunda.optimize.service.util.configuration.users.AuthorizedUserType;
// import io.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
// import io.camunda.optimize.test.util.decision.DecisionReportDataType;
// import io.camunda.optimize.util.FileReaderUtil;
// import jakarta.ws.rs.core.Response;
// import java.time.OffsetDateTime;
// import java.util.Collections;
// import java.util.stream.Stream;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.Arguments;
// import org.junit.jupiter.params.provider.MethodSource;
// import org.junit.jupiter.params.provider.ValueSource;
//
// public class DecisionCsvExportServiceIT extends AbstractPlatformIT {
//
//   private static final String FAKE = "FAKE";
//
//   @ParameterizedTest
//   @ValueSource(strings = {"SUPERUSER", "NONE"})
//   public void csvExportForbiddenWhenDisabledForNonSuperuserInConfig(
//       final String authorizationType) {
//     // given
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getCsvConfiguration()
//         .setAuthorizedUserType(AuthorizedUserType.valueOf(authorizationType));
//     embeddedOptimizeExtension.reloadConfiguration();
//     final String reportId = reportClient.createEmptySingleDecisionReport();
//
//     // when
//     Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv", "Etc/GMT-1");
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @Test
//   public void csvExportForbiddenWhenDisabledForSuperuserInConfig() {
//     // given
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getAuthConfiguration()
//         .setSuperUserIds(Collections.singletonList(DEFAULT_USERNAME));
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getCsvConfiguration()
//         .setAuthorizedUserType(AuthorizedUserType.NONE);
//     embeddedOptimizeExtension.reloadConfiguration();
//     final String reportId = reportClient.createEmptySingleDecisionReport();
//
//     // when
//     Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv", "Etc/GMT-1");
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @ValueSource(strings = {"SUPERUSER", "ALL"})
//   public void csvExportWorksForSuperuserWhenAuthorizedInConfig(final String authorizationType) {
//     // given
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getAuthConfiguration()
//         .setSuperUserIds(Collections.singletonList(DEFAULT_USERNAME));
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getCsvConfiguration()
//         .setAuthorizedUserType(AuthorizedUserType.valueOf(authorizationType));
//     embeddedOptimizeExtension.reloadConfiguration();
//     DecisionDefinitionEngineDto decisionDefinitionEngineDto =
//         engineIntegrationExtension.deployAndStartDecisionDefinition();
//
//     importAllEngineEntitiesFromScratch();
//
//     final DecisionReportDataDto currentReport =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey(FAKE)
//             .setDecisionDefinitionVersion(FAKE)
//             .setReportDataType(DecisionReportDataType.RAW_DATA)
//             .build();
//     currentReport.setDecisionDefinitionKey(decisionDefinitionEngineDto.getKey());
//     currentReport.setDecisionDefinitionVersion(decisionDefinitionEngineDto.getVersionAsString());
//     String reportId = createAndStoreDefaultReportDefinition(currentReport);
//
//     // when
//     Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv", "Etc/GMT-1");
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource("getArguments")
//   public void reportCsvHasExpectedValue(DecisionReportDataDto currentReport, String expectedCSV)
//       throws Exception {
//     // given
//     OffsetDateTime lastEvaluationDateFilter =
// OffsetDateTime.parse("2019-01-29T18:20:23.277+01:00");
//     DecisionDefinitionEngineDto decisionDefinitionEngineDto =
//         engineIntegrationExtension.deployAndStartDecisionDefinition();
//
//     currentReport.setDecisionDefinitionKey(decisionDefinitionEngineDto.getKey());
//     currentReport.setDecisionDefinitionVersion(decisionDefinitionEngineDto.getVersionAsString());
//     String reportId = createAndStoreDefaultReportDefinition(currentReport);
//     engineDatabaseExtension.changeDecisionInstanceEvaluationDate(
//         lastEvaluationDateFilter, lastEvaluationDateFilter);
//     String decisionInstanceId =
//         engineDatabaseExtension
//             .getDecisionInstanceIdsWithEvaluationDateEqualTo(lastEvaluationDateFilter)
//             .get(0);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//
//     String actualContent = getResponseContentAsString(response);
//     String stringExpected =
//         getExpectedContentAsString(decisionInstanceId, decisionDefinitionEngineDto, expectedCSV);
//
//     assertThat(actualContent).isEqualTo(stringExpected);
//   }
//
//   @ParameterizedTest
//   @MethodSource("getArgumentsForCustomDelimiter")
//   public void csvExportWorksWithCustomDelimiter(
//       DecisionReportDataDto currentReport, String expectedCSV, char csvDelimiter) throws
// Exception {
//     // given
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getCsvConfiguration()
//         .setExportCsvDelimiter(csvDelimiter);
//     OffsetDateTime lastEvaluationDateFilter =
// OffsetDateTime.parse("2019-01-29T18:20:23.277+01:00");
//     DecisionDefinitionEngineDto decisionDefinitionEngineDto =
//         engineIntegrationExtension.deployAndStartDecisionDefinition();
//
//     currentReport.setDecisionDefinitionKey(decisionDefinitionEngineDto.getKey());
//     currentReport.setDecisionDefinitionVersion(decisionDefinitionEngineDto.getVersionAsString());
//     String reportId = createAndStoreDefaultReportDefinition(currentReport);
//     engineDatabaseExtension.changeDecisionInstanceEvaluationDate(
//         lastEvaluationDateFilter, lastEvaluationDateFilter);
//     String decisionInstanceId =
//         engineDatabaseExtension
//             .getDecisionInstanceIdsWithEvaluationDateEqualTo(lastEvaluationDateFilter)
//             .get(0);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//
//     String actualContent = getResponseContentAsString(response);
//     String stringExpected =
//         getExpectedContentAsString(decisionInstanceId, decisionDefinitionEngineDto, expectedCSV);
//
//     assertThat(actualContent).isEqualTo(stringExpected);
//   }
//
//   private String getExpectedContentAsString(
//       String decisionInstanceId,
//       DecisionDefinitionEngineDto decisionDefinitionEngineDto,
//       String expectedCSV) {
//     String expectedString = FileReaderUtil.readFileWithWindowsLineSeparator(expectedCSV);
//     expectedString = expectedString.replace("${DI_ID}", decisionInstanceId);
//     expectedString = expectedString.replace("${DD_ID}", decisionDefinitionEngineDto.getId());
//     return expectedString;
//   }
//
//   private String createAndStoreDefaultReportDefinition(DecisionReportDataDto reportData) {
//     SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
//         new SingleDecisionReportDefinitionRequestDto();
//     singleDecisionReportDefinitionDto.setData(reportData);
//     singleDecisionReportDefinitionDto.setId("something");
//     singleDecisionReportDefinitionDto.setLastModifier("something");
//     singleDecisionReportDefinitionDto.setName("something");
//     OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
//     singleDecisionReportDefinitionDto.setCreated(someDate);
//     singleDecisionReportDefinitionDto.setLastModified(someDate);
//     singleDecisionReportDefinitionDto.setOwner("something");
//     singleDecisionReportDefinitionDto
//         .getData()
//         .getConfiguration()
//         .getTableColumns()
//         .setIncludeNewVariables(true);
//     return reportClient.createSingleDecisionReport(singleDecisionReportDefinitionDto);
//   }
//
//   private static Stream<Arguments> getArguments() {
//     return Stream.of(
//         Arguments.of(
//             DecisionReportDataBuilder.create()
//                 .setDecisionDefinitionKey(FAKE)
//                 .setDecisionDefinitionVersion(FAKE)
//                 .setReportDataType(DecisionReportDataType.RAW_DATA)
//                 .build(),
//             "/csv/decision/raw_decision_data_grouped_by_none.csv",
//             "Raw Data Grouped By None"),
//         Arguments.of(
//             DecisionReportDataBuilder.create()
//                 .setDecisionDefinitionKey(FAKE)
//                 .setDecisionDefinitionVersion(FAKE)
//                 .setReportDataType(
//                     DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
//                 .setDateInterval(AggregateByDateUnit.DAY)
//                 .build(),
//             "/csv/decision/count_decision_frequency_group_by_evaluation_date.csv",
//             "Count Decision Frequency grouped by evaluation date"),
//         Arguments.of(
//             DecisionReportDataBuilder.create()
//                 .setDecisionDefinitionKey(FAKE)
//                 .setDecisionDefinitionVersion(FAKE)
//                 .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
//                 .build(),
//             "/csv/decision/count_decision_frequency_group_by_none.csv",
//             "Count Decision Frequency grouped by none"),
//         Arguments.of(
//             DecisionReportDataBuilder.create()
//                 .setDecisionDefinitionKey(FAKE)
//                 .setDecisionDefinitionVersion(FAKE)
//
// .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
//                 .build(),
//             "/csv/decision/count_decision_frequency_group_by_matched_rule.csv",
//             "Count Decision Frequency grouped by matched rule"));
//   }
//
//   private static Stream<Arguments> getArgumentsForCustomDelimiter() {
//     return Stream.of(
//         Arguments.of(
//             DecisionReportDataBuilder.create()
//                 .setDecisionDefinitionKey(FAKE)
//                 .setDecisionDefinitionVersion(FAKE)
//                 .setReportDataType(DecisionReportDataType.RAW_DATA)
//                 .build(),
//             "/csv/decision/raw_decision_data_grouped_by_none_semicolon_delimiter.csv",
//             ';'),
//         Arguments.of(
//             DecisionReportDataBuilder.create()
//                 .setDecisionDefinitionKey(FAKE)
//                 .setDecisionDefinitionVersion(FAKE)
//                 .setReportDataType(
//                     DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
//                 .setDateInterval(AggregateByDateUnit.DAY)
//                 .build(),
//
// "/csv/decision/count_decision_frequency_group_by_evaluation_date_semicolon_delimiter.csv",
//             ';'),
//         Arguments.of(
//             DecisionReportDataBuilder.create()
//                 .setDecisionDefinitionKey(FAKE)
//                 .setDecisionDefinitionVersion(FAKE)
//                 .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
//                 .build(),
//             "/csv/decision/count_decision_frequency_group_by_none.csv",
//             ';'),
//         Arguments.of(
//             DecisionReportDataBuilder.create()
//                 .setDecisionDefinitionKey(FAKE)
//                 .setDecisionDefinitionVersion(FAKE)
//
// .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
//                 .build(),
//
// "/csv/decision/count_decision_frequency_group_by_matched_rule_semicolon_delimiter.csv",
//             ';'),
//         Arguments.of(
//             DecisionReportDataBuilder.create()
//                 .setDecisionDefinitionKey(FAKE)
//                 .setDecisionDefinitionVersion(FAKE)
//                 .setReportDataType(DecisionReportDataType.RAW_DATA)
//                 .build(),
//             "/csv/decision/raw_decision_data_grouped_by_none_tabs_delimiter.csv",
//             '\t'));
//   }
// }

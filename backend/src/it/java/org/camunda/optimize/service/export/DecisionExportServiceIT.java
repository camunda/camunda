/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DecisionExportServiceIT {

  private static final String FAKE = "FAKE";

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();
  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtensionRule engineDatabaseExtensionRule = new EngineDatabaseExtensionRule(engineIntegrationExtensionRule.getEngineName());

  @ParameterizedTest
  @MethodSource("getArguments")
  public void reportCsvHasExpectedValue(DecisionReportDataDto currentReport, String expectedCSV) throws Exception {
    //given
    OffsetDateTime lastEvaluationDateFilter = OffsetDateTime.parse("2019-01-29T18:20:23.277+01:00");
    DecisionDefinitionEngineDto decisionDefinitionEngineDto = engineIntegrationExtensionRule.deployAndStartDecisionDefinition();

    currentReport.setDecisionDefinitionKey(decisionDefinitionEngineDto.getKey());
    currentReport.setDecisionDefinitionVersion(decisionDefinitionEngineDto.getVersionAsString());
    String reportId = createAndStoreDefaultReportDefinition(currentReport);
    engineDatabaseExtensionRule.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, lastEvaluationDateFilter);
    String decisionInstanceId =
      engineDatabaseExtensionRule.getDecisionInstanceIdsWithEvaluationDateEqualTo(
        lastEvaluationDateFilter).get(0);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getResponseContentAsString(response);
    String stringExpected = getExpectedContentAsString(decisionInstanceId, decisionDefinitionEngineDto, expectedCSV);

    assertThat(actualContent, is(stringExpected));
  }

  private String getExpectedContentAsString(String decisionInstanceId,
                                              DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                            String expectedCSV) {
    String expectedString = FileReaderUtil.readFileWithWindowsLineSeparator(expectedCSV);
    expectedString = expectedString.replace("${DI_ID}", decisionInstanceId);
    expectedString = expectedString.replace("${DD_ID}", decisionDefinitionEngineDto.getId());
    return expectedString;
  }

  private String createAndStoreDefaultReportDefinition(DecisionReportDataDto reportData) {
    SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
    singleDecisionReportDefinitionDto.setData(reportData);
    singleDecisionReportDefinitionDto.setId("something");
    singleDecisionReportDefinitionDto.setLastModifier("something");
    singleDecisionReportDefinitionDto.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleDecisionReportDefinitionDto.setCreated(someDate);
    singleDecisionReportDefinitionDto.setLastModified(someDate);
    singleDecisionReportDefinitionDto.setOwner("something");
    return createNewReport(singleDecisionReportDefinitionDto);
  }

  private String createNewReport(SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private static Stream<Arguments> getArguments() {
    return Stream.of(
      Arguments.of(DecisionReportDataBuilder
                     .create()
                     .setDecisionDefinitionKey(FAKE)
                     .setDecisionDefinitionVersion(FAKE)
                     .setReportDataType(DecisionReportDataType.RAW_DATA)
                     .build(),
                   "/csv/decision/raw_decision_data_grouped_by_none.csv",
                   "Raw Data Grouped By None"),
      Arguments.of(DecisionReportDataBuilder.create()
                     .setDecisionDefinitionKey(FAKE)
                     .setDecisionDefinitionVersion(FAKE)
                     .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
                     .setDateInterval(GroupByDateUnit.DAY)
                     .build(),
                   "/csv/decision/count_decision_frequency_group_by_evaluation_date.csv",
                   "Count Decision Frequency grouped by evaluation date"),
      Arguments.of(DecisionReportDataBuilder.create()
                     .setDecisionDefinitionKey(FAKE)
                     .setDecisionDefinitionVersion(FAKE)
                     .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
                     .build(),
                   "/csv/decision/count_decision_frequency_group_by_none.csv",
                   "Count Decision Frequency grouped by none"),
      Arguments.of(DecisionReportDataBuilder.create()
                     .setDecisionDefinitionKey(FAKE)
                     .setDecisionDefinitionVersion(FAKE)
                     .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
                     .build(),
                   "/csv/decision/count_decision_frequency_group_by_matched_rule.csv",
                   "Count Decision Frequency grouped by matched rule")
    );
  }
}
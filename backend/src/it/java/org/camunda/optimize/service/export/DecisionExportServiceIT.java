/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.apache.commons.io.IOUtils;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.DecisionReportDataType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


@RunWith(Parameterized.class)
public class DecisionExportServiceIT {

  private static final String START = "aStart";
  private static final String END = "anEnd";
  public static final String DEFAULT_DMN_DEFINITION_PATH = "dmn/invoiceBusinessDecision_withName_and_versionTag.xml";

  @Parameterized.Parameters(name = "{2}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {
        DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
          FAKE,
          FAKE
        ),
        "/csv/decision/raw_decision_data_grouped_by_none.csv",
        "Raw Data Grouped By None"
      },
      {
        DecisionReportDataBuilder.create()
        .setDecisionDefinitionKey(FAKE)
        .setDecisionDefinitionVersion(FAKE)
        .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
        .setDateInterval(GroupByDateUnit.DAY)
        .build(),
        "/csv/decision/count_decision_frequency_group_by_evaluation_date.csv",
        "Count Decision Frequency grouped by evaluation date"
      },
      {
        DecisionReportDataBuilder.create()
        .setDecisionDefinitionKey(FAKE)
        .setDecisionDefinitionVersion(FAKE)
        .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
        .build(),
        "/csv/decision/count_decision_frequency_group_by_none.csv",
        "Count Decision Frequency grouped by none"
      },
      {
        DecisionReportDataBuilder.create()
        .setDecisionDefinitionKey(FAKE)
        .setDecisionDefinitionVersion(FAKE)
        .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_MATCHED_RULE)
        .build(),
        "/csv/decision/count_decision_frequency_group_by_matched_rule.csv",
        "Count Decision Frequency grouped by matched rule"
      }
    });
  }

  private DecisionReportDataDto currentReport;
  private String expectedCSV;

  private static final String FAKE = "FAKE";
  private static final String CSV_EXPORT = "export/csv";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule)
      .around(engineDatabaseRule);

  public DecisionExportServiceIT(DecisionReportDataDto currentReport, String expectedCSV, String testName) {
    this.currentReport = currentReport;
    this.expectedCSV = expectedCSV;
  }

  @Test
  public void reportCsvHasExpectedValue() throws Exception {
    //given
    OffsetDateTime lastEvaluationDateFilter = OffsetDateTime.parse("2019-01-29T18:20:23.277+01:00");
    DecisionDefinitionEngineDto decisionDefinitionEngineDto = engineRule.deployAndStartDecisionDefinition();

    currentReport.setDecisionDefinitionKey(decisionDefinitionEngineDto.getKey());
    currentReport.setDecisionDefinitionVersion(decisionDefinitionEngineDto.getVersionAsString());
    String reportId = createAndStoreDefaultReportDefinition(currentReport);
    engineDatabaseRule.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, lastEvaluationDateFilter);
    String decisionInstanceId =
      engineDatabaseRule.getDecisionInstanceIdsWithEvaluationDateEqualTo(
      lastEvaluationDateFilter).get(0);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCsvExportRequest(reportId, "my_file.csv")
            .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getActualContentAsString(response);
    String stringExpected = getExpectedContentAsString(decisionInstanceId, decisionDefinitionEngineDto);

    assertThat(actualContent, is(stringExpected));
  }

  private String getActualContentAsString(Response response) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    return new String(result);
  }

  private String getExpectedContentAsString(String decisionInstanceId,
                                            DecisionDefinitionEngineDto decisionDefinitionEngineDto) throws IOException {
    Path path = Paths.get(this.getClass().getResource(expectedCSV).getPath());
    byte[] expectedContent = Files.readAllBytes(path);
    String stringExpected = new String(expectedContent);
    stringExpected  = stringExpected.
      replace("${DI_ID}", decisionInstanceId);
    stringExpected  = stringExpected.
      replace("${DD_ID}", decisionDefinitionEngineDto.getId());
    return stringExpected;
  }

  private String createAndStoreDefaultReportDefinition(DecisionReportDataDto reportData) {
    String id = createNewReportHelper();
    SingleDecisionReportDefinitionDto report = new SingleDecisionReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  private void updateReport(String id, SingleDecisionReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateSingleDecisionReportRequest(id, updatedReport)
            .execute();

    assertThat(response.getStatus(), is(204));
  }

  private String createNewReportHelper() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateSingleDecisionReportRequest()
            .execute(IdDto.class, 200)
            .getId();
  }
}
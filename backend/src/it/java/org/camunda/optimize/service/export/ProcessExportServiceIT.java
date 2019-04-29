/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.apache.commons.io.IOUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
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
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


@RunWith(Parameterized.class)
public class ProcessExportServiceIT {

  private static final String START = "aStart";
  private static final String END = "anEnd";

  @Parameterized.Parameters(name = "{2}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {
        ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
          FAKE,
          FAKE
        ),
        "/csv/process/single/raw_process_data_grouped_by_none.csv",
        "Raw Data Grouped By None"
      },
      {
        ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByStartDate(
          FAKE,
          FAKE,
          GroupByDateUnit.DAY
        ),
        "/csv/process/single/pi_frequency_group_by_start_date.csv",
        "Process Instance Frequency Grouped By Start Date"
      },
      {
        ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone(
          FAKE,
          FAKE
        ),
        "/csv/process/single/pi_frequency_group_by_none.csv",
        "Process Instance Frequency Grouped By None"
      },
      {
        ProcessReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNode(
          FAKE,
          FAKE
        ),
        "/csv/process/single/flownode_frequency_group_by_flownodes.csv",
        "Flow Node Frequency Grouped By Flow Node"
      },
      {
        ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByStartDateReport(
          FAKE,
          FAKE,
          GroupByDateUnit.DAY
        ),
        "/csv/process/single/pi_duration_group_by_start_date.csv",
        "Process Instance Duration Grouped By PI Start Date"
      },
      {
        ProcessReportDataBuilderHelper.createProcessInstanceDurationGroupByNone(
          FAKE,
          FAKE
        ),
        "/csv/process/single/pi_duration_group_by_none.csv",
        "Process Instance Duration Grouped By None"
      },
      {
        ProcessReportDataBuilderHelper.createFlowNodeDurationGroupByFlowNodeHeatmapReport(
          FAKE,
          FAKE
        ),
        "/csv/process/single/flownode_duration_group_by_flownodes.csv",
        "Flow Node Duration Grouped By Flow Node"
      }
    });
  }

  private ProcessReportDataDto currentReport;
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

  public ProcessExportServiceIT(ProcessReportDataDto currentReport, String expectedCSV, String testName) {
    this.currentReport = currentReport;
    this.expectedCSV = expectedCSV;
  }

  @Test
  public void reportCsvHasExpectedValue() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    currentReport.setProcessDefinitionKey(processInstance.getProcessDefinitionKey());
    currentReport.setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion());
    String reportId = createAndStoreDefaultReportDefinition(currentReport);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCsvExportRequest(reportId, "my_file.csv")
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getActualContentAsString(response);
    String stringExpected = getExpectedContentAsString(processInstance);

    assertThat(actualContent, is(stringExpected));
  }

  private String getActualContentAsString(Response response) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    return new String(result);
  }

  private String getExpectedContentAsString(ProcessInstanceEngineDto processInstance) throws IOException {
    Path path = Paths.get(this.getClass().getResource(expectedCSV).getPath());
    byte[] expectedContent = Files.readAllBytes(path);
    String stringExpected = new String(expectedContent);
    stringExpected = stringExpected.
      replace("${PI_ID}", processInstance.getId());
    stringExpected = stringExpected.
      replace("${PD_ID}", processInstance.getDefinitionId());
    return stringExpected;
  }

  private String createAndStoreDefaultReportDefinition(ProcessReportDataDto reportData) {
    String id = createNewReportHelper();
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
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

  private void updateReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();

    assertThat(response.getStatus(), is(204));
  }

  private String createNewReportHelper() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() throws Exception {
    HashMap<String, Object> variables = new HashMap<>();
    variables.put("1", "test");
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartSimpleProcessWithVariables(variables);

    OffsetDateTime shiftedStartDate = OffsetDateTime.parse("2018-02-26T14:20:00.000+01:00");
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceEngineDto.getId(), shiftedStartDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceEngineDto.getId(), shiftedStartDate);
    engineDatabaseRule.changeActivityDuration(processInstanceEngineDto.getId(), START, 0L);
    engineDatabaseRule.changeActivityDuration(processInstanceEngineDto.getId(), END, 0L);
    return processInstanceEngineDto;
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START)
      .endEvent(END)
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }
}
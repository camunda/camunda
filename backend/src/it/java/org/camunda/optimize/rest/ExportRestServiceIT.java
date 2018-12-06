package org.camunda.optimize.rest;

import org.apache.commons.io.IOUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataBuilderHelper;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;


public class ExportRestServiceIT {
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);


  @Test
  public void exportWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .withoutAuthentication()
            .buildCsvExportRequest("fake_id", "my_file.csv")
            .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(307));
    Assert.assertThat(response.getLocation().getPath(), is("/login"));
  }

  @Test
  public void exportExistingReportWithoutFilename() {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultValidReportDefinition(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion()
    );

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCsvExportRequest(reportId, "")
            .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void exportExistingReport() throws IOException {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultValidReportDefinition(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion()
    );

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCsvExportRequest(reportId, "my_file.csv")
            .execute();

    // then
    assertThat(response.getStatus(), is(200));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    assertThat(result.length, is(not(0)));
  }

  @Test
  public void exportExistingInvalidReport() throws IOException {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultInvalidReportDefinition(
            processInstance.getProcessDefinitionKey(),
            processInstance.getProcessDefinitionVersion()
    );

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCsvExportRequest(reportId, "my_file.csv")
            .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }


  @Test
  public void exportNotExistingReport() {
    // when
    Response response =
        embeddedOptimizeRule
            .getRequestExecutor()
            .buildCsvExportRequest("UFUK", "IGDE.csv")
            .execute();
    // then
    assertThat(response.getStatus(), is(404));
  }

  private String createAndStoreDefaultValidReportDefinition(String processDefinitionKey, String processDefinitionVersion) {
    ProcessReportDataDto reportData = ReportDataBuilderHelper
            .createProcessReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);

    return createAndStoreDefaultReportDefinition(reportData);
  }

  private String createAndStoreDefaultInvalidReportDefinition(String processDefinitionKey, String processDefinitionVersion) {
    ProcessReportDataDto reportData = ReportDataBuilderHelper
            .createCountFlowNodeFrequencyGroupByFlowNodeNumber(processDefinitionKey, processDefinitionVersion);

    return createAndStoreDefaultReportDefinition(reportData);
  }

  private String createAndStoreDefaultReportDefinition(ProcessReportDataDto reportData) {
    String id = createNewProcessReport();

    SingleReportDefinitionDto<ProcessReportDataDto> report = new SingleReportDefinitionDto<>();
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

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateReportRequest(id, updatedReport)
            .execute();
    assertThat(response.getStatus(), is(204));
  }


  protected String createNewProcessReport() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateSingleProcessReportRequest()
            .execute(IdDto.class, 200)
            .getId();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
        .name("aProcessName")
        .startEvent()
        .endEvent()
        .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }
}
package org.camunda.optimize.service.export;

import org.apache.commons.io.IOUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;

import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyString;


public class CombinedProcessExportServiceIT {

  private static final String START = "aStart";
  private static final String END = "anEnd";

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

  @Test
  public void combinedMapReportHasExpectedValue() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    String singleReportId1 = createNewSingleMapReport(processInstance1);
    String singleReportId2 = createNewSingleMapReport(processInstance2);
    String combinedReportId = createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCsvExportRequest(combinedReportId, "my_file.csv")
            .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getActualContentAsString(response);
    String stringExpected =
      getExpectedContentAsString("/csv/process/combined/combined_count_flow_node_frequency_group_by_flow_node.csv");

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void theOrderOfTheReportsDoesMatter() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    String singleReportId1 = createNewSingleMapReport(processInstance1);
    String singleReportId2 = createNewSingleMapReport(processInstance2);
    String combinedReportId = createNewCombinedReport(singleReportId2, singleReportId1);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCsvExportRequest(combinedReportId, "my_file.csv")
            .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getActualContentAsString(response);
    String stringExpected =
      getExpectedContentAsString("/csv/process/combined/" +
                                   "combined_count_flow_node_frequency_group_by_flow_node_different_order.csv");

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedNumberReportHasExpectedValue() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    String singleReportId1 = createNewSingleNumberReport(processInstance1);
    String singleReportId2 = createNewSingleNumberReport(processInstance2);
    String combinedReportId = createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCsvExportRequest(combinedReportId, "my_file.csv")
            .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getActualContentAsString(response);
    String stringExpected =
      getExpectedContentAsString("/csv/process/combined/combined_count_pi_frequency_group_by_none.csv");

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedReportWithUnevaluatableReportProducesEmptyResult() throws Exception {
    //given
    String singleReportId1 = createNewSingleReport();
    String combinedReportId = createNewCombinedReport(singleReportId1);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCsvExportRequest(combinedReportId, "my_file.csv")
            .execute();

    // then
    assertThat(response.getStatus(), is(200));

    String actualContent = getActualContentAsString(response);
    String stringExpected =
      getExpectedContentAsString("/csv/process/combined/combined_empty_report.csv");

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedReportWithoutReportsProducesEmptyResult() throws IOException {
    //given
    String combinedReportId = createNewCombinedReport();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCsvExportRequest(combinedReportId, "my_file.csv")
            .execute();

    // then
    assertThat(response.getStatus(), is(200));
    String actualContent = getActualContentAsString(response);
    assertThat(actualContent.trim(), isEmptyString());
  }

  private String getActualContentAsString(Response response) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    return new String(result);
  }

  private String getExpectedContentAsString(String pathToExpectedCSV) throws IOException {
    Path path = Paths.get(this.getClass().getResource(pathToExpectedCSV).getPath());
    byte[] expectedContent = Files.readAllBytes(path);
    return new String(expectedContent);
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

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateReportRequest(id, updatedReport)
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

  private String createNewCombinedReport() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateCombinedReportRequest()
            .execute(IdDto.class, 200)
            .getId();
  }


    private String createNewSingleMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createCountFlowNodeFrequencyGroupByFlowNode(
        engineDto.getProcessDefinitionKey(),
        engineDto.getProcessDefinitionVersion()
      );
    return createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
  }

  private String createNewSingleMapReport(ProcessReportDataDto data) {
    String singleReportId = createNewSingleReport();
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setName("FooName");
    definitionDto.setData(data);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewSingleNumberReport(ProcessInstanceEngineDto engineDto) {
    String singleReportId = createNewSingleReport();
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createPiFrequencyCountGroupedByNone(engineDto.getProcessDefinitionKey(), engineDto.getProcessDefinitionVersion());
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private String createNewSingleReport() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateSingleProcessReportRequest()
            .execute(IdDto.class, 200)
            .getId();
  }

  private String createNewCombinedReport(CombinedReportDefinitionDto report) {
    String reportId = createNewCombinedReport();
    updateReport(reportId, report);
    return reportId;
  }

  private String createNewCombinedReport(String... singleReportIds) {
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReport(singleReportIds));
    return createNewCombinedReport(report);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWith5FlowNodes() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
        .startEvent(START)
        .serviceTask("ServiceTask1")
          .camundaExpression("${true}")
        .serviceTask("ServiceTask2")
          .camundaExpression("${true}")
        .serviceTask("ServiceTask3")
          .camundaExpression("${true}")
        .endEvent(END)
      .done();
    return engineRule.deployAndStartProcess(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWith2FlowNodes() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
        .name("aProcessName")
        .startEvent(START)
        .endEvent(END)
        .done();
    return engineRule.deployAndStartProcess(processModel);
  }
}

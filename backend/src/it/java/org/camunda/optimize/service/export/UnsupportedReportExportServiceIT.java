package org.camunda.optimize.service.export;

import org.apache.commons.io.IOUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticsearchIntegrationRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
public class UnsupportedReportExportServiceIT {
  protected static final String CSV_EXPORT = "export/csv";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticsearchIntegrationRule elasticSearchRule = new ElasticsearchIntegrationRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);


  @Test
  public void unsupportedReportResultHasNoCSV() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    ReportDataDto currentReport = ReportDataHelper.createAvgPiDurationAsNumberGroupByNone(
        processInstance.getProcessDefinitionKey(),processInstance.getProcessDefinitionVersion());
    String reportId = createAndStoreDefaultReportDefinition(currentReport);

    // when
    Response response =
        embeddedOptimizeRule.target(CSV_EXPORT + "/" + reportId + "/my_file.csv")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then
    assertThat(response.getStatus(), is(200));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    assertThat(result.length, is(0));
  }

  private String createAndStoreDefaultReportDefinition(ReportDataDto reportData) {
    String id = createNewReportHelper();
    ReportDefinitionDto report = new ReportDefinitionDto();
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
    Response response =
        embeddedOptimizeRule.target("report/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }


  protected String createNewReportHelper() {
    Response response =
        embeddedOptimizeRule.target("report")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() throws Exception {
    HashMap<String, Object> variables = new HashMap<>();
    variables.put("1", "test");
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartSimpleProcessWithVariables(variables);
    return processInstanceEngineDto;
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
        .name("aProcessName")
        .startEvent("aStart")
        .endEvent("anEnd")
        .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }
}
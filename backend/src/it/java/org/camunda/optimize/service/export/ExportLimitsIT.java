package org.camunda.optimize.service.export;

import com.opencsv.CSVReader;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.ALL_VERSIONS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
public class ExportLimitsIT {
  protected static final String CSV_EXPORT = "export/csv";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);


  @Test
  public void exportWithOffset() throws Exception {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultReportDefinition(
        processInstance.getProcessDefinitionKey(),
        ALL_VERSIONS
    );
    deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();

    embeddedOptimizeRule.getConfigurationService().setExportCsvOffset(1);
    embeddedOptimizeRule.getConfigurationService().setExportCsvLimit(null);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Response response =
        embeddedOptimizeRule.target(CSV_EXPORT + "/" + reportId + "/my_file.csv")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();


    assertThat(response.getStatus(), is(200));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(result)));

    // then
    List<String[]> csvLines = reader.readAll();
    assertThat(csvLines.size(), is(3));
    reader.close();
  }

  @Test
  public void exportWithLimit() throws Exception {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultReportDefinition(
        processInstance.getProcessDefinitionKey(),
        ALL_VERSIONS
    );
    deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();

    embeddedOptimizeRule.getConfigurationService().setExportCsvOffset(null);
    embeddedOptimizeRule.getConfigurationService().setExportCsvLimit(1);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Response response =
        embeddedOptimizeRule.target(CSV_EXPORT + "/" + reportId + "/my_file.csv")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();


    assertThat(response.getStatus(), is(200));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(result)));

    // then
    assertThat(reader.readAll().size(), is(2));
    reader.close();
  }

  @Test
  public void exportWithOffsetAndLimit() throws Exception {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultReportDefinition(
        processInstance.getProcessDefinitionKey(),
        ALL_VERSIONS
    );
    deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();

    embeddedOptimizeRule.getConfigurationService().setExportCsvOffset(1);
    embeddedOptimizeRule.getConfigurationService().setExportCsvLimit(1);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    Response response =
        embeddedOptimizeRule.target(CSV_EXPORT + "/" + reportId + "/my_file.csv")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();


    assertThat(response.getStatus(), is(200));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(result)));

    // then
    assertThat(reader.readAll().size(), is(2));
    reader.close();
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey,
                                                       String processDefinitionVersion) {
    String id = createNewReportHelper();
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
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
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
        embeddedOptimizeRule.target("report/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }


  protected String createNewReportHelper() {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
        embeddedOptimizeRule.target("report")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
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

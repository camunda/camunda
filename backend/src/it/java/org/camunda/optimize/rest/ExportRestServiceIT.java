package org.camunda.optimize.rest;

import org.apache.commons.io.IOUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticsearchIntegrationRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.rest.util.AuthenticationUtil.OPTIMIZE_AUTHORIZATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

/**
 * @author Askar Akhmerov
 */
public class ExportRestServiceIT {
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
  public void exportWithoutAuthorization() {
    // when
    Response response =
        embeddedOptimizeRule.target(CSV_EXPORT + "/fake_id/my_file.csv")
            .request()
            .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(307));
    Assert.assertThat(response.getLocation().getPath(), is("/login"));
  }

  @Test
  public void exportExistingReportWithoutFilename() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultReportDefinition(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion()
    );

    // when
    Response response =
        embeddedOptimizeRule.target(CSV_EXPORT + "/" + reportId + "/")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void exportExistingReport() throws IOException {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultReportDefinition(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion()
    );

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
    assertThat(result.length, is(not(0)));
  }

  @Test
  public void exportExistingReportWithCookies() throws IOException {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultReportDefinition(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion()
    );

    // when
    Response response =
        embeddedOptimizeRule.target(CSV_EXPORT + "/" + reportId + "/my_file.csv")
            .request()
            .cookie(OPTIMIZE_AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then
    assertThat(response.getStatus(), is(200));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    assertThat(result.length, is(not(0)));
  }

  @Test
  public void exportNotExistingReport() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    // when
    Response response =
        embeddedOptimizeRule.target(CSV_EXPORT + "/fake_id/my_file.csv")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .post(Entity.json(new AlertCreationDto()));
    // then the status code is not authorized
    assertThat(response.getStatus(), is(500));
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
package org.camunda.optimize.service.alert;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataHelper;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
public abstract class AbstractAlertSchedulerIT {

  protected static final String BEARER = "Bearer ";
  protected static final String ALERT = "alert";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  protected AlertCreationDto setupBasicAlert() throws IOException, InterruptedException {
    String processDefinitionId = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(processDefinitionId);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String reportId = createAndStoreNumberReport(processDefinitionId);
    return createSimpleAlert(reportId);
  }

  protected AlertCreationDto createSimpleAlert(String reportId) {
    return createSimpleAlert(reportId, 1,"Seconds");
  }

  protected AlertCreationDto createSimpleAlert(String reportId, int intervalValue, String unit) {
    AlertCreationDto alertCreationDto = new AlertCreationDto();

    AlertInterval interval = new AlertInterval();
    interval.setUnit(unit);
    interval.setValue(intervalValue);
    alertCreationDto.setCheckInterval(interval);
    alertCreationDto.setThreshold(0);
    alertCreationDto.setThresholdOperator(">");
    alertCreationDto.setEmail("test@camunda.com");
    alertCreationDto.setName("test alert");
    alertCreationDto.setReportId(reportId);

    return alertCreationDto;
  }

  protected String createNewReportHelper() {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
        embeddedOptimizeRule.target("report")
            .request()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }


  protected String createAndStoreNumberReport(String processDefinitionId) {
    String id = createNewReportHelper();
    ReportDefinitionDto report = getReportDefinitionDto(processDefinitionId);
    updateReport(id, report);
    return id;
  }

  protected ReportDefinitionDto getReportDefinitionDto(String processDefinitionId) {
    ReportDataDto reportData = ReportDataHelper.createPiFrequencyCountGroupedByNoneAsNumber(processDefinitionId);
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    return report;
  }

  protected void updateReport(String id, ReportDefinitionDto updatedReport) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
        embeddedOptimizeRule.target("report/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }

  protected String deploySimpleServiceTaskProcess() throws IOException {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
        .startEvent()
          .serviceTask()
            .camundaExpression("${true}")
        .endEvent()
        .done();
    return engineRule.deployProcessAndGetId(processModel);
  }
}

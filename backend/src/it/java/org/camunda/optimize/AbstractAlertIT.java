package org.camunda.optimize;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.alert.SyncListener;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.quartz.JobKey;
import org.quartz.SchedulerException;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.test.util.ReportDataHelper.createAvgPiDurationAsNumberGroupByNone;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
public abstract class AbstractAlertIT {

  protected static final String ALERT = "alert";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  protected String createAlert(AlertCreationDto simpleAlert) {
    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .post(Entity.json(simpleAlert));
    return response.readEntity(IdDto.class).getId();
  }

  protected void triggerAndCompleteCheckJob(String id) throws SchedulerException, InterruptedException {
    this.triggerAndCompleteJob(checkJobKey(id));
  }

  protected void triggerAndCompleteJob(JobKey jobKey ) throws SchedulerException, InterruptedException {
    SyncListener jobListener = new SyncListener(1);
    embeddedOptimizeRule.getAlertService().getScheduler().getListenerManager().addJobListener(jobListener);
    //trigger job
    embeddedOptimizeRule.getAlertService().getScheduler().triggerJob(jobKey);
    //wait for job to finish
    jobListener.getDone().await();
    embeddedOptimizeRule.getAlertService().getScheduler().getListenerManager().removeJobListener(jobListener.getName());
  }

  protected void triggerAndCompleteReminderJob(String id) throws SchedulerException, InterruptedException  {
    this.triggerAndCompleteJob(reminderJobKey(id));
  }

  private JobKey reminderJobKey(String id) {
    return new JobKey(id + "-reminder-job", "statusReminder-job");
  }

  protected ProcessInstanceEngineDto deployWithTimeShift(long daysToShift, long durationInSec) throws SQLException, InterruptedException {
    OffsetDateTime startDate = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC);
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    adjustProcessInstanceDates(processInstance.getId(), startDate, daysToShift, durationInSec);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    return processInstance;
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
        .name("aProcessName")
        .startEvent()
        .endEvent()
        .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected void adjustProcessInstanceDates(String processInstanceId,
                                            OffsetDateTime startDate,
                                            long daysToShift,
                                            long durationInSec) throws SQLException {
    OffsetDateTime shiftedStartDate = startDate.plusDays(daysToShift);
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceId, shiftedStartDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceId, shiftedStartDate.plusSeconds(durationInSec));
  }

  protected JobKey checkJobKey(String id) {
    return new JobKey(id + "-check-job", "statusCheck-job");
  }

  protected AlertCreationDto createBasicAlertWithReminder() throws IOException, InterruptedException {
    AlertCreationDto simpleAlert = setupBasicAlert();
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);
    return simpleAlert;
  }

  protected AlertCreationDto setupBasicAlert() throws IOException, InterruptedException {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(processDefinition.getId());

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String reportId = createAndStoreNumberReport(processDefinition);
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
    Response response =
        embeddedOptimizeRule.target("report")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }


  protected String createAndStoreNumberReport(ProcessDefinitionEngineDto processDefinition) {
    String id = createNewReportHelper();
    ReportDefinitionDto report = getReportDefinitionDto(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
    updateReport(id, report);
    return id;
  }

  protected ReportDefinitionDto getReportDefinitionDto(ProcessDefinitionEngineDto processDefinition) {
    return getReportDefinitionDto(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }


  protected ReportDefinitionDto getReportDefinitionDto(String processDefinitionKey, String processDefinitionVersion) {
    ReportDataDto reportData =
      ReportDataHelper.createPiFrequencyCountGroupedByNoneAsNumber(processDefinitionKey, processDefinitionVersion);
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
    Response response =
        embeddedOptimizeRule.target("report/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }

  protected ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() throws IOException {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
        .startEvent()
          .serviceTask()
            .camundaExpression("${true}")
        .endEvent()
        .done();
    return engineRule.deployProcessAndGetProcessDefinition(processModel);
  }

  protected String createAndStoreDurationNumberReport(ProcessInstanceEngineDto instanceEngineDto) {
    return createAndStoreDurationNumberReport(instanceEngineDto.getProcessDefinitionKey(), instanceEngineDto.getProcessDefinitionVersion());
  }

  protected String createAndStoreDurationNumberReport(ProcessDefinitionEngineDto definition) {
    return createAndStoreDurationNumberReport(definition.getKey(), String.valueOf(definition.getVersion()));
  }

  protected String createAndStoreDurationNumberReport(String processDefinitionKey, String processDefinitionVersion) {
    String id = createNewReportHelper();
    ReportDefinitionDto report =
      getDurationReportDefinitionDto(processDefinitionKey, processDefinitionVersion);
    updateReport(id, report);
    return id;
  }

  protected ReportDefinitionDto getDurationReportDefinitionDto(String processDefinitionKey, String processDefinitionVersion) {
    ReportDataDto reportData =
      createAvgPiDurationAsNumberGroupByNone(processDefinitionKey, processDefinitionVersion);
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

  protected void setEmailConfiguration() {
    embeddedOptimizeRule.getConfigurationService().setEmailsEnabled(true);
    embeddedOptimizeRule.getConfigurationService().setAlertEmailUsername("demo");
    embeddedOptimizeRule.getConfigurationService().setAlertEmailPassword("demo");
    embeddedOptimizeRule.getConfigurationService().setAlertEmailAddress("from@localhost.com");
    embeddedOptimizeRule.getConfigurationService().setAlertEmailHostname("127.0.0.1");
    embeddedOptimizeRule.getConfigurationService().setAlertEmailPort(6666);
    embeddedOptimizeRule.getConfigurationService().setAlertEmailProtocol("NONE");
  }

  protected GreenMail initGreenMail() {
    GreenMail greenMail = new GreenMail(new ServerSetup(6666, null, ServerSetup.PROTOCOL_SMTP));
    greenMail.setUser("from@localhost.com", "demo", "demo");
    greenMail.setUser("test@camunda.com", "test@camunda.com", "test@camunda.com");
    greenMail.start();
    return greenMail;
  }
}

package org.camunda.optimize.service.alert;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.TriggerKey;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.HEAT_VISUALIZATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class AlertReminderSchedulerIT extends AbstractAlertSchedulerIT {

  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule)
      .around(engineDatabaseRule);

  @Before
  public void cleanUp() throws Exception {
    embeddedOptimizeRule.getAlertService().getScheduler().clear();
  }

  @Test
  public void reminderJobsAreRemovedOnAlertDeletion() throws Exception {
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = setupBasicAlert();
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);

    SyncListener jobListener = new SyncListener(1);
    embeddedOptimizeRule.getAlertService().getScheduler().getListenerManager().addJobListener(jobListener);

    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(simpleAlert));
    String id = response.readEntity(String.class);

    //trigger job
    embeddedOptimizeRule.getAlertService().getScheduler().triggerJob(checkJobKey(id));
    //wait for job to finish
    jobListener.getDone().await();

    //when
    response =
      embeddedOptimizeRule.target("alert/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();
    //then
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );
  }

  @Test
  public void reminderJobsAreRemovedOnReportDeletion() throws Exception {
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = setupBasicAlert();
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);

    SyncListener jobListener = new SyncListener(1);
    embeddedOptimizeRule.getAlertService().getScheduler().getListenerManager().addJobListener(jobListener);

    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(simpleAlert));
    String id = response.readEntity(String.class);

    //trigger job
    embeddedOptimizeRule.getAlertService().getScheduler().triggerJob(checkJobKey(id));
    //wait for job to finish
    jobListener.getDone().await();

    //when
    response =
        embeddedOptimizeRule.target("report/" + simpleAlert.getReportId())
          .request()
          .header(HttpHeaders.AUTHORIZATION, BEARER + token)
          .delete();
    //then
    assertThat(
        embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
        is(0)
    );
  }

  @Test
  public void reminderJobsAreRemovedOnReportUpdate() throws Exception {
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String processDefinitionId = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(processDefinitionId);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String reportId = createAndStoreNumberReport(processDefinitionId);
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);

    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);

    SyncListener jobListener = new SyncListener(1);
    embeddedOptimizeRule.getAlertService().getScheduler().getListenerManager().addJobListener(jobListener);

    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(simpleAlert));
    String id = response.readEntity(String.class);

    //trigger job
    embeddedOptimizeRule.getAlertService().getScheduler().triggerJob(checkJobKey(id));
    //wait for job to finish
    jobListener.getDone().await();
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(2)
    );

    //when
    ReportDefinitionDto report = getReportDefinitionDto(processDefinitionId);
    report.getData().getGroupBy().setType(GROUP_BY_FLOW_NODE_TYPE);
    report.getData().setVisualization(HEAT_VISUALIZATION);
    updateReport(simpleAlert.getReportId(), report);

    //then
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );
  }

  @Test
  public void reminderJobsAreRemovedOnEvaluationResultChange() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    String processDefinitionId = processInstance.getDefinitionId();
    // when
    String reportId = createAndStoreDurationNumberReport(processDefinitionId);
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);

    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);

    simpleAlert.setThreshold(1000);

    SyncListener jobListener = new SyncListener(1);
    embeddedOptimizeRule.getAlertService().getScheduler().getListenerManager().addJobListener(jobListener);

    Response response =
        embeddedOptimizeRule.target(ALERT)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(simpleAlert));
    String id = response.readEntity(String.class);

    //trigger job
    embeddedOptimizeRule.getAlertService().getScheduler().triggerJob(checkJobKey(id));
    //wait for job to finish
    jobListener.getDone().await();

    //when
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    jobListener = new SyncListener(1);
    embeddedOptimizeRule.getAlertService().getScheduler().getListenerManager().addJobListener(jobListener);
    embeddedOptimizeRule.getAlertService().getScheduler().triggerJob(checkJobKey(id));
    //wait for job to finish
    jobListener.getDone().await();

    //then
    assertThat(
        embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
        is(1)
    );
  }

  @Test
  public void reminderJobsAreRemovedOnAlertChange() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = setupBasicAlert();
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);

    SyncListener jobListener = new SyncListener(1);
    embeddedOptimizeRule.getAlertService().getScheduler().getListenerManager().addJobListener(jobListener);

    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(simpleAlert));
    String id = response.readEntity(String.class);

    //trigger job
    embeddedOptimizeRule.getAlertService().getScheduler().triggerJob(checkJobKey(id));
    //wait for job to finish
    jobListener.getDone().await();

    //when
    simpleAlert.getCheckInterval().setValue(30);
    jobListener = new SyncListener(1);
    embeddedOptimizeRule.getAlertService().getScheduler().getListenerManager().addJobListener(jobListener);

    embeddedOptimizeRule.target("alert/" + id)
      .request()
      .header(HttpHeaders.AUTHORIZATION, BEARER + token)
      .put(Entity.json(simpleAlert));
    //then
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(1)
    );

    //when
    //trigger job
    embeddedOptimizeRule.getAlertService().getScheduler().triggerJob(checkJobKey(id));
    //wait for job to finish
    jobListener.getDone().await();
    //then
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(2)
    );
  }

  @Test
  public void reminderJobsAreScheduledOnAlertCreation() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = setupBasicAlert();
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);

    SyncListener jobListener = new SyncListener(1);
    embeddedOptimizeRule.getAlertService().getScheduler().getListenerManager().addJobListener(jobListener);

    // when
    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(simpleAlert));
    String id = response.readEntity(String.class);

    //trigger job
    embeddedOptimizeRule.getAlertService().getScheduler().triggerJob(checkJobKey(id));
    //wait for job to finish
    jobListener.getDone().await();

    // then
    assertThat(response.getStatus(), is(200));
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(2)
    );
  }

  @Test
  public void reminderJobsAreScheduledAfterRestart() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = setupBasicAlert();
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);

    SyncListener jobListener = new SyncListener(1);
    embeddedOptimizeRule.getAlertService().getScheduler().getListenerManager().addJobListener(jobListener);

    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(simpleAlert));
    String id = response.readEntity(String.class);

    //trigger job
    embeddedOptimizeRule.getAlertService().getScheduler().triggerJob(checkJobKey(id));
    //wait for job to finish
    jobListener.getDone().await();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();

    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(2)
    );
  }

  private JobKey checkJobKey(String id) {
    return new JobKey(id + "-check-job", "statusCheck-job");
  }

  private class SyncListener implements JobListener {
    private CountDownLatch done;

    public SyncListener(int numberOfExecutions) {
      done = new CountDownLatch(numberOfExecutions);
    }

    @Override
    public String getName() {
      return "test-synchronization-listener";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {

    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {

    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
      done.countDown();
    }

    public CountDownLatch getDone() {
      return done;
    }
  }

  protected String createAndStoreDurationNumberReport(String processDefinitionId) {
    String id = createNewReportHelper();
    ReportDefinitionDto report = getDurationReportDefinitionDto(processDefinitionId);
    updateReport(id, report);
    return id;
  }

  protected ReportDefinitionDto getDurationReportDefinitionDto(String processDefinitionId) {
    ReportDataDto reportData = ReportDataHelper.createAvgPiDurationAsNumberGroupByNone(processDefinitionId);
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
}

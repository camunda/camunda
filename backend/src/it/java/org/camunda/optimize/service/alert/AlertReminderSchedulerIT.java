package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.HEAT_VISUALIZATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class AlertReminderSchedulerIT extends AbstractAlertSchedulerIT {

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

    AlertCreationDto simpleAlert = createBasicAlertWithReminder();

    String id = createAlert(token, simpleAlert);

    triggerAndCompleteCheckJob(id);

    //when
    Response response =
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

    AlertCreationDto simpleAlert = createBasicAlertWithReminder();

    String id = createAlert(token, simpleAlert);

    triggerAndCompleteCheckJob(id);

    //when
    Response response =
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

    String id = createAlert(token, simpleAlert);

    triggerAndCompleteCheckJob(id);

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

    String id = createAlert(token, simpleAlert);

    triggerAndCompleteCheckJob(id);

    assertThat(
        embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
        is(greaterThanOrEqualTo(2))
    );
    //when
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    triggerAndCompleteReminderJob(id);

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

    AlertCreationDto simpleAlert = createBasicAlertWithReminder();

    String id = createAlert(token, simpleAlert);

    triggerAndCompleteCheckJob(id);

    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(greaterThanOrEqualTo(2))
    );
    //when
    simpleAlert.getCheckInterval().setValue(30);

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
    triggerAndCompleteCheckJob(id);

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

    AlertCreationDto simpleAlert = createBasicAlertWithReminder();

    // when
    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(simpleAlert));
    String id = response.readEntity(String.class);

    triggerAndCompleteCheckJob(id);

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

    AlertCreationDto simpleAlert = createBasicAlertWithReminder();

    String id = createAlert(token, simpleAlert);

    triggerAndCompleteCheckJob(id);

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();

    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(2)
    );
  }


}

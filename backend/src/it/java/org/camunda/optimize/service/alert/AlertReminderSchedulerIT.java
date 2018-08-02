package org.camunda.optimize.service.alert;

import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.group.FlowNodesGroupByDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.HEAT_VISUALIZATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;


public class AlertReminderSchedulerIT extends AbstractAlertIT {

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

    // given
    AlertCreationDto simpleAlert = createBasicAlertWithReminder();
    String id = createAlert(simpleAlert);
    triggerAndCompleteCheckJob(id);

    //when
    embeddedOptimizeRule.target("alert/" + id)
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .delete();

    //then
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );
  }

  @Test
  public void reminderJobsAreRemovedOnReportDeletion() throws Exception {
    // given
    AlertCreationDto simpleAlert = createBasicAlertWithReminder();
    String id = createAlert(simpleAlert);
    triggerAndCompleteCheckJob(id);

    //when
    embeddedOptimizeRule.target("report/" + simpleAlert.getReportId())
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .delete();

    //then
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );
  }

  @Test
  public void reminderIsNotCreatedOnStartupIfNotDefinedInAlert() throws Exception {
    //given
    AlertCreationDto alert = setupBasicAlert();
    createAlert(alert);

    //when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();


    //then
    assertThat(getAllAlerts().get(0).getReminder(), is(nullValue()));
  }


  private List<AlertDefinitionDto> getAllAlerts() {
    Response response =
            embeddedOptimizeRule.target(ALERT)
                    .request()
                    .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
                    .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<AlertDefinitionDto>>() {
    });
  }

  @Test
  public void reminderJobsAreRemovedOnReportUpdate() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(processDefinition.getId());

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String reportId = createAndStoreNumberReport(processDefinition);
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);

    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);

    String id = createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(2)
    );

    //when
    ReportDefinitionDto report =
      getReportDefinitionDto(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
    report.getData().setGroupBy(new FlowNodesGroupByDto());
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
    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    // when
    String reportId =
      createAndStoreDurationNumberReport(processInstance);
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);

    simpleAlert.setThreshold(1500);

    String id = createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    assertThat(
        embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
        is(greaterThanOrEqualTo(2))
    );
    //when
    engineRule.startProcessInstance(processInstance.getDefinitionId());
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
  public void reminderJobsAreRemovedOnAlertDefinitionChange() throws Exception {
    //given
    AlertCreationDto simpleAlert = createBasicAlertWithReminder();

    String id = createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(greaterThanOrEqualTo(2))
    );
    //when
    simpleAlert.getCheckInterval().setValue(30);

    embeddedOptimizeRule.target("alert/" + id)
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
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
    AlertCreationDto simpleAlert = createBasicAlertWithReminder();

    // when
    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(simpleAlert));
    String id = response.readEntity(IdDto.class).getId();

    triggerAndCompleteCheckJob(id);

    // then
    assertThat(response.getStatus(), is(200));
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(2)
    );
  }

  @Test
  public void reminderJobsAreScheduledCorrectly() throws Exception {
    //given
    AlertCreationDto simpleAlert = createBasicAlertWithReminder();
    AlertInterval checkInterval = new AlertInterval();
    checkInterval.setUnit("Minutes");
    checkInterval.setValue(10);
    simpleAlert.setCheckInterval(checkInterval);

    // when
    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(simpleAlert));
    assertThat(response.getStatus(), is(200));
    String id = response.readEntity(IdDto.class).getId();
    triggerAndCompleteCheckJob(id);

    // then
    OffsetDateTime nextTimeReminderIsExecuted = getNextReminderExecutionTime(id);
    OffsetDateTime upperBoundary = OffsetDateTime.now().plusSeconds(2L); // 1 second is too unstable
    assertTrue(nextTimeReminderIsExecuted.isBefore(upperBoundary));
  }

  @Test
  public void reminderJobsAreScheduledAfterRestart() throws Exception {
    //given
    AlertCreationDto simpleAlert = createBasicAlertWithReminder();

    String id = createAlert(simpleAlert);

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

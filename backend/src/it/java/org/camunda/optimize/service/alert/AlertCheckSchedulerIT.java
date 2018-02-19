package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.GreenMail;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.listeners.TriggerListenerSupport;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.mail.internet.MimeMessage;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_FLOW_NODE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.HEAT_VISUALIZATION;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class AlertCheckSchedulerIT extends AbstractAlertSchedulerIT {

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);

  @Before
  public void cleanUp() throws Exception {
    embeddedOptimizeRule.getAlertService().getScheduler().clear();
  }

  @Test
  public void reportUpdateToNotNumberRemovesAlert() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String processDefinitionId = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(processDefinitionId);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String reportId = createAndStoreNumberReport(processDefinitionId);
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);

    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(simpleAlert));

    assertThat(response.getStatus(), is(200));

    // when
    ReportDefinitionDto report = getReportDefinitionDto(processDefinitionId);
    report.getData().getGroupBy().setType(GROUP_BY_FLOW_NODE_TYPE);
    report.getData().setVisualization(HEAT_VISUALIZATION);
    updateReport(simpleAlert.getReportId(), report);

    // then
    // scheduler does not contain any triggers
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );

    //alert is deleted from ES
    response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .get();

    assertThat(response.getStatus(), is(200));
    List<AlertDefinitionDto> alertDefinitionDtos = response.readEntity(
      new GenericType<List<AlertDefinitionDto>>() {}
    );
    assertThat(alertDefinitionDtos.size(), is(0));
  }

  @Test
  public void reportDeletionRemovesAlert() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = setupBasicAlert();

    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(simpleAlert));

    assertThat(response.getStatus(), is(200));

    // when
    response =
      embeddedOptimizeRule.target("report/" + simpleAlert.getReportId())
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();

    // then
    assertThat(response.getStatus(), is(204));
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );

    response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .get();

    assertThat(response.getStatus(), is(200));
    List<AlertDefinitionDto> alertDefinitionDtos = response.readEntity(
      new GenericType<List<AlertDefinitionDto>>() {}
    );
    assertThat(alertDefinitionDtos.size(), is(0));
  }

  @Test
  public void createNewAlertPropagatedToScheduler() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = setupBasicAlert();

    // when
    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(simpleAlert));

    // then
    assertThat(response.getStatus(), is(200));
    String id = response.readEntity(String.class);
    assertThat(id, is(notNullValue()));
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(1)
    );
  }

  @Test
  public void deletedAlertsAreRemovedFromScheduler() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = setupBasicAlert();

    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(simpleAlert));

    String alertId = response.readEntity(IdDto.class).getId();

    // when
    response =
      embeddedOptimizeRule.target("alert/" + alertId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();

    // then
    assertThat(response.getStatus(), is(204));
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );
  }

  @Test
  public void updatedAlertIsRescheduled() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    AlertCreationDto simpleAlert = setupBasicAlert();

    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(simpleAlert));

    String alertId = response.readEntity(IdDto.class).getId();
    Trigger trigger = embeddedOptimizeRule.getAlertService().getScheduler().getTrigger(getTriggerKey(alertId));
    assertThat(
      getNextFireTime(trigger).truncatedTo(ChronoUnit.SECONDS),
      is(
        OffsetDateTime.now().plus(1,ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS)
      )
    );

    // when
    simpleAlert.getCheckInterval().setValue(30);

    response =
      embeddedOptimizeRule.target("alert/" + alertId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .put(Entity.json(simpleAlert));

    // then
    assertThat(response.getStatus(), is(204));

    List<AlertDefinitionDto> allAlerts = getAllAlerts(token);
    assertThat(allAlerts.get(0).isTriggered(), is(false));

    trigger = embeddedOptimizeRule.getAlertService().getScheduler().getTrigger(getTriggerKey(alertId));
    assertThat(
      getNextFireTime(trigger).truncatedTo(ChronoUnit.SECONDS),
      is(OffsetDateTime.now().plus(30,ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS))
    );
  }

  private TriggerKey getTriggerKey(String alertId) {
    return new TriggerKey(alertId + "-check-trigger", "statusCheck-trigger");
  }

  @Test
  public void testScheduleTriggers() throws Exception {

    //given
    GreenMail greenMail = initGreenMail();
    try {
      String reportId = startProcessAndCreateReport();
      setEmailConfiguration();

      // when
      String token = embeddedOptimizeRule.getAuthenticationToken();
      AlertCreationDto simpleAlert = createSimpleAlert(reportId);
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(simpleAlert));
      assertThat(greenMail.waitForIncomingEmail(3000, 1), is(true));

      //then
      MimeMessage[] emails = greenMail.getReceivedMessages();
      assertThat(emails.length, is(1));
      assertThat(emails[0].getSubject(), is("[Camunda-Optimize] - Report status"));
      assertThat(emails[0].getContent().toString(), containsString(simpleAlert.getName()));
    } finally {
      greenMail.stop();
    }
  }

  private String startProcessAndCreateReport() throws IOException, InterruptedException {
    String processDefinitionId = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(processDefinitionId);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    return createAndStoreNumberReport(processDefinitionId);
  }


  @Test
  public void testCronMinutesInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeRule.getAlertService();
    int intervalValue = 11;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue,"Minutes");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    OffsetDateTime now = OffsetDateTime.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    OffsetDateTime nextFireTime = getNextFireTime(trigger).truncatedTo(ChronoUnit.MINUTES);

    assertThat(nextFireTime, is(now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(intervalValue).truncatedTo(ChronoUnit.MINUTES)));
  }

  @Test
  public void testCronHoursInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeRule.getAlertService();
    int intervalValue = 11;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue,"Hours");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    OffsetDateTime now = OffsetDateTime.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    OffsetDateTime nextFireTime = getNextFireTime(trigger);

    OffsetDateTime targetTime = now.plusHours(intervalValue).truncatedTo(ChronoUnit.HOURS);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.HOURS), is(targetTime));
  }

  @Test
  public void testCronDaysInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeRule.getAlertService();
    int intervalValue = 5;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue,"Days");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    OffsetDateTime now = OffsetDateTime.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    OffsetDateTime nextFireTime = getNextFireTime(trigger);

    OffsetDateTime targetTime = now.plusDays(intervalValue).truncatedTo(ChronoUnit.DAYS);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.DAYS), is(targetTime));
  }


  @Test
  public void testCronWeeksInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeRule.getAlertService();
    int intervalValue = 5;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue,"Weeks");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    OffsetDateTime nextFireTime = getNextFireTime(trigger);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.MINUTES), is(OffsetDateTime.now().plus(intervalValue*7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES)));
  }


  private OffsetDateTime getNextFireTime(Trigger cronTrigger) {
    return OffsetDateTime.ofInstant(cronTrigger.getNextFireTime().toInstant(), TimeZone.getDefault().toZoneId());
  }

  private AlertDefinitionDto getAlertDefinitionDto(int intervalValue, String intervalUnit) {
    AlertCreationDto simpleAlert = createSimpleAlert("fakeReport", intervalValue, intervalUnit);

    AlertDefinitionDto alert = createFakeReport(simpleAlert);
    alert.setId(UUID.randomUUID().toString());
    return alert;
  }

  private AlertDefinitionDto createFakeReport(AlertCreationDto fakeReportAlert) {
    AlertDefinitionDto result = new AlertDefinitionDto();

    AlertUtil.mapBasicFields(fakeReportAlert, result);
    return result;
  }

  class TestListener extends TriggerListenerSupport {
    private int fireCounter = 0;

    @Override
    public String getName() {
      return "test-listener";
    }

    @Override
    public void triggerFired(Trigger trigger, JobExecutionContext context) {
      super.triggerFired(trigger, context);
      this.fireCounter = this.fireCounter + 1;
    }
  }

  private List<AlertDefinitionDto> getAllAlerts(String token) {
    Response response =
      embeddedOptimizeRule.target(ALERT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .get();

    assertThat(response.getStatus(), is(200));
    return response.readEntity(new GenericType<List<AlertDefinitionDto>>() {
    });
  }
}

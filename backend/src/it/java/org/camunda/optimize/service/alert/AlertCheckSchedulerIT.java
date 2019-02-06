package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.GreenMail;
import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.listeners.TriggerListenerSupport;

import javax.mail.internet.MimeMessage;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class AlertCheckSchedulerIT extends AbstractAlertIT {

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
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(processDefinition.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    String reportId = createAndStoreNumberReport(processDefinition);
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);

    Response response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateAlertRequest(simpleAlert)
            .execute();

    assertThat(response.getStatus(), is(200));

    // when
    SingleProcessReportDefinitionDto report = getReportDefinitionDto(processDefinition);
    report.getData().setGroupBy(new FlowNodesGroupByDto());
    report.getData().setVisualization(ProcessVisualization.HEAT);
    updateReport(simpleAlert.getReportId(), report);

    // then
    // scheduler does not contain any triggers
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );

    //alert is deleted from ES
    List<AlertDefinitionDto> alertDefinitionDtos =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetAllAlertsRequest()
            .executeAndReturnList(AlertDefinitionDto.class, 200);

    assertThat(alertDefinitionDtos.size(), is(0));
  }

  @Test
  public void reportDeletionRemovesAlert() throws Exception {
    //given
    AlertCreationDto simpleAlert = setupBasicAlert();

    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateAlertRequest(simpleAlert)
            .execute();

    assertThat(response.getStatus(), is(200));

    // when
    response =
      embeddedOptimizeRule
            .getRequestExecutor()
        .buildDeleteReportRequest(simpleAlert.getReportId(), true)
            .execute();

    // then
    assertThat(response.getStatus(), is(204));
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );

    response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetAllAlertsRequest()
            .execute();

    assertThat(response.getStatus(), is(200));
    List<AlertDefinitionDto> alertDefinitionDtos = response.readEntity(
      new GenericType<List<AlertDefinitionDto>>() {}
    );
    assertThat(alertDefinitionDtos.size(), is(0));
  }

  @Test
  public void createNewAlertPropagatedToScheduler() throws Exception {
    //given
    AlertCreationDto simpleAlert = setupBasicAlert();

    // when
    String id =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateAlertRequest(simpleAlert)
            .execute(String.class, 200);

    // then
    assertThat(id, is(notNullValue()));
    assertThat(
      embeddedOptimizeRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(1)
    );
  }

  @Test
  public void deletedAlertsAreRemovedFromScheduler() throws Exception {
    //given
    AlertCreationDto simpleAlert = setupBasicAlert();

    String alertId = embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateAlertRequest(simpleAlert)
            .execute(IdDto.class, 200)
            .getId();

    // when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildDeleteAlertRequest(alertId)
            .execute();

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
    AlertCreationDto simpleAlert = setupBasicAlert();

    String alertId =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateAlertRequest(simpleAlert)
            .execute(IdDto.class, 200)
            .getId();

    Trigger trigger = embeddedOptimizeRule.getAlertService().getScheduler().getTrigger(getTriggerKey(alertId));
    assertThat(
      getNextFireTime(trigger).truncatedTo(ChronoUnit.SECONDS),
      is(
        OffsetDateTime.now().plus(1,ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS)
      )
    );

    // when
    simpleAlert.getCheckInterval().setValue(30);

    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateAlertRequest(alertId, simpleAlert)
            .execute();

    // then
    assertThat(response.getStatus(), is(204));

    List<AlertDefinitionDto> allAlerts = getAllAlerts();
    assertThat(allAlerts.get(0).isTriggered(), is(false));

    trigger = embeddedOptimizeRule.getAlertService().getScheduler().getTrigger(getTriggerKey(alertId));
    int secondsUntilItShouldFireNext = 30;
    assertThatTriggerIsInRange(trigger, secondsUntilItShouldFireNext);
  }

  private void assertThatTriggerIsInRange(Trigger trigger, int secondsUntilItShouldFireNext) {
    // we cannot check for exact time since
    // time is running while we check for the supposed next trigger time
    // and then the check might be by one second off. Thus we check if the
    // the next trigger is within +/- 1 second bound.
    OffsetDateTime nextTimeToFire = getNextFireTime(trigger);
    OffsetDateTime lowerBound = OffsetDateTime.now()
      .plus(secondsUntilItShouldFireNext - 1, ChronoUnit.SECONDS)
      .truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime upperBound = OffsetDateTime.now()
      .plus(secondsUntilItShouldFireNext + 1, ChronoUnit.SECONDS)
      .truncatedTo(ChronoUnit.SECONDS);
    assertTrue(lowerBound.isBefore(nextTimeToFire));
    assertTrue(upperBound.isAfter(nextTimeToFire));
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
      AlertCreationDto simpleAlert = createSimpleAlert(reportId);
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateAlertRequest(simpleAlert)
            .execute();
      assertThat(greenMail.waitForIncomingEmail(3000, 1), is(true));

      //then
      MimeMessage[] emails = greenMail.getReceivedMessages();
      assertThat(emails.length, is(1));
      assertThat(emails[0].getSubject(), is("[Camunda-Optimize] - Report status"));
      String content = emails[0].getContent().toString();
      assertThat(content, containsString(simpleAlert.getName()));
      assertThat(content, containsString("http://localhost:8090/#/report/" + reportId));
    } finally {
      greenMail.stop();
    }
  }

  private String startProcessAndCreateReport() throws IOException {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    engineRule.startProcessInstance(processDefinition.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    return createAndStoreNumberReport(processDefinition);
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

    OffsetDateTime targetTime = now.truncatedTo(ChronoUnit.DAYS).plusDays(intervalValue);

    assertThat(nextFireTime.withOffsetSameInstant(OffsetDateTime.now().getOffset()).truncatedTo(ChronoUnit.DAYS), is(targetTime));
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

    assertThat(
        nextFireTime.truncatedTo(ChronoUnit.SECONDS),
        is(OffsetDateTime.now().plus(intervalValue*7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))
    );
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

  private List<AlertDefinitionDto> getAllAlerts() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildGetAllAlertsRequest()
            .executeAndReturnList(AlertDefinitionDto.class, 200);
  }
}

package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.GreenMail;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.alert.EmailAlertEnabledDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.mail.internet.MimeMessage;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
public class AlertStateChangeIT extends AbstractAlertSchedulerIT {

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule)
      .around(engineDatabaseRule);

  private GreenMail greenMail;

  @Before
  public void cleanUp() throws Exception {
    embeddedOptimizeRule.getAlertService().getScheduler().clear();
    greenMail = initGreenMail();
  }

  @After
  public void tearDown() {
    greenMail.stop();
  }

  @Test
  public void reminderJobsSendEmailEveryTime() throws Exception {
    //given
    setEmailConfiguration();
    String token = embeddedOptimizeRule.getAuthenticationToken();
    long daysToShift = 0L;
    long durationInSec = 2L;
    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
    String processDefinitionId = processInstance.getDefinitionId();
    String reportId = createAndStoreDurationNumberReport(processDefinitionId);
    AlertCreationDto simpleAlert = createAlertWithReminder(reportId);
    String id = createAlert(token, simpleAlert);

    triggerAndCompleteCheckJob(id);

    //when
    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteReminderJob(id);

    //reminder received once
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));

    //when
    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteReminderJob(id);

    //then
    //reminder received twice
    emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));

    //when
    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteCheckJob(id);

    //then
    //reminder is not received
    emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(0));
  }

  @Test
  public void changeNotificationIsNotSentByDefault() throws Exception {
    //given
    setEmailConfiguration();
    String token = embeddedOptimizeRule.getAuthenticationToken();
    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    String processDefinitionId = processInstance.getDefinitionId();
    // when
    String reportId = createAndStoreDurationNumberReport(processDefinitionId);
    AlertCreationDto simpleAlert = createAlertWithReminder(reportId);

    String id = createAlert(token, simpleAlert);

    triggerAndCompleteCheckJob(id);

    //when
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    greenMail.purgeEmailFromAllMailboxes();

    //then
    triggerAndCompleteReminderJob(id);

    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(0));
  }

  @Test
  public void changeNotificationIsSent() throws Exception {
    //given
    setEmailConfiguration();

    String token = embeddedOptimizeRule.getAuthenticationToken();
    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    String processDefinitionId = processInstance.getDefinitionId();
    // when
    String reportId = createAndStoreDurationNumberReport(processDefinitionId);
    AlertCreationDto simpleAlert = createAlertWithReminder(reportId);
    simpleAlert.setFixNotification(true);

    String id = createAlert(token, simpleAlert);

    triggerAndCompleteCheckJob(id);

    //when
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteReminderJob(id);
    //then

    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    assertThat(emails[0].getSubject(), is("[Camunda-Optimize] - Report status"));
    assertThat(emails[0].getContent().toString(), containsString(simpleAlert.getName()));
    assertThat(emails[0].getContent().toString(), containsString("is not exceeded anymore."));
  }

  @Test
  public void emailNotificationIsEnabled() {
    //given
    embeddedOptimizeRule.getConfigurationService().setEmailsEnabled(true);

    // when
    Response response =
        embeddedOptimizeRule.target("alert/email/isEnabled")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then the status code is authorized
    assertThat(response.getStatus(), is(200));
    EmailAlertEnabledDto emailEnabled = response.readEntity(EmailAlertEnabledDto.class);
    assertThat(emailEnabled.isEnabled(), is(true));
  }

  @Test
  public void emailNotificationIsDisabled() {
    //given
    embeddedOptimizeRule.getConfigurationService().setEmailsEnabled(false);

    // when
    Response response =
        embeddedOptimizeRule.target("alert/email/isEnabled")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .get();

    // then the status code is authorized
    assertThat(response.getStatus(), is(200));
    EmailAlertEnabledDto emailEnabled = response.readEntity(EmailAlertEnabledDto.class);
    assertThat(emailEnabled.isEnabled(), is(false));
  }

  private AlertCreationDto createAlertWithReminder(String reportId) {
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(3);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);
    simpleAlert.setThreshold(1500);
    simpleAlert.getCheckInterval().setValue(5);
    return simpleAlert;
  }

}

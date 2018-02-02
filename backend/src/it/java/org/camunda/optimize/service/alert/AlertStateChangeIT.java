package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.GreenMail;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
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
    assertThat(greenMail.waitForIncomingEmail(3000, 1), is(false));
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
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);
    simpleAlert.setFixNotification(true);
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
    assertThat(greenMail.waitForIncomingEmail(3000, 1), is(true));
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(2));
    assertThat(emails[1].getSubject(), is("[Camunda-Optimize] - Report status"));
    assertThat(emails[1].getContent().toString(), containsString("is not exceeded anymore."));
  }

}

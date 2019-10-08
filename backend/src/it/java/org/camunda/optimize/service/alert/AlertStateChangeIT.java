/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.GreenMail;
import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.mail.internet.MimeMessage;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class AlertStateChangeIT extends AbstractAlertIT {

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
    long daysToShift = 0L;
    long durationInSec = 2L;
    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
    String reportId = createAndStoreDurationNumberReport(processInstance);
    AlertCreationDto simpleAlert = createAlertWithReminder(reportId);
    String id = createAlert(simpleAlert);

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
    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    // when
    String reportId = createAndStoreDurationNumberReport(processInstance);
    AlertCreationDto simpleAlert = createAlertWithReminder(reportId);

    String id = createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    //when
    engineRule.startProcessInstance(processInstance.getDefinitionId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
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

    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    // when
    String reportId = createAndStoreDurationNumberReport(processInstance);
    AlertCreationDto simpleAlert = createAlertWithReminder(reportId);
    simpleAlert.setFixNotification(true);

    String id = createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    //when
    engineRule.startProcessInstance(processInstance.getDefinitionId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteReminderJob(id);
    //then

    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    assertThat(emails[0].getSubject(), is("[Camunda-Optimize] - Report status"));
    String content = emails[0].getContent().toString();
    assertThat(content, containsString(simpleAlert.getName()));
    assertThat(content, containsString("is not exceeded anymore."));
    assertThat(
      content,
      containsString(String.format("http://localhost:%d/#/report/" + reportId, getOptimizeHttpPort()))
    );
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

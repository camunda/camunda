/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.alert;

import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.slf4j.Logger;

public class ReminderHandlingListener implements JobListener {

  private static final String LISTENER_NAME = "alert-reminder-handler";
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ReminderHandlingListener.class);

  private final AlertReminderJobFactory alertReminderJobFactory;

  public ReminderHandlingListener(final AlertReminderJobFactory reminderJobFactory) {
    alertReminderJobFactory = reminderJobFactory;
  }

  @Override
  public String getName() {
    return LISTENER_NAME;
  }

  @Override
  public void jobToBeExecuted(final JobExecutionContext context) {
    // do nothing
  }

  @Override
  public void jobExecutionVetoed(final JobExecutionContext context) {
    // do nothing
  }

  @Override
  public void jobWasExecuted(
      final JobExecutionContext context, final JobExecutionException jobException) {
    final AlertJobResult result = (AlertJobResult) context.getResult();
    if (result != null && result.isStatusChanged()) {
      // create reminders if needed
      if (result.getAlert().isTriggered() && result.getAlert().getReminder() != null) {
        log.debug("Creating reminder job for [{}]", result.getAlert().getId());
        final JobDetail jobDetails = alertReminderJobFactory.createJobDetails(result.getAlert());
        try {
          if (context.getScheduler().checkExists(jobDetails.getKey())) {
            log.debug(
                "Skipping creating new job with key [{}] as it already exists",
                jobDetails.getKey());
            return;
          }
          context
              .getScheduler()
              .scheduleJob(
                  jobDetails, alertReminderJobFactory.createTrigger(result.getAlert(), jobDetails));
        } catch (final Exception e) {
          log.error("can't schedule reminder for [{}]", result.getAlert().getId(), e);
        }
      } else {
        // remove reminders
        final JobKey jobKey = alertReminderJobFactory.getJobKey(result.getAlert());
        final TriggerKey triggerKey = alertReminderJobFactory.getTriggerKey(result.getAlert());

        try {
          context.getScheduler().unscheduleJob(triggerKey);
          context.getScheduler().deleteJob(jobKey);
        } catch (final SchedulerException e) {
          log.error("can't remove reminders for alert [{}]", result.getAlert().getId());
        }
      }
    }
  }
}

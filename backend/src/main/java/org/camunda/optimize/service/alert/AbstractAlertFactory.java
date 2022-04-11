/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

public abstract class AbstractAlertFactory<T extends Job> {

  private final ApplicationContext applicationContext;

  protected AbstractAlertFactory(final ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  private long durationInMs(AlertInterval checkInterval) {
    ChronoUnit parsedUnit = unitOf(checkInterval.getUnit().name());
    long millis = Duration.between(
        OffsetDateTime.now(),
        OffsetDateTime.now().plus(checkInterval.getValue(), parsedUnit)
    ).toMillis();
    return millis;
  }

  private ChronoUnit unitOf(String unit) {
    return ChronoUnit.valueOf(unit.toUpperCase());
  }

  public Trigger createTrigger(AlertDefinitionDto alert, JobDetail jobDetail) {
    SimpleTrigger trigger = null;
    if (getInterval(alert) != null) {
      OffsetDateTime startFuture = OffsetDateTime.now()
          .plus(
              getInterval(alert).getValue(),
              unitOf(getInterval(alert).getUnit().name())
          );

      trigger = newTrigger()
          .withIdentity(getTriggerName(alert), getTriggerGroup())
          .startAt(new Date(startFuture.toInstant().toEpochMilli()))
          .withSchedule(simpleSchedule()
              .withIntervalInMilliseconds(durationInMs(getInterval(alert)))
              .repeatForever()
          )
          .forJob(jobDetail)
          .build();
    }

    return trigger;
  }

  public JobDetail createJobDetails(AlertDefinitionDto alert) {

    JobDetailFactoryBean jobDetailFactoryBean = new JobDetailFactoryBean();
    jobDetailFactoryBean.setJobClass(getJobClass());
    jobDetailFactoryBean.setDurability(true);
    jobDetailFactoryBean.setName(getJobName(alert));
    jobDetailFactoryBean.setGroup(getJobGroup());

    Map<String, String> dataMap = new HashMap<>();
    dataMap.put("alertId", alert.getId());
    jobDetailFactoryBean.setJobDataAsMap(dataMap);
    jobDetailFactoryBean.setApplicationContext(getApplicationContext());

    jobDetailFactoryBean.afterPropertiesSet();

    return jobDetailFactoryBean.getObject();
  }

  public ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  protected JobKey getJobKey(AlertDefinitionDto alert) {
    return new JobKey(
        this.getJobName(alert),
        this.getJobGroup()
    );
  }

  protected TriggerKey getTriggerKey(AlertDefinitionDto toDelete) {
    return new TriggerKey(getTriggerName(toDelete), getTriggerGroup());
  }

  protected abstract AlertInterval getInterval(AlertDefinitionDto alert);

  protected abstract Class<T> getJobClass();

  protected abstract String getJobGroup();

  protected abstract String getJobName(AlertDefinitionDto alert);

  protected abstract String getTriggerName(AlertDefinitionDto alert);

  protected abstract String getTriggerGroup();
}

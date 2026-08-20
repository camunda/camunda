/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.alert;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;

public abstract class AbstractAlertFactory<T extends Job> {

  private final ApplicationContext applicationContext;

  protected AbstractAlertFactory(final ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  private long durationInMs(final AlertInterval checkInterval) {
    return Duration.between(
            OffsetDateTime.now(),
            OffsetDateTime.now()
                .plus(checkInterval.getValue(), unitOf(checkInterval.getUnit().name())))
        .toMillis();
  }

  private ChronoUnit unitOf(final String unit) {
    return ChronoUnit.valueOf(unit.toUpperCase());
  }

  public Trigger createTrigger(final AlertDefinitionDto alert, final JobDetail jobDetail) {
    SimpleTrigger trigger = null;
    if (getInterval(alert) != null) {
      final OffsetDateTime startFuture =
          OffsetDateTime.now()
              .plus(getInterval(alert).getValue(), unitOf(getInterval(alert).getUnit().name()));

      trigger =
          newTrigger()
              .withIdentity(getTriggerName(alert), getTriggerGroup())
              .startAt(new Date(startFuture.toInstant().toEpochMilli()))
              .withSchedule(
                  simpleSchedule()
                      .withIntervalInMilliseconds(durationInMs(getInterval(alert)))
                      .repeatForever())
              .forJob(jobDetail)
              .build();
    }
    return trigger;
  }

  public JobDetail createJobDetails(final AlertDefinitionDto alert) {
    final JobDetailFactoryBean jobDetailFactoryBean = new JobDetailFactoryBean();
    jobDetailFactoryBean.setJobClass(getJobClass());
    jobDetailFactoryBean.setDurability(true);
    jobDetailFactoryBean.setName(getJobName(alert));
    jobDetailFactoryBean.setGroup(getJobGroup());

    final Map<String, String> dataMap = new HashMap<>();
    dataMap.put("alertId", alert.getId());
    jobDetailFactoryBean.setJobDataAsMap(dataMap);
    jobDetailFactoryBean.setApplicationContext(getApplicationContext());

    jobDetailFactoryBean.afterPropertiesSet();

    return jobDetailFactoryBean.getObject();
  }

  public ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  protected JobKey getJobKey(final AlertDefinitionDto alert) {
    return new JobKey(this.getJobName(alert), this.getJobGroup());
  }

  protected TriggerKey getTriggerKey(final AlertDefinitionDto toDelete) {
    return new TriggerKey(getTriggerName(toDelete), getTriggerGroup());
  }

  protected abstract AlertInterval getInterval(AlertDefinitionDto alert);

  protected abstract Class<T> getJobClass();

  protected abstract String getJobGroup();

  protected abstract String getJobName(AlertDefinitionDto alert);

  protected abstract String getTriggerName(AlertDefinitionDto alert);

  protected abstract String getTriggerGroup();
}

package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.writer.AlertWriter;
import org.camunda.optimize.service.security.TokenService;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;


/**
 * @author Askar Akhmerov
 */
@Component
public class  AlertService  {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private AlertReader alertReader;

  @Autowired
  private AlertWriter alertWriter;

  @Autowired
  private TokenService tokenService;

  private SchedulerFactoryBean schedulerFactoryBean;
  private Class<? extends Job> alertJobClass = AlertJob.class;

  @PostConstruct
  private void init () {
    //clean up
    try {
      alertWriter.deleteAllStatuses();
    } catch (Exception e) {
      logger.error("can't clean alert statuses", e);
    }

    QuartzJobFactory sampleJobFactory = new QuartzJobFactory();
    sampleJobFactory.setApplicationContext(applicationContext);

    schedulerFactoryBean = new SchedulerFactoryBean();
    schedulerFactoryBean.setOverwriteExistingJobs(true);
    schedulerFactoryBean.setJobFactory(sampleJobFactory);


    Trigger[] array = createTriggers();
    schedulerFactoryBean.setTriggers(array);
    schedulerFactoryBean.setApplicationContext(applicationContext);
    schedulerFactoryBean.setConfigLocation(new ClassPathResource("quartz.properties"));
    try {
      schedulerFactoryBean.afterPropertiesSet();
    } catch (Exception e) {
      logger.error("can't instantiate scheduler", e);
    }
    schedulerFactoryBean.start();
  }

  public Scheduler getScheduler() {
    return schedulerFactoryBean.getObject();
  }

  private Trigger[] createTriggers() {
    List<AlertDefinitionDto> alerts = new ArrayList<>();
    try {
      alerts = this.getStoredAlerts();
    } catch (Exception e) {
      logger.error("can't initialize alerts", e);
    }
    List<Trigger> triggers = new ArrayList<>();
    for (AlertDefinitionDto alert : alerts) {
      triggers.add(statusCheckTrigger(alert));
    }

    return triggers.toArray(new Trigger[triggers.size()]);
  }

  public Trigger statusCheckTrigger(AlertDefinitionDto alert, JobDetail jobDetail) {
    SimpleTrigger trigger = null;
    if (alert.getCheckInterval() != null) {
      OffsetDateTime startFuture = OffsetDateTime.now()
          .plus(
              alert.getCheckInterval().getValue(),
              unitOf(alert.getCheckInterval().getUnit())
          );

      trigger = newTrigger()
          .withIdentity(alert.getId() + "-trigger", "statusCheck-trigger")
          .startAt(new Date(startFuture.toInstant().toEpochMilli()))
          .withSchedule(simpleSchedule()
              .withIntervalInMilliseconds(durationInMs(alert.getCheckInterval()))
              .repeatForever()
          )
          .forJob(jobDetail)
          .build();
    }

    return trigger;
  }

  public Trigger statusCheckTrigger(AlertDefinitionDto alert) {
    JobDetail jobDetail = statusCheckJobDetails(alert);

    return statusCheckTrigger(alert, jobDetail);
  }

  private long durationInMs(AlertInterval checkInterval) {
    ChronoUnit parsedUnit = unitOf(checkInterval.getUnit());
    long millis = Duration.between(
        OffsetDateTime.now(),
        OffsetDateTime.now().plus(checkInterval.getValue(), parsedUnit)
    ).toMillis();
    return millis;
  }

  private ChronoUnit unitOf(String unit) {
    return ChronoUnit.valueOf(unit.toUpperCase());
  }

  public JobDetail statusCheckJobDetails(AlertDefinitionDto alert) {

    JobDetailFactoryBean jobDetailFactoryBean = new JobDetailFactoryBean();
    jobDetailFactoryBean.setJobClass(alertJobClass);
    jobDetailFactoryBean.setDurability(true);
    jobDetailFactoryBean.setName(alert.getId() + "-job");
    jobDetailFactoryBean.setGroup("statusCheck-job");

    Map<String, String> dataMap = new HashMap<>();
    dataMap.put("alertId", alert.getId());
    jobDetailFactoryBean.setJobDataAsMap(dataMap);
    jobDetailFactoryBean.setApplicationContext(applicationContext);

    jobDetailFactoryBean.afterPropertiesSet();

    return jobDetailFactoryBean.getObject();
  }

  public List<AlertDefinitionDto> getStoredAlerts() {
    return alertReader.getStoredAlerts();
  }

  public AlertDefinitionDto createAlert(AlertCreationDto toCreate, String token) {
    String userId = tokenService.getTokenIssuer(token);
    return this.createAlertForUser(toCreate, userId);
  }

  public AlertDefinitionDto createAlertForUser(AlertCreationDto toCreate, String userId) {
    AlertDefinitionDto alert = alertWriter.createAlert(newAlert(toCreate, userId));
    try {
      JobDetail jobDetail = statusCheckJobDetails(alert);
      schedulerFactoryBean.getObject().scheduleJob(jobDetail, statusCheckTrigger(alert, jobDetail));
    } catch (SchedulerException e) {
      logger.error("can't schedule new alert", e);
    }

    return alert;
  }

  private static AlertDefinitionDto newAlert(AlertCreationDto toCreate, String userId) {
    AlertDefinitionDto result = new AlertDefinitionDto();
    result.setCreated(OffsetDateTime.now());
    result.setOwner(userId);
    AlertUtil.updateFromUser(userId, result);

    AlertUtil.mapBasicFields(toCreate, result);
    return result;
  }

  public void updateAlert(String alertId, AlertCreationDto toCreate, String token) {
    String userId = tokenService.getTokenIssuer(token);
    this.updateAlertForUser(alertId, toCreate, userId);
  }

  private void updateAlertForUser(String alertId, AlertCreationDto toCreate, String userId) {
    AlertDefinitionDto toUpdate = alertReader.findAlert(alertId);
    AlertUtil.updateFromUser(userId, toUpdate);
    AlertUtil.mapBasicFields(toCreate, toUpdate);
    alertWriter.updateAlert(toUpdate);
  }

  public void deleteAlert(String alertId) {
    alertWriter.deleteAlert(alertId);
  }
}

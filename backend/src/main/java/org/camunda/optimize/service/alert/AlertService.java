package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.writer.AlertWriter;
import org.camunda.optimize.service.security.TokenService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
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
import java.util.Set;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_NONE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_NUMBER_VISUALIZATION;
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

  @Autowired
  private ConfigurationService configurationService;

  private SchedulerFactoryBean schedulerFactoryBean;
  private Class<? extends Job> alertJobClass = AlertJob.class;

  @PostConstruct
  private void init () {
    QuartzJobFactory sampleJobFactory = new QuartzJobFactory();
    sampleJobFactory.setApplicationContext(applicationContext);

    schedulerFactoryBean = new SchedulerFactoryBean();
    schedulerFactoryBean.setOverwriteExistingJobs(true);
    schedulerFactoryBean.setJobFactory(sampleJobFactory);


    Trigger[] array = createTriggers();
    schedulerFactoryBean.setTriggers(array);
    schedulerFactoryBean.setApplicationContext(applicationContext);
    schedulerFactoryBean.setQuartzProperties(configurationService.getQuartzProperties());
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
      triggers.add(createStatusCheckTrigger(alert));
    }

    return triggers.toArray(new Trigger[triggers.size()]);
  }

  public Trigger createStatusCheckTrigger(AlertDefinitionDto alert, JobDetail jobDetail) {
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

  public Trigger createStatusCheckTrigger(AlertDefinitionDto alert) {
    JobDetail jobDetail = createStatusCheckJobDetails(alert);

    return createStatusCheckTrigger(alert, jobDetail);
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

  public JobDetail createStatusCheckJobDetails(AlertDefinitionDto alert) {

    JobDetailFactoryBean jobDetailFactoryBean = new JobDetailFactoryBean();
    jobDetailFactoryBean.setJobClass(alertJobClass);
    jobDetailFactoryBean.setDurability(true);
    jobDetailFactoryBean.setName(getJobName(alert));
    jobDetailFactoryBean.setGroup(getJobGroup());

    Map<String, String> dataMap = new HashMap<>();
    dataMap.put("alertId", alert.getId());
    jobDetailFactoryBean.setJobDataAsMap(dataMap);
    jobDetailFactoryBean.setApplicationContext(applicationContext);

    jobDetailFactoryBean.afterPropertiesSet();

    return jobDetailFactoryBean.getObject();
  }

  private String getJobGroup() {
    return "statusCheck-job";
  }

  private String getJobName(AlertDefinitionDto alert) {
    return alert.getId() + "-job";
  }

  public List<AlertDefinitionDto> getStoredAlerts() {
    return alertReader.getStoredAlerts();
  }

  public String createAlert(AlertCreationDto toCreate, String token) {
    String userId = tokenService.getTokenIssuer(token);
    return this.createAlertForUser(toCreate, userId).getId();
  }

  protected AlertDefinitionDto createAlertForUser(AlertCreationDto toCreate, String userId) {
    AlertDefinitionDto alert = alertWriter.createAlert(newAlert(toCreate, userId));
    scheduleAlert(alert);
    return alert;
  }

  private void scheduleAlert(AlertDefinitionDto alert) {
    try {
      JobDetail jobDetail = createStatusCheckJobDetails(alert);
      if (schedulerFactoryBean != null) {
        getScheduler().scheduleJob(jobDetail, createStatusCheckTrigger(alert, jobDetail));
      }
    } catch (SchedulerException e) {
      logger.error("can't schedule new alert", e);
    }
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
    unscheduleJob(toUpdate);
    AlertUtil.updateFromUser(userId, toUpdate);
    AlertUtil.mapBasicFields(toCreate, toUpdate);
    alertWriter.updateAlert(toUpdate);
    scheduleAlert(toUpdate);
  }

  public void deleteAlert(String alertId) {
    AlertDefinitionDto toDelete = alertReader.findAlert(alertId);
    alertWriter.deleteAlert(alertId);
    unscheduleJob(toDelete);
  }

  private void unscheduleJob(AlertDefinitionDto toDelete) {
    String alertId = toDelete.getId();
    try {
      Set<JobKey> jobKeys = getScheduler().getJobKeys(GroupMatcher.jobGroupEquals(getJobGroup()));
      JobKey toUnschedule = null;

      for (JobKey key : jobKeys) {
        if (key.getName().equals(getJobName(toDelete))) {
          toUnschedule = key;
        }
      }

      if (toUnschedule != null) {
        getScheduler().deleteJob(toUnschedule);
      }

    } catch (SchedulerException e) {
      logger.error("can't adjust scheduler for alert [{}]", alertId, e);
    }
  }

  public void deleteAlertsForReport(String reportId) {
    List<AlertDefinitionDto> alerts = alertReader.findAlertsForReport(reportId);

    for (AlertDefinitionDto alert : alerts) {
      unscheduleJob(alert);
    }

    alertWriter.deleteAlertsForReport(reportId);
  }

  /**
   * Check if it's still evaluated as number.
   * @param reportId
   * @param data
   */
  public void deleteAlertsIfNeeded(String reportId, ReportDataDto data) {
    if (
        data == null ||
        data.getGroupBy() == null ||
            (!GROUP_BY_NONE_TYPE.equals(data.getGroupBy().getType()) ||
                !SINGLE_NUMBER_VISUALIZATION.equals(data.getVisualization())
            )
        ) {
      this.deleteAlertsForReport(reportId);
    }
  }
}

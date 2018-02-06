package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.writer.AlertWriter;
import org.camunda.optimize.service.security.TokenService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.GROUP_BY_NONE_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_NUMBER_VISUALIZATION;


/**
 * @author Askar Akhmerov
 */
@Component
public class  AlertService {
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

  @Autowired
  private AlertReminderJobFactory alertReminderJobFactory;

  @Autowired
  private AlertCheckJobFactory alertCheckJobFactory;

  private SchedulerFactoryBean schedulerFactoryBean;

  @PostConstruct
  public void init () {

    if (schedulerFactoryBean == null) {
      QuartzJobFactory sampleJobFactory = new QuartzJobFactory();
      sampleJobFactory.setApplicationContext(applicationContext);

      schedulerFactoryBean = new SchedulerFactoryBean();
      schedulerFactoryBean.setOverwriteExistingJobs(true);
      schedulerFactoryBean.setJobFactory(sampleJobFactory);

      List<AlertDefinitionDto> alerts = new ArrayList<>();
      try {
        alerts = this.getStoredAlerts();
      } catch (Exception e) {
        logger.error("can't initialize alerts", e);
      }

      Map<AlertDefinitionDto, JobDetail> checkingDetails = createCheckDetails(alerts);
      List<Trigger> checkingTriggers = createCheckTriggers(checkingDetails);

      Map<AlertDefinitionDto, JobDetail> reminderDetails = createReminderDetails(alerts);
      List<Trigger> reminderTriggers = createReminderTriggers(reminderDetails);

      List<Trigger> allTriggers = new ArrayList<>();
      allTriggers.addAll(checkingTriggers);
      allTriggers.addAll(reminderTriggers);

      List<JobDetail> allJobDetails = new ArrayList<>();
      allJobDetails.addAll(checkingDetails.values());
      allJobDetails.addAll(reminderDetails.values());

      JobDetail[] jobDetails = allJobDetails.toArray(new JobDetail[allJobDetails.size()]);
      Trigger[] triggers = allTriggers.toArray(new Trigger[allTriggers.size()]);

      schedulerFactoryBean.setGlobalJobListeners(createReminderListener());
      schedulerFactoryBean.setTriggers(triggers);
      schedulerFactoryBean.setJobDetails(jobDetails);
      schedulerFactoryBean.setApplicationContext(applicationContext);
      schedulerFactoryBean.setQuartzProperties(configurationService.getQuartzProperties());
      try {
        schedulerFactoryBean.afterPropertiesSet();
      } catch (Exception e) {
        logger.error("can't instantiate scheduler", e);
      }
      schedulerFactoryBean.start();
    }

  }

  private List<Trigger> createReminderTriggers(Map<AlertDefinitionDto, JobDetail> reminderDetails) {
    List<Trigger> triggers = new ArrayList<>();

    for (Map.Entry <AlertDefinitionDto, JobDetail> e : reminderDetails.entrySet()) {
      triggers.add(alertReminderJobFactory.createTrigger(e.getKey(), e.getValue()));
    }

    return triggers;
  }

  private List<Trigger> createCheckTriggers(Map<AlertDefinitionDto, JobDetail> details) {
    List<Trigger> triggers = new ArrayList<>();

    for (Map.Entry <AlertDefinitionDto, JobDetail> e : details.entrySet()) {
      triggers.add(alertCheckJobFactory.createTrigger(e.getKey(), e.getValue()));
    }

    return triggers;
  }

  private Map<AlertDefinitionDto, JobDetail> createReminderDetails(List<AlertDefinitionDto> alerts) {
    Map<AlertDefinitionDto, JobDetail> result = new HashMap<>();

    for (AlertDefinitionDto alert : alerts) {
      result.put(alert, alertReminderJobFactory.createJobDetails(alert));
    }

    return result;
  }

  private Map<AlertDefinitionDto, JobDetail> createCheckDetails(List<AlertDefinitionDto> alerts) {
    Map<AlertDefinitionDto, JobDetail> result = new HashMap<>();

    for (AlertDefinitionDto alert : alerts) {
      result.put(alert, alertCheckJobFactory.createJobDetails(alert));
    }

    return result;
  }

  private JobListener createReminderListener() {
    return new ReminderHandlingListener(alertReminderJobFactory);
  }

  public Scheduler getScheduler() {
    return schedulerFactoryBean.getObject();
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
      JobDetail jobDetail = alertCheckJobFactory.createJobDetails(alert);
      if (schedulerFactoryBean != null) {
        getScheduler().scheduleJob(jobDetail, alertCheckJobFactory.createTrigger(alert, jobDetail));
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
      unscheduleCheckJob(toDelete);
      unscheduleReminderJob(toDelete);

    } catch (SchedulerException e) {
      logger.error("can't adjust scheduler for alert [{}]", alertId, e);
    }
    toDelete.setTriggered(false);
  }

  private void unscheduleReminderJob(AlertDefinitionDto toDelete) throws SchedulerException {
    JobKey toUnschedule = alertReminderJobFactory.getJobKey(toDelete);
    TriggerKey triggerKey = alertReminderJobFactory.getTriggerKey(toDelete);

    if (toUnschedule != null) {
      getScheduler().unscheduleJob(triggerKey);
      getScheduler().deleteJob(toUnschedule);
    }
  }



  private void unscheduleCheckJob(AlertDefinitionDto toDelete) throws SchedulerException {
    JobKey toUnschedule = alertCheckJobFactory.getJobKey(toDelete);
    TriggerKey triggerKey = alertReminderJobFactory.getTriggerKey(toDelete);

    if (toUnschedule != null) {
      getScheduler().unscheduleJob(triggerKey);
      getScheduler().deleteJob(toUnschedule);
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

  public JobDetail createStatusCheckJobDetails(AlertDefinitionDto fakeReportAlert) {
    return this.alertCheckJobFactory.createJobDetails(fakeReportAlert);
  }

  public Trigger createStatusCheckTrigger(AlertDefinitionDto fakeReportAlert, JobDetail jobDetail) {
    return this.alertCheckJobFactory.createTrigger(fakeReportAlert, jobDetail);
  }

  public void destroy() {
    if (schedulerFactoryBean != null) {
      schedulerFactoryBean.stop();
      try {
        schedulerFactoryBean.destroy();
      } catch (SchedulerException e) {
        logger.error("Can't destroy scheduler", e);
      }
      this.schedulerFactoryBean = null;
    }
  }
}

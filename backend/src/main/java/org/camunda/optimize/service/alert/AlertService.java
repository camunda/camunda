/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.writer.AlertWriter;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.relations.ReportReferencingService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.camunda.bpm.engine.EntityTypes.REPORT;
import static org.camunda.optimize.service.es.schema.index.AlertIndex.CHECK_INTERVAL;
import static org.camunda.optimize.service.es.schema.index.AlertIndex.INTERVAL_UNIT;
import static org.camunda.optimize.service.es.schema.index.AlertIndex.THRESHOLD_OPERATOR;

@RequiredArgsConstructor
@Component
@Slf4j
public class AlertService implements ReportReferencingService {

  private final ApplicationContext applicationContext;
  private final AlertReader alertReader;
  private final AlertWriter alertWriter;
  private final ConfigurationService configurationService;
  private final AlertReminderJobFactory alertReminderJobFactory;
  private final AlertCheckJobFactory alertCheckJobFactory;
  private final ReportService reportService;
  private final AuthorizedCollectionService authorizedCollectionService;
  private final AlertRecipientValidator alertRecipientValidator;

  private SchedulerFactoryBean schedulerFactoryBean;

  @PostConstruct
  public void init() {
    List<AlertDefinitionDto> alerts = alertReader.getStoredAlerts();
    checkAlertWebhooksAllExist(alerts);
    try {
      if (schedulerFactoryBean == null) {
        SpringBeanJobFactory sampleJobFactory = new SpringBeanJobFactory();
        sampleJobFactory.setApplicationContext(applicationContext);

        schedulerFactoryBean = new SchedulerFactoryBean();
        schedulerFactoryBean.setOverwriteExistingJobs(true);
        schedulerFactoryBean.setJobFactory(sampleJobFactory);

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

        JobDetail[] jobDetails = allJobDetails.toArray(new JobDetail[0]);
        Trigger[] triggers = allTriggers.toArray(new Trigger[0]);

        schedulerFactoryBean.setGlobalJobListeners(createReminderListener());
        schedulerFactoryBean.setTriggers(triggers);
        schedulerFactoryBean.setJobDetails(jobDetails);
        schedulerFactoryBean.setApplicationContext(applicationContext);
        schedulerFactoryBean.setQuartzProperties(configurationService.getQuartzProperties());
        schedulerFactoryBean.afterPropertiesSet();
        // we need to set this to make sure that there are no further threads running
        // after the quartz scheduler has been stopped.
        schedulerFactoryBean.setWaitForJobsToCompleteOnShutdown(true);
        schedulerFactoryBean.start();
      }
    } catch (Exception e) {
      log.error("Couldn't initialize alert scheduling.", e);
      try {
        destroy();
      } catch (Exception destroyException) {
        log.error("Failed destroying alertService", destroyException);
      }
      throw new RuntimeException(e);
    }
  }

  @PreDestroy
  public void destroy() {
    if (schedulerFactoryBean != null) {
      try {
        schedulerFactoryBean.stop();
        schedulerFactoryBean.destroy();
      } catch (Exception e) {
        log.error("Can't destroy scheduler", e);
      }
      this.schedulerFactoryBean = null;
    }
  }

  public Scheduler getScheduler() {
    return schedulerFactoryBean.getObject();
  }

  public List<AlertDefinitionDto> getStoredAlertsForCollection(String userId, String collectionId) {
    List<String> authorizedReportIds = reportService
      .findAndFilterReports(userId, collectionId)
      .stream()
      .map(authorizedReportDefinitionDto -> authorizedReportDefinitionDto.getDefinitionDto().getId())
      .collect(toList());

    return alertReader.getAlertsForReports(authorizedReportIds);
  }

  public JobDetail createStatusCheckJobDetails(AlertDefinitionDto fakeReportAlert) {
    return this.alertCheckJobFactory.createJobDetails(fakeReportAlert);
  }

  public Trigger createStatusCheckTrigger(AlertDefinitionDto fakeReportAlert, JobDetail jobDetail) {
    return this.alertCheckJobFactory.createTrigger(fakeReportAlert, jobDetail);
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportDelete(final ReportDefinitionDto reportDefinition) {
    return mapAlertsToConflictingItems(getAlertsForReport(reportDefinition.getId()));
  }

  @Override
  public void handleReportDeleted(final ReportDefinitionDto reportDefinition) {
    deleteAlertsForReport(reportDefinition.getId());
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportUpdate(ReportDefinitionDto currentDefinition,
                                                                  ReportDefinitionDto updateDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();

    if (currentDefinition instanceof SingleProcessReportDefinitionRequestDto) {
      if (validateIfReportIsSuitableForAlert((SingleProcessReportDefinitionRequestDto) currentDefinition)
        && !validateIfReportIsSuitableForAlert((SingleProcessReportDefinitionRequestDto) updateDefinition)) {
        conflictedItems.addAll(mapAlertsToConflictingItems(getAlertsForReport(currentDefinition.getId())));
      }
    } else if (currentDefinition instanceof SingleDecisionReportDefinitionRequestDto) {
      if (validateIfReportIsSuitableForAlert((SingleDecisionReportDefinitionRequestDto) currentDefinition)
        && !validateIfReportIsSuitableForAlert((SingleDecisionReportDefinitionRequestDto) updateDefinition)) {
        conflictedItems.addAll(mapAlertsToConflictingItems(getAlertsForReport(currentDefinition.getId())));
      }
    }

    return conflictedItems;
  }

  @Override
  public void handleReportUpdated(final String reportId, final ReportDefinitionDto updateDefinition) {
    deleteAlertsIfNeeded(reportId, updateDefinition);
  }

  private List<AlertDefinitionDto> getAlertsForReport(String reportId) {
    return alertReader.getAlertsForReport(reportId);
  }

  private AlertDefinitionDto getAlert(String alertId) {
    return alertReader.getAlert(alertId).orElseThrow(() -> new NotFoundException("Alert does not exist!"));
  }

  public IdResponseDto createAlert(AlertCreationRequestDto toCreate, String userId) {
    validateAlert(toCreate, userId);
    verifyUserAuthorizedToEditAlertOrFail(toCreate, userId);
    String alertId = this.createAlertForUser(toCreate, userId).getId();
    return new IdResponseDto(alertId);
  }

  public void copyAndMoveAlerts(String oldReportId, String newReportId) {
    List<AlertDefinitionDto> oldAlerts = getAlertsForReport(oldReportId);
    for (AlertDefinitionDto alert : oldAlerts) {
      alert.setReportId(newReportId);
      createAlert(alert, alert.getOwner());
    }
  }

  public void updateAlert(String alertId, AlertCreationRequestDto toCreate, String userId) {
    validateAlert(toCreate, userId);
    this.updateAlertForUser(alertId, toCreate, userId);
  }

  public void deleteAlert(String alertId, String userId) {
    verifyUserAuthorizedToEditAlertOrFail(getAlert(alertId), userId);

    AlertDefinitionDto toDelete = getAlert(alertId);
    alertWriter.deleteAlert(alertId);
    unscheduleJob(toDelete);
  }

  public void deleteAlerts(List<String> alertIds, String userId) {
    List<String> alertIdsToDelete = new ArrayList<>();
    for (String alertId : alertIds) {
      try {
        verifyUserAuthorizedToEditAlertOrFail(getAlert(alertId), userId);
        alertIdsToDelete.add(alertId);
      } catch (NotFoundException e) {
        log.debug("Cannot find alert with id [{}], it may have been deleted already", alertId);
      }
    }
    alertWriter.deleteAlerts(alertIdsToDelete);
  }

  private void unscheduleJob(AlertDefinitionDto toDelete) {
    String alertId = toDelete.getId();
    try {
      unscheduleCheckJob(toDelete);
      unscheduleReminderJob(toDelete);
    } catch (SchedulerException e) {
      log.error("can't adjust scheduler for alert [{}]", alertId, e);
    }
    toDelete.setTriggered(false);
  }

  private static AlertDefinitionDto newAlert(AlertCreationRequestDto toCreate, String userId) {
    AlertDefinitionDto result = new AlertDefinitionDto();
    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    result.setOwner(userId);
    result.setCreated(now);
    result.setLastModified(now);
    result.setLastModifier(userId);
    AlertUtil.mapBasicFields(toCreate, result);
    return result;
  }

  private void updateAlertForUser(String alertId, AlertCreationRequestDto toCreate, String userId) {
    verifyUserAuthorizedToEditAlertOrFail(toCreate, userId);

    AlertDefinitionDto toUpdate = getAlert(alertId);
    unscheduleJob(toUpdate);
    toUpdate.setLastModified(LocalDateUtil.getCurrentDateTime());
    toUpdate.setLastModifier(userId);
    AlertUtil.mapBasicFields(toCreate, toUpdate);
    alertWriter.updateAlert(toUpdate);
    scheduleAlert(toUpdate);
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

  private void deleteAlertsForReport(String reportId) {
    alertReader.getAlertsForReport(reportId).forEach(this::unscheduleJob);
    alertWriter.deleteAlertsForReport(reportId);
  }

  /**
   * Check if it's still evaluated as number.
   */
  private void deleteAlertsIfNeeded(String reportId, ReportDefinitionDto reportDefinition) {
    if (reportDefinition instanceof SingleProcessReportDefinitionRequestDto) {
      SingleProcessReportDefinitionRequestDto singleReport = (SingleProcessReportDefinitionRequestDto) reportDefinition;
      if (!validateIfReportIsSuitableForAlert(singleReport)) {
        this.deleteAlertsForReport(reportId);
      }
    }
  }

  private boolean validateIfReportIsSuitableForAlert(SingleProcessReportDefinitionRequestDto report) {
    final ProcessReportDataDto data = report.getData();
    return data != null && data.getGroupBy() != null
      && ProcessGroupByType.NONE.equals(data.getGroupBy().getType())
      && ProcessVisualization.NUMBER.equals(data.getVisualization())
      && data.getView().getProperties().size() == 1
      && data.getConfiguration().getAggregationTypes().size() == 1
      && data.getConfiguration().getUserTaskDurationTimes().size() == 1;
  }

  private boolean validateIfReportIsSuitableForAlert(SingleDecisionReportDefinitionRequestDto report) {
    final DecisionReportDataDto data = report.getData();
    return data != null && data.getGroupBy() != null
      && DecisionGroupByType.NONE.equals(data.getGroupBy().getType())
      && DecisionVisualization.NUMBER.equals(data.getVisualization());
  }

  private Set<ConflictedItemDto> mapAlertsToConflictingItems(List<AlertDefinitionDto> alertsForReport) {
    return alertsForReport.stream()
      .map(alertDto -> new ConflictedItemDto(alertDto.getId(), ConflictedItemType.ALERT, alertDto.getName()))
      .collect(toSet());
  }

  private void verifyUserAuthorizedToEditAlertOrFail(final AlertCreationRequestDto alertDto, final String userId) {
    AuthorizedReportDefinitionResponseDto reportDefinitionDto = reportService.getReportDefinition(
      alertDto.getReportId(),
      userId
    );
    authorizedCollectionService.verifyUserAuthorizedToEditCollectionResources(
      userId,
      reportDefinitionDto.getDefinitionDto()
        .getCollectionId()
    );
  }

  private void validateAlert(AlertCreationRequestDto toCreate, String userId) {
    ReportDefinitionDto report;
    try {
      report = reportService.getReportDefinition(toCreate.getReportId(), userId).getDefinitionDto();
    } catch (Exception e) {
      String errorMessage = "Could not create alert [" + toCreate.getName() + "]. Report id [" +
        toCreate.getReportId() + "] does not exist.";
      log.error(errorMessage);
      throw new BadRequestException(errorMessage, e);
    }

    if (report.getCollectionId() == null || report.getCollectionId().isEmpty()) {
      throw new BadRequestException(
        "Alerts cannot be created for private reports, only for reports within a collection.");
    }

    ValidationHelper.ensureNotEmpty(REPORT, report);

    ValidationHelper.ensureNotEmpty(THRESHOLD_OPERATOR, toCreate.getThresholdOperator());
    ValidationHelper.ensureNotNull(CHECK_INTERVAL, toCreate.getCheckInterval());
    ValidationHelper.ensureNotEmpty(INTERVAL_UNIT, toCreate.getCheckInterval().getUnit());

    if (report.getData() instanceof ProcessReportDataDto) {
      ProcessReportDataDto data = (ProcessReportDataDto) report.getData();
      if (data.getView() != null && data.getView().getFirstProperty() != null &&
        data.getView().getFirstProperty().equals(ViewProperty.PERCENTAGE) &&
        (toCreate.getThreshold() == null || (toCreate.getThreshold() > 100 || toCreate.getThreshold() < 0))) {
        throw new OptimizeValidationException("The threshold for alerts on % reports must be between 0 and 100.");
      }
    }

    final boolean emailsDefined = CollectionUtils.isNotEmpty(toCreate.getEmails());
    if (emailsDefined) {
      final List<String> validatedRecipients =
        alertRecipientValidator.getValidatedRecipientEmailList(toCreate.getEmails());
      toCreate.setEmails(validatedRecipients);
    }
    final boolean webhookDefined = StringUtils.isNotBlank(toCreate.getWebhook());
    if (!emailsDefined && !webhookDefined) {
      throw new OptimizeValidationException(
        "The fields [emails] and [webhook] are not allowed to both be empty. At least one of them must be set.");
    }
  }

  private AlertDefinitionDto createAlertForUser(AlertCreationRequestDto toCreate, String userId) {
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
      log.error("can't schedule new alert", e);
    }
  }

  private List<Trigger> createReminderTriggers(Map<AlertDefinitionDto, JobDetail> reminderDetails) {
    return reminderDetails.entrySet()
      .stream()
      .filter(entry -> Objects.nonNull(entry.getKey().getReminder()))
      .map(entry -> alertReminderJobFactory.createTrigger(entry.getKey(), entry.getValue()))
      .collect(Collectors.toList());
  }

  private List<Trigger> createCheckTriggers(Map<AlertDefinitionDto, JobDetail> details) {
    return details.entrySet()
      .stream()
      .map(entry -> alertCheckJobFactory.createTrigger(entry.getKey(), entry.getValue()))
      .collect(Collectors.toList());
  }

  private Map<AlertDefinitionDto, JobDetail> createReminderDetails(List<AlertDefinitionDto> alerts) {
    return alerts.stream().collect(Collectors.toMap(alert -> alert, alertReminderJobFactory::createJobDetails));
  }

  private Map<AlertDefinitionDto, JobDetail> createCheckDetails(List<AlertDefinitionDto> alerts) {
    return alerts.stream().collect(Collectors.toMap(alert -> alert, alertCheckJobFactory::createJobDetails));
  }

  private JobListener createReminderListener() {
    return new ReminderHandlingListener(alertReminderJobFactory);
  }

  private void checkAlertWebhooksAllExist(List<AlertDefinitionDto> alerts) {
    final Set<String> webhooks = configurationService.getConfiguredWebhooks().keySet();
    final Map<String, List<String>> missingWebhookMap = alerts.stream()
      .filter(alert -> StringUtils.isNotEmpty(alert.getWebhook()) && !webhooks.contains(alert.getWebhook()))
      .collect(groupingBy(AlertCreationRequestDto::getWebhook, mapping(AlertDefinitionDto::getId, toList())));
    if (!missingWebhookMap.isEmpty()) {
      final String missingWebhookSummary = missingWebhookMap.entrySet()
        .stream()
        .map(entry -> String.format("Webhook: [%s] - Associated with alert(s): %s", entry.getKey(), entry.getValue()))
        .collect(Collectors.joining("\n"));
      final String errorMsg = String.format(
        "The following webhooks no longer exist in Optimize configuration, yet are associated with existing " +
          "alerts:%n%s",
        missingWebhookSummary
      );
      log.error(errorMsg);
    }
  }

}

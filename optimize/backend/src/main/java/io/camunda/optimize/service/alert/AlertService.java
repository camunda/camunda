/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.alert;

import static io.camunda.optimize.service.db.schema.index.AlertIndex.CHECK_INTERVAL;
import static io.camunda.optimize.service.db.schema.index.AlertIndex.INTERVAL_UNIT;
import static io.camunda.optimize.service.db.schema.index.AlertIndex.THRESHOLD_OPERATOR;
import static java.util.stream.Collectors.toSet;
import static org.camunda.bpm.engine.EntityTypes.REPORT;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByType;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.db.reader.AlertReader;
import io.camunda.optimize.service.db.writer.AlertWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.relations.ReportReferencingService;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.ValidationHelper;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.springframework.stereotype.Component;

@Component
public class AlertService implements ReportReferencingService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AlertService.class);
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

  public AlertService(
      final ApplicationContext applicationContext,
      final AlertReader alertReader,
      final AlertWriter alertWriter,
      final ConfigurationService configurationService,
      final AlertReminderJobFactory alertReminderJobFactory,
      final AlertCheckJobFactory alertCheckJobFactory,
      final ReportService reportService,
      final AuthorizedCollectionService authorizedCollectionService,
      final AlertRecipientValidator alertRecipientValidator) {
    this.applicationContext = applicationContext;
    this.alertReader = alertReader;
    this.alertWriter = alertWriter;
    this.configurationService = configurationService;
    this.alertReminderJobFactory = alertReminderJobFactory;
    this.alertCheckJobFactory = alertCheckJobFactory;
    this.reportService = reportService;
    this.authorizedCollectionService = authorizedCollectionService;
    this.alertRecipientValidator = alertRecipientValidator;
  }

  @PostConstruct
  public void init() {
    final List<AlertDefinitionDto> alerts = alertReader.getStoredAlerts();
    try {
      if (schedulerFactoryBean == null) {
        final SpringBeanJobFactory sampleJobFactory = new SpringBeanJobFactory();
        sampleJobFactory.setApplicationContext(applicationContext);

        schedulerFactoryBean = new SchedulerFactoryBean();
        schedulerFactoryBean.setOverwriteExistingJobs(true);
        schedulerFactoryBean.setJobFactory(sampleJobFactory);

        final Map<AlertDefinitionDto, JobDetail> checkingDetails = createCheckDetails(alerts);
        final List<Trigger> checkingTriggers = createCheckTriggers(checkingDetails);

        final Map<AlertDefinitionDto, JobDetail> reminderDetails = createReminderDetails(alerts);
        final List<Trigger> reminderTriggers = createReminderTriggers(reminderDetails);

        final List<Trigger> allTriggers = new ArrayList<>();
        allTriggers.addAll(checkingTriggers);
        allTriggers.addAll(reminderTriggers);

        final List<JobDetail> allJobDetails = new ArrayList<>();
        allJobDetails.addAll(checkingDetails.values());
        allJobDetails.addAll(reminderDetails.values());

        final JobDetail[] jobDetails = allJobDetails.toArray(new JobDetail[0]);
        final Trigger[] triggers = allTriggers.toArray(new Trigger[0]);

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
    } catch (final Exception e) {
      LOG.error("Couldn't initialize alert scheduling.", e);
      try {
        destroy();
      } catch (final Exception destroyException) {
        LOG.error("Failed destroying alertService", destroyException);
      }
      throw new OptimizeRuntimeException(e);
    }
  }

  @PreDestroy
  public void destroy() {
    if (schedulerFactoryBean != null) {
      try {
        schedulerFactoryBean.stop();
        schedulerFactoryBean.destroy();
      } catch (final Exception e) {
        LOG.error("Can't destroy scheduler", e);
      }
      schedulerFactoryBean = null;
    }
  }

  public Scheduler getScheduler() {
    return schedulerFactoryBean.getObject();
  }

  public List<AlertDefinitionDto> getStoredAlertsForCollection(
      final String userId, final String collectionId) {
    final List<String> authorizedReportIds =
        reportService.findAndFilterReports(userId, collectionId).stream()
            .map(
                authorizedReportDefinitionDto ->
                    authorizedReportDefinitionDto.getDefinitionDto().getId())
            .toList();

    return alertReader.getAlertsForReports(authorizedReportIds);
  }

  public JobDetail createStatusCheckJobDetails(final AlertDefinitionDto fakeReportAlert) {
    return alertCheckJobFactory.createJobDetails(fakeReportAlert);
  }

  public Trigger createStatusCheckTrigger(
      final AlertDefinitionDto fakeReportAlert, final JobDetail jobDetail) {
    return alertCheckJobFactory.createTrigger(fakeReportAlert, jobDetail);
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportDelete(
      final ReportDefinitionDto reportDefinition) {
    return mapAlertsToConflictingItems(getAlertsForReport(reportDefinition.getId()));
  }

  @Override
  public void handleReportDeleted(final ReportDefinitionDto reportDefinition) {
    deleteAlertsForReport(reportDefinition.getId());
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportUpdate(
      final ReportDefinitionDto currentDefinition, final ReportDefinitionDto updateDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();

    if (currentDefinition instanceof SingleProcessReportDefinitionRequestDto) {
      if (validateIfReportIsSuitableForAlert(
              (SingleProcessReportDefinitionRequestDto) currentDefinition)
          && !validateIfReportIsSuitableForAlert(
              (SingleProcessReportDefinitionRequestDto) updateDefinition)) {
        conflictedItems.addAll(
            mapAlertsToConflictingItems(getAlertsForReport(currentDefinition.getId())));
      }
    } else if (currentDefinition instanceof SingleDecisionReportDefinitionRequestDto) {
      if (validateIfReportIsSuitableForAlert(
              (SingleDecisionReportDefinitionRequestDto) currentDefinition)
          && !validateIfReportIsSuitableForAlert(
              (SingleDecisionReportDefinitionRequestDto) updateDefinition)) {
        conflictedItems.addAll(
            mapAlertsToConflictingItems(getAlertsForReport(currentDefinition.getId())));
      }
    }

    return conflictedItems;
  }

  @Override
  public void handleReportUpdated(
      final String reportId, final ReportDefinitionDto updateDefinition) {
    deleteAlertsIfNeeded(reportId, updateDefinition);
  }

  private List<AlertDefinitionDto> getAlertsForReport(final String reportId) {
    return alertReader.getAlertsForReport(reportId);
  }

  private AlertDefinitionDto getAlert(final String alertId) {
    return alertReader
        .getAlert(alertId)
        .orElseThrow(() -> new NotFoundException("Alert does not exist!"));
  }

  public IdResponseDto createAlert(final AlertCreationRequestDto toCreate, final String userId) {
    validateAlert(toCreate, userId);
    verifyUserAuthorizedToEditAlertOrFail(toCreate, userId);
    final String alertId = createAlertForUser(toCreate, userId).getId();
    return new IdResponseDto(alertId);
  }

  public void copyAndMoveAlerts(final String oldReportId, final String newReportId) {
    final List<AlertDefinitionDto> oldAlerts = getAlertsForReport(oldReportId);
    for (final AlertDefinitionDto alert : oldAlerts) {
      alert.setReportId(newReportId);
      createAlert(alert, alert.getOwner());
    }
  }

  public void updateAlert(
      final String alertId, final AlertCreationRequestDto toCreate, final String userId) {
    validateAlert(toCreate, userId);
    updateAlertForUser(alertId, toCreate, userId);
  }

  public void deleteAlert(final String alertId, final String userId) {
    verifyUserAuthorizedToEditAlertOrFail(getAlert(alertId), userId);

    final AlertDefinitionDto toDelete = getAlert(alertId);
    alertWriter.deleteAlert(alertId);
    unscheduleJob(toDelete);
  }

  public void deleteAlerts(final List<String> alertIds, final String userId) {
    final List<String> alertIdsToDelete = new ArrayList<>();
    for (final String alertId : alertIds) {
      try {
        verifyUserAuthorizedToEditAlertOrFail(getAlert(alertId), userId);
        alertIdsToDelete.add(alertId);
      } catch (final NotFoundException e) {
        LOG.debug("Cannot find alert with id [{}], it may have been deleted already", alertId);
      }
    }
    alertWriter.deleteAlerts(alertIdsToDelete);
  }

  private void unscheduleJob(final AlertDefinitionDto toDelete) {
    final String alertId = toDelete.getId();
    try {
      unscheduleCheckJob(toDelete);
      unscheduleReminderJob(toDelete);
    } catch (final SchedulerException e) {
      LOG.error("can't adjust scheduler for alert [{}]", alertId, e);
    }
    toDelete.setTriggered(false);
  }

  private static AlertDefinitionDto newAlert(
      final AlertCreationRequestDto toCreate, final String userId) {
    final AlertDefinitionDto result = new AlertDefinitionDto();
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    result.setOwner(userId);
    result.setCreated(now);
    result.setLastModified(now);
    result.setLastModifier(userId);
    AlertUtil.mapBasicFields(toCreate, result);
    return result;
  }

  private void updateAlertForUser(
      final String alertId, final AlertCreationRequestDto toCreate, final String userId) {
    verifyUserAuthorizedToEditAlertOrFail(toCreate, userId);

    final AlertDefinitionDto toUpdate = getAlert(alertId);
    unscheduleJob(toUpdate);
    toUpdate.setLastModified(LocalDateUtil.getCurrentDateTime());
    toUpdate.setLastModifier(userId);
    AlertUtil.mapBasicFields(toCreate, toUpdate);
    alertWriter.updateAlert(toUpdate);
    scheduleAlert(toUpdate);
  }

  private void unscheduleReminderJob(final AlertDefinitionDto toDelete) throws SchedulerException {
    final JobKey toUnschedule = alertReminderJobFactory.getJobKey(toDelete);
    final TriggerKey triggerKey = alertReminderJobFactory.getTriggerKey(toDelete);

    if (toUnschedule != null) {
      getScheduler().unscheduleJob(triggerKey);
      getScheduler().deleteJob(toUnschedule);
    }
  }

  private void unscheduleCheckJob(final AlertDefinitionDto toDelete) throws SchedulerException {
    final JobKey toUnschedule = alertCheckJobFactory.getJobKey(toDelete);
    final TriggerKey triggerKey = alertReminderJobFactory.getTriggerKey(toDelete);

    if (toUnschedule != null) {
      getScheduler().unscheduleJob(triggerKey);
      getScheduler().deleteJob(toUnschedule);
    }
  }

  private void deleteAlertsForReport(final String reportId) {
    alertReader.getAlertsForReport(reportId).forEach(this::unscheduleJob);
    alertWriter.deleteAlertsForReport(reportId);
  }

  /** Check if it's still evaluated as number. */
  private void deleteAlertsIfNeeded(
      final String reportId, final ReportDefinitionDto reportDefinition) {
    if (reportDefinition instanceof SingleProcessReportDefinitionRequestDto) {
      final SingleProcessReportDefinitionRequestDto singleReport =
          (SingleProcessReportDefinitionRequestDto) reportDefinition;
      if (!validateIfReportIsSuitableForAlert(singleReport)) {
        deleteAlertsForReport(reportId);
      }
    }
  }

  private boolean validateIfReportIsSuitableForAlert(
      final SingleProcessReportDefinitionRequestDto report) {
    final ProcessReportDataDto data = report.getData();
    return data != null
        && data.getGroupBy() != null
        && ProcessGroupByType.NONE.equals(data.getGroupBy().getType())
        && ProcessVisualization.NUMBER.equals(data.getVisualization())
        && data.getView().getProperties().size() == 1
        && data.getConfiguration().getAggregationTypes().size() == 1
        && data.getConfiguration().getUserTaskDurationTimes().size() == 1;
  }

  private boolean validateIfReportIsSuitableForAlert(
      final SingleDecisionReportDefinitionRequestDto report) {
    final DecisionReportDataDto data = report.getData();
    return data != null
        && data.getGroupBy() != null
        && DecisionGroupByType.NONE.equals(data.getGroupBy().getType())
        && DecisionVisualization.NUMBER.equals(data.getVisualization());
  }

  private Set<ConflictedItemDto> mapAlertsToConflictingItems(
      final List<AlertDefinitionDto> alertsForReport) {
    return alertsForReport.stream()
        .map(
            alertDto ->
                new ConflictedItemDto(
                    alertDto.getId(), ConflictedItemType.ALERT, alertDto.getName()))
        .collect(toSet());
  }

  private void verifyUserAuthorizedToEditAlertOrFail(
      final AlertCreationRequestDto alertDto, final String userId) {
    final AuthorizedReportDefinitionResponseDto reportDefinitionDto =
        reportService.getReportDefinition(alertDto.getReportId(), userId);
    authorizedCollectionService.verifyUserAuthorizedToEditCollectionResources(
        userId, reportDefinitionDto.getDefinitionDto().getCollectionId());
  }

  private void validateAlert(final AlertCreationRequestDto toCreate, final String userId) {
    final ReportDefinitionDto report;
    try {
      report = reportService.getReportDefinition(toCreate.getReportId(), userId).getDefinitionDto();
    } catch (final Exception e) {
      final String errorMessage =
          "Could not create alert ["
              + toCreate.getName()
              + "]. Report id ["
              + toCreate.getReportId()
              + "] does not exist.";
      LOG.error(errorMessage);
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
      final ProcessReportDataDto data = (ProcessReportDataDto) report.getData();
      if (data.getView() != null
          && data.getView().getFirstProperty() != null
          && data.getView().getFirstProperty().equals(ViewProperty.PERCENTAGE)
          && (toCreate.getThreshold() == null
              || (toCreate.getThreshold() > 100 || toCreate.getThreshold() < 0))) {
        throw new OptimizeValidationException(
            "The threshold for alerts on % reports must be between 0 and 100.");
      }
    }

    final boolean emailsDefined = CollectionUtils.isNotEmpty(toCreate.getEmails());
    if (emailsDefined) {
      alertRecipientValidator.validateAlertRecipientEmailAddresses(toCreate.getEmails());
    }
    if (!emailsDefined) {
      throw new OptimizeValidationException("The field [emails] is not allowed to be empty.");
    }
  }

  private AlertDefinitionDto createAlertForUser(
      final AlertCreationRequestDto toCreate, final String userId) {
    final AlertDefinitionDto alert = alertWriter.createAlert(newAlert(toCreate, userId));
    scheduleAlert(alert);
    return alert;
  }

  private void scheduleAlert(final AlertDefinitionDto alert) {
    try {
      final JobDetail jobDetail = alertCheckJobFactory.createJobDetails(alert);
      if (schedulerFactoryBean != null) {
        getScheduler().scheduleJob(jobDetail, alertCheckJobFactory.createTrigger(alert, jobDetail));
      }
    } catch (final SchedulerException e) {
      LOG.error("can't schedule new alert", e);
    }
  }

  private List<Trigger> createReminderTriggers(
      final Map<AlertDefinitionDto, JobDetail> reminderDetails) {
    return reminderDetails.entrySet().stream()
        .filter(entry -> Objects.nonNull(entry.getKey().getReminder()))
        .map(entry -> alertReminderJobFactory.createTrigger(entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());
  }

  private List<Trigger> createCheckTriggers(final Map<AlertDefinitionDto, JobDetail> details) {
    return details.entrySet().stream()
        .map(entry -> alertCheckJobFactory.createTrigger(entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());
  }

  private Map<AlertDefinitionDto, JobDetail> createReminderDetails(
      final List<AlertDefinitionDto> alerts) {
    return alerts.stream()
        .collect(Collectors.toMap(alert -> alert, alertReminderJobFactory::createJobDetails));
  }

  private Map<AlertDefinitionDto, JobDetail> createCheckDetails(
      final List<AlertDefinitionDto> alerts) {
    return alerts.stream()
        .collect(Collectors.toMap(alert -> alert, alertCheckJobFactory::createJobDetails));
  }

  private JobListener createReminderListener() {
    return new ReminderHandlingListener(alertReminderJobFactory);
  }
}

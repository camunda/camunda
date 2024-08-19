/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.alert;

import static io.camunda.optimize.dto.optimize.alert.AlertNotificationType.NEW;
import static io.camunda.optimize.dto.optimize.alert.AlertNotificationType.REMINDER;
import static io.camunda.optimize.dto.optimize.alert.AlertNotificationType.RESOLVED;

import io.camunda.optimize.dto.optimize.alert.AlertNotificationDto;
import io.camunda.optimize.dto.optimize.alert.AlertNotificationType;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertThresholdOperator;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.PlainReportEvaluationHandler;
import io.camunda.optimize.service.db.es.report.ReportEvaluationInfo;
import io.camunda.optimize.service.db.reader.AlertReader;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.db.writer.AlertWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DurationFormatterUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AlertJob implements Job {

  private static final String HTTP_PREFIX = "http://";
  private static final String HTTPS_PREFIX = "https://";
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(AlertJob.class);

  private final ConfigurationService configurationService;
  private final List<AlertNotificationService> notificationServices;
  private final AlertReader alertReader;
  private final ReportReader reportReader;
  private final AlertWriter alertWriter;
  private final PlainReportEvaluationHandler reportEvaluator;

  public AlertJob(
      final ConfigurationService configurationService,
      final List<AlertNotificationService> notificationServices,
      final AlertReader alertReader,
      final ReportReader reportReader,
      final AlertWriter alertWriter,
      final PlainReportEvaluationHandler reportEvaluator) {
    this.configurationService = configurationService;
    this.notificationServices = notificationServices;
    this.alertReader = alertReader;
    this.reportReader = reportReader;
    this.alertWriter = alertWriter;
    this.reportEvaluator = reportEvaluator;
  }

  @Override
  public void execute(final JobExecutionContext jobExecutionContext) {
    final JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
    final String alertId = dataMap.getString("alertId");
    log.debug("Executing status check for alert [{}]", alertId);

    final AlertDefinitionDto alert =
        alertReader
            .getAlert(alertId)
            .orElseThrow(() -> new OptimizeRuntimeException("Alert does not exist!"));
    try {
      final ReportDefinitionDto<?> reportDefinition =
          reportReader
              .getReport(alert.getReportId())
              .orElseThrow(
                  () ->
                      new OptimizeRuntimeException(
                          "Was not able to retrieve report with id "
                              + alert.getReportId()
                              + "] from Elasticsearch. Report does not exist."));
      final ReportEvaluationInfo reportEvaluationInfo =
          ReportEvaluationInfo.builder(reportDefinition).build();
      @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
      final SingleReportEvaluationResult<Double> evaluationResult =
          (SingleReportEvaluationResult<Double>)
              reportEvaluator.evaluateReport(reportEvaluationInfo).getEvaluationResult();
      final Double reportResult = evaluationResult.getFirstCommandResult().getFirstMeasureData();

      if (thresholdExceeded(alert, reportResult)) {
        jobExecutionContext.setResult(
            handleAlertTriggered(
                jobExecutionContext.getJobDetail().getKey(),
                alert,
                reportDefinition,
                reportResult));
      } else if (alert.isTriggered()) {
        // the alert was triggered before but the threshold is not exceeded anymore, thus the alert
        // got resolved
        jobExecutionContext.setResult(handleAlertResolved(alert, reportDefinition, reportResult));
      }
    } catch (final Exception e) {
      log.error(
          "Error while processing alert [{}] for report [{}]", alertId, alert.getReportId(), e);
    }
  }

  private AlertJobResult handleAlertTriggered(
      final JobKey key,
      final AlertDefinitionDto alert,
      final ReportDefinitionDto<?> reportDefinition,
      final Double currentValue) {
    final boolean isReminder = isReminder(key);
    final boolean haveToSendReminder = isReminder && alert.isTriggered();
    final boolean haveToNotify = haveToSendReminder || !alert.isTriggered();
    alert.setTriggered(true);

    final AlertJobResult alertJobResult = new AlertJobResult(alert);
    if (haveToNotify) {
      alertWriter.writeAlertTriggeredStatus(true, alert.getId());
      fanoutNotification(
          createAlertNotification(
              alert, isReminder ? REMINDER : NEW, reportDefinition, currentValue));
      alertJobResult.setStatusChanged(true);
    }

    return alertJobResult;
  }

  private AlertJobResult handleAlertResolved(
      final AlertDefinitionDto alert,
      final ReportDefinitionDto<?> reportDefinition,
      final Double reportResult) {
    final AlertJobResult alertJobResult;
    alert.setTriggered(false);
    alertJobResult = new AlertJobResult(alert);
    alertJobResult.setStatusChanged(true);

    alertWriter.writeAlertTriggeredStatus(false, alert.getId());

    if (alert.isFixNotification()) {
      fanoutNotification(createResolvedAlertNotification(alert, reportDefinition, reportResult));
    }
    return alertJobResult;
  }

  private AlertNotificationDto createAlertNotification(
      final AlertDefinitionDto alert,
      final AlertNotificationType notificationType,
      final ReportDefinitionDto<?> reportDefinition,
      final Double currentValue) {
    return new AlertNotificationDto(
        alert,
        currentValue,
        notificationType,
        composeAlertText(alert, reportDefinition, notificationType, currentValue),
        createReportViewLink(alert, notificationType));
  }

  private AlertNotificationDto createResolvedAlertNotification(
      final AlertDefinitionDto alert,
      final ReportDefinitionDto<?> reportDefinition,
      final Double currentValue) {
    return new AlertNotificationDto(
        alert,
        currentValue,
        RESOLVED,
        composeFixText(alert, reportDefinition, currentValue),
        createReportViewLink(alert, RESOLVED));
  }

  private void fanoutNotification(final AlertNotificationDto notification) {
    for (final AlertNotificationService notificationService : notificationServices) {
      try {
        notificationService.notify(notification);
      } catch (final Exception e) {
        log.error(
            "Exception thrown while trying to send notification: {}",
            notificationService.getNotificationDescription(),
            e);
      }
    }
  }

  private boolean isReminder(final JobKey key) {
    return key.getName().toLowerCase(Locale.ENGLISH).contains("reminder");
  }

  private String composeAlertText(
      final AlertDefinitionDto alert,
      final ReportDefinitionDto<?> reportDefinition,
      final AlertNotificationType notificationType,
      final Double result) {
    final String statusText =
        AlertThresholdOperator.LESS.equals(alert.getThresholdOperator())
            ? "is not reached"
            : "was exceeded";
    return composeAlertText(alert, reportDefinition, result, notificationType, statusText);
  }

  private String composeFixText(
      final AlertDefinitionDto alert,
      final ReportDefinitionDto<?> reportDefinition,
      final Double result) {
    final String statusText =
        AlertThresholdOperator.LESS.equals(alert.getThresholdOperator())
            ? "has been reached"
            : "is not exceeded anymore";
    return composeAlertText(alert, reportDefinition, result, RESOLVED, statusText);
  }

  private String composeAlertText(
      final AlertDefinitionDto alert,
      final ReportDefinitionDto<?> reportDefinition,
      final Double result,
      final AlertNotificationType notificationType,
      final String statusText) {
    return configurationService.getNotificationEmailCompanyBranding()
        + " Optimize - Report Status\n"
        + "Alert name: "
        + alert.getName()
        + "\n"
        + "Report name: "
        + reportDefinition.getName()
        + "\n"
        + "Status: Given threshold ["
        + formatValueToHumanReadableString(alert.getThreshold(), reportDefinition)
        + "] "
        + statusText
        + ". Current value: "
        + formatValueToHumanReadableString(result, reportDefinition)
        + ". Please check your Optimize report for more information! \n"
        + createReportViewLink(alert, notificationType);
  }

  private boolean thresholdExceeded(final AlertDefinitionDto alert, final Double result) {
    boolean exceeded = false;
    if (result != null) {
      if (AlertThresholdOperator.GREATER.equals(alert.getThresholdOperator())) {
        exceeded = result > alert.getThreshold();
      } else if (AlertThresholdOperator.LESS.equals(alert.getThresholdOperator())) {
        exceeded = result < alert.getThreshold();
      }
    }
    return exceeded;
  }

  private String formatValueToHumanReadableString(
      final Double value, final ReportDefinitionDto<?> reportDefinition) {
    return isDurationReport(reportDefinition)
        ? durationInMsToReadableFormat(value)
        : String.valueOf(value);
  }

  private boolean isDurationReport(final ReportDefinitionDto<?> reportDefinition) {
    if (reportDefinition.getData() instanceof ProcessReportDataDto) {
      final ProcessReportDataDto data = (ProcessReportDataDto) reportDefinition.getData();
      return data.getView().getFirstProperty().equals(ViewProperty.DURATION);
    }
    return false;
  }

  private String durationInMsToReadableFormat(final Double durationInMsAsDouble) {
    if (durationInMsAsDouble == null) {
      return String.valueOf(durationInMsAsDouble);
    }
    final long durationInMs = durationInMsAsDouble.longValue();
    return DurationFormatterUtil.formatMilliSecondsToReadableDurationString(durationInMs);
  }

  private String createReportViewLink(
      final AlertDefinitionDto alert, final AlertNotificationType notificationType) {
    final Optional<String> containerAccessUrl = configurationService.getContainerAccessUrl();

    if (containerAccessUrl.isPresent()) {
      return containerAccessUrl.get() + createReportViewLinkPath(alert, notificationType);
    } else {
      final Optional<Integer> containerHttpPort = configurationService.getContainerHttpPort();
      final String httpPrefix = containerHttpPort.map(p -> HTTP_PREFIX).orElse(HTTPS_PREFIX);
      final Integer port = containerHttpPort.orElse(configurationService.getContainerHttpsPort());
      return httpPrefix
          + configurationService.getContainerHost()
          + ":"
          + port
          + configurationService.getContextPath().orElse("")
          + createReportViewLinkPath(alert, notificationType);
    }
  }

  private String createReportViewLinkPath(
      final AlertDefinitionDto alert, final AlertNotificationType notificationType) {
    final ReportDefinitionDto<?> reportDefinition =
        reportReader
            .getReport(alert.getReportId())
            .orElseThrow(
                () ->
                    new OptimizeRuntimeException(
                        "Was not able to retrieve report with id ["
                            + alert.getReportId()
                            + "] from Elasticsearch. Report does not exist."));

    final String collectionId = reportDefinition.getCollectionId();
    if (collectionId != null) {
      return String.format(
          "/#/collection/%s/report/%s?utm_source=%s",
          collectionId, alert.getReportId(), notificationType.getUtmSource());
    } else {
      return String.format(
          "/#/report/%s?utm_source=%s", alert.getReportId(), notificationType.getUtmSource());
    }
  }
}

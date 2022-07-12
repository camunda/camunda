/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.JettyConfig;
import org.camunda.optimize.dto.optimize.alert.AlertNotificationDto;
import org.camunda.optimize.dto.optimize.alert.AlertNotificationType;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertThresholdOperator;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.PlainReportEvaluationHandler;
import org.camunda.optimize.service.es.report.ReportEvaluationInfo;
import org.camunda.optimize.service.es.writer.AlertWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.util.SuppressionConstants;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.camunda.optimize.dto.optimize.alert.AlertNotificationType.NEW;
import static org.camunda.optimize.dto.optimize.alert.AlertNotificationType.REMINDER;
import static org.camunda.optimize.dto.optimize.alert.AlertNotificationType.RESOLVED;

@AllArgsConstructor
@Slf4j
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AlertJob implements Job {
  private static final String HTTP_PREFIX = "http://";
  private static final String HTTPS_PREFIX = "https://";

  private final ConfigurationService configurationService;
  private final List<AlertNotificationService> notificationServices;
  private final AlertReader alertReader;
  private final ReportReader reportReader;
  private final AlertWriter alertWriter;
  private final PlainReportEvaluationHandler reportEvaluator;
  private final JettyConfig jettyConfig;

  @Override
  public void execute(final JobExecutionContext jobExecutionContext) {
    final JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
    final String alertId = dataMap.getString("alertId");
    log.debug("Executing status check for alert [{}]", alertId);

    final AlertDefinitionDto alert = alertReader.getAlert(alertId)
      .orElseThrow(() -> new OptimizeRuntimeException("Alert does not exist!"));
    try {
      final ReportDefinitionDto<?> reportDefinition = reportReader.getReport(alert.getReportId())
        .orElseThrow(() -> new OptimizeRuntimeException(
          "Was not able to retrieve report with id " + alert.getReportId()
            + "] from Elasticsearch. Report does not exist."
        ));
      final ReportEvaluationInfo reportEvaluationInfo = ReportEvaluationInfo.builder(reportDefinition).build();
      @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST) final SingleReportEvaluationResult<Double> evaluationResult = (SingleReportEvaluationResult<Double>)
        reportEvaluator.evaluateReport(reportEvaluationInfo).getEvaluationResult();
      final Double reportResult = evaluationResult.getFirstCommandResult().getFirstMeasureData();

      if (thresholdExceeded(alert, reportResult)) {
        jobExecutionContext.setResult(handleAlertTriggered(
          jobExecutionContext.getJobDetail().getKey(), alert, reportDefinition, reportResult
        ));
      } else if (alert.isTriggered()) {
        // the alert was triggered before but the threshold is not exceeded anymore, thus the alert got resolved
        jobExecutionContext.setResult(handleAlertResolved(alert, reportDefinition, reportResult));
      }
    } catch (Exception e) {
      log.error("Error while processing alert [{}] for report [{}]", alertId, alert.getReportId(), e);
    }
  }

  private AlertJobResult handleAlertTriggered(final JobKey key,
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
      fanoutNotification(createAlertNotification(alert, isReminder ? REMINDER : NEW, reportDefinition, currentValue));
      alertJobResult.setStatusChanged(true);
    }

    return alertJobResult;
  }

  private AlertJobResult handleAlertResolved(final AlertDefinitionDto alert,
                                             final ReportDefinitionDto<?> reportDefinition,
                                             final Double reportResult) {
    AlertJobResult alertJobResult;
    alert.setTriggered(false);
    alertJobResult = new AlertJobResult(alert);
    alertJobResult.setStatusChanged(true);

    alertWriter.writeAlertTriggeredStatus(false, alert.getId());

    if (alert.isFixNotification()) {
      fanoutNotification(createResolvedAlertNotification(alert, reportDefinition, reportResult));
    }
    return alertJobResult;
  }

  private AlertNotificationDto createAlertNotification(final AlertDefinitionDto alert,
                                                       final AlertNotificationType notificationType,
                                                       final ReportDefinitionDto<?> reportDefinition,
                                                       final Double currentValue) {
    return new AlertNotificationDto(
      alert,
      currentValue,
      notificationType,
      composeAlertText(alert, reportDefinition, notificationType, currentValue),
      createReportViewLink(alert, notificationType)
    );
  }

  private AlertNotificationDto createResolvedAlertNotification(final AlertDefinitionDto alert,
                                                               final ReportDefinitionDto<?> reportDefinition,
                                                               final Double currentValue) {
    return new AlertNotificationDto(
      alert,
      currentValue,
      RESOLVED,
      composeFixText(alert, reportDefinition, currentValue),
      createReportViewLink(alert, RESOLVED)
    );
  }

  private void fanoutNotification(final AlertNotificationDto notification) {
    for (AlertNotificationService notificationService : notificationServices) {
      try {
        notificationService.notify(notification);
      } catch (Exception e) {
        log.error("Exception thrown while trying to send notification", e);
      }
    }
  }

  private boolean isReminder(JobKey key) {
    return key.getName().toLowerCase().contains("reminder");
  }

  private String composeAlertText(final AlertDefinitionDto alert,
                                  final ReportDefinitionDto<?> reportDefinition,
                                  final AlertNotificationType notificationType,
                                  final Double result) {
    final String statusText = AlertThresholdOperator.LESS.equals(alert.getThresholdOperator())
      ? "is not reached" : "was exceeded";
    return composeAlertText(alert, reportDefinition, result, notificationType, statusText);
  }

  private String composeFixText(final AlertDefinitionDto alert,
                                final ReportDefinitionDto<?> reportDefinition,
                                final Double result) {
    String statusText = AlertThresholdOperator.LESS.equals(alert.getThresholdOperator())
      ? "has been reached" : "is not exceeded anymore";
    return composeAlertText(alert, reportDefinition, result, RESOLVED, statusText);
  }

  private String composeAlertText(final AlertDefinitionDto alert,
                                  final ReportDefinitionDto<?> reportDefinition,
                                  final Double result,
                                  final AlertNotificationType notificationType,
                                  final String statusText) {
    return configurationService.getNotificationEmailCompanyBranding() + " Optimize - Report Status\n" +
      "Alert name: " + alert.getName() + "\n" +
      "Report name: " + reportDefinition.getName() + "\n" +
      "Status: Given threshold [" +
      formatValueToHumanReadableString(alert.getThreshold(), reportDefinition) +
      "] " + statusText +
      ". Current value: " +
      formatValueToHumanReadableString(result, reportDefinition) +
      ". Please check your Optimize report for more information! \n" +
      createReportViewLink(alert, notificationType);
  }

  private boolean thresholdExceeded(AlertDefinitionDto alert, Double result) {
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

  private String formatValueToHumanReadableString(final Double value, final ReportDefinitionDto<?> reportDefinition) {
    return isDurationReport(reportDefinition) ? durationInMsToReadableFormat(value) : String.valueOf(value);
  }

  private boolean isDurationReport(final ReportDefinitionDto<?> reportDefinition) {
    if (reportDefinition.getData() instanceof ProcessReportDataDto) {
      ProcessReportDataDto data = (ProcessReportDataDto) reportDefinition.getData();
      return data.getView().getFirstProperty().equals(ViewProperty.DURATION);
    }
    return false;
  }

  private String durationInMsToReadableFormat(final Double durationInMsAsDouble) {
    if (durationInMsAsDouble == null) {
      return String.valueOf(durationInMsAsDouble);
    }
    final long durationInMs = durationInMsAsDouble.longValue();
    return formatMilliSecondsToReadableDurationString(durationInMs);
  }

  private String formatMilliSecondsToReadableDurationString(final long durationInMs) {
    final long days = TimeUnit.MILLISECONDS.toDays(durationInMs);
    final long hours = TimeUnit.MILLISECONDS.toHours(durationInMs) - TimeUnit.DAYS.toHours(days);
    final long minutes = TimeUnit.MILLISECONDS.toMinutes(durationInMs)
      - TimeUnit.DAYS.toMinutes(days)
      - TimeUnit.HOURS.toMinutes(hours);
    final long seconds = TimeUnit.MILLISECONDS.toSeconds(durationInMs)
      - TimeUnit.DAYS.toSeconds(days)
      - TimeUnit.HOURS.toSeconds(hours)
      - TimeUnit.MINUTES.toSeconds(minutes);
    final long milliSeconds = durationInMs - TimeUnit.DAYS.toMillis(days)
      - TimeUnit.HOURS.toMillis(hours)
      - TimeUnit.MINUTES.toMillis(minutes)
      - TimeUnit.SECONDS.toMillis(seconds);

    return String.format("%sd %sh %smin %ss %sms", days, hours, minutes, seconds, milliSeconds);
  }

  private String createReportViewLink(final AlertDefinitionDto alert,
                                      final AlertNotificationType notificationType) {
    final Optional<String> containerAccessUrl = configurationService.getContainerAccessUrl();

    if (containerAccessUrl.isPresent()) {
      return containerAccessUrl.get() + createReportViewLinkPath(alert, notificationType);
    } else {
      Optional<Integer> containerHttpPort = configurationService.getContainerHttpPort();
      String httpPrefix = containerHttpPort.map(p -> HTTP_PREFIX).orElse(HTTPS_PREFIX);
      Integer port = containerHttpPort.orElse(configurationService.getContainerHttpsPort());
      return httpPrefix + configurationService.getContainerHost() + ":" + port + createReportViewLinkPath(
        alert,
        notificationType
      );
    }
  }

  private String createReportViewLinkPath(final AlertDefinitionDto alert,
                                          final AlertNotificationType notificationType) {
    final ReportDefinitionDto<?> reportDefinition = reportReader.getReport(alert.getReportId())
      .orElseThrow(() -> new OptimizeRuntimeException(
        "Was not able to retrieve report with id [" + alert.getReportId()
          + "] from Elasticsearch. Report does not exist."
      ));

    final String collectionId = reportDefinition.getCollectionId();
    if (collectionId != null) {
      return String.format(
        "/#/collection/%s/report/%s?utm_source=%s",
        collectionId,
        alert.getReportId(),
        notificationType.getUtmSource()
      );
    } else {
      return String.format("/#/report/%s?utm_source=%s", alert.getReportId(), notificationType.getUtmSource());
    }
  }
}

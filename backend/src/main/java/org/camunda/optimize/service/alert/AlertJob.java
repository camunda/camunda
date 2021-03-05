/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@NoArgsConstructor
@Slf4j
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AlertJob implements Job {
  private static final String HTTP_PREFIX = "http://";
  private static final String HTTPS_PREFIX = "https://";

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private List<NotificationService> notificationServices;
  @Autowired
  private AlertReader alertReader;
  @Autowired
  private ReportReader reportReader;
  @Autowired
  private AlertWriter alertWriter;
  @Autowired
  private PlainReportEvaluationHandler reportEvaluator;

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
    String alertId = dataMap.getString("alertId");
    log.debug("Executing status check for alert [{}]", alertId);

    AlertDefinitionDto alert = alertReader.getAlert(alertId)
      .orElseThrow(() -> new OptimizeRuntimeException("Alert does not exist!"));

    try {
      ReportDefinitionDto reportDefinition = reportReader.getReport(alert.getReportId())
        .orElseThrow(() -> new OptimizeRuntimeException("Was not able to retrieve report with id "
                                                          + "[" + alert.getReportId() + "]"
                                                          + "from Elasticsearch. Report does not exist."));
      final ReportEvaluationInfo reportEvaluationInfo = ReportEvaluationInfo.builder(reportDefinition).build();
      final SingleReportEvaluationResult<Double> evaluationResult =
        (SingleReportEvaluationResult<Double>) reportEvaluator
          .evaluateReport(reportEvaluationInfo)
          .getEvaluationResult();
      Double reportResult = evaluationResult.getFirstCommandResult().getFirstMeasureData();

      AlertJobResult alertJobResult = null;
      if (thresholdExceeded(alert, reportResult)) {
        alertJobResult = notifyIfNeeded(
          jobExecutionContext.getJobDetail().getKey(),
          alertId,
          alert,
          reportDefinition,
          reportResult
        );
      } else if (alert.isTriggered()) {
        alert.setTriggered(false);

        alertJobResult = new AlertJobResult(alert);
        alertJobResult.setStatusChanged(true);

        alertWriter.writeAlertStatus(false, alertId);

        if (alert.isFixNotification()) {
          notifyAvailableTargets(
            composeFixText(alert, reportDefinition, reportResult),
            alert
          );
        }
      }

      jobExecutionContext.setResult(alertJobResult);
    } catch (Exception e) {
      log.error("Error while processing alert [{}] for report [{}]", alertId, alert.getReportId(), e);
    }
  }

  private AlertJobResult notifyIfNeeded(
    JobKey key, String alertId,
    AlertDefinitionDto alert,
    ReportDefinitionDto reportDefinition,
    Double result
  ) {
    boolean haveToSendReminder = isReminder(key) && alert.isTriggered();
    boolean haveToNotify = haveToSendReminder || !alert.isTriggered();
    alert.setTriggered(true);

    AlertJobResult alertJobResult = new AlertJobResult(alert);

    if (haveToNotify) {
      alertWriter.writeAlertStatus(true, alertId);

      notifyAvailableTargets(
        composeAlertText(alert, reportDefinition, result),
        alert
      );

      alertJobResult.setStatusChanged(true);
    }

    return alertJobResult;
  }

  private void notifyAvailableTargets(final String alertContent, final AlertDefinitionDto alert) {
    for (NotificationService notificationService : notificationServices) {
      try {
        List<String> recipients = new ArrayList<>();
        if (notificationService instanceof EmailNotificationService) {
          recipients = alert.getEmails();
        } else if (notificationService instanceof WebhookNotificationService) {
          recipients = Collections.singletonList(alert.getWebhook());
        }
        notificationService.notifyRecipients(alertContent, recipients);
      } catch (Exception e) {
        log.error("Exception thrown while trying to send notification", e);
      }
    }
  }

  private boolean isReminder(JobKey key) {
    return key.getName().toLowerCase().contains("reminder");
  }

  private String composeAlertText(
    AlertDefinitionDto alert,
    ReportDefinitionDto reportDefinition,
    Double result
  ) {
    String statusText = AlertThresholdOperator.LESS.equals(alert.getThresholdOperator())
      ? "is not reached" : "was exceeded";
    return composeAlertText(alert, reportDefinition, result, statusText);
  }

  private String composeFixText(
    AlertDefinitionDto alert,
    ReportDefinitionDto reportDefinition,
    Double result) {
    String statusText = AlertThresholdOperator.LESS.equals(alert.getThresholdOperator())
      ? "has been reached" : "is not exceeded anymore";
    return composeAlertText(alert, reportDefinition, result, statusText);
  }

  private String composeAlertText(final AlertDefinitionDto alert,
                                  final ReportDefinitionDto reportDefinition,
                                  final Double result,
                                  final String statusText) {
    return "Camunda Optimize - Report Status\n" +
      "Alert name: " + alert.getName() + "\n" +
      "Report name: " + reportDefinition.getName() + "\n" +
      "Status: Given threshold [" +
      formatValueToHumanReadableString(alert.getThreshold(), reportDefinition) +
      "] " + statusText +
      ". Current value: " +
      formatValueToHumanReadableString(result, reportDefinition) +
      ". Please check your Optimize report for more information! \n" +
      createViewLink(alert);
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

  private String formatValueToHumanReadableString(final Double value, final ReportDefinitionDto reportDefinition) {
    return isDurationReport(reportDefinition)
      ? durationInMsToReadableFormat(value)
      : String.valueOf(value);
  }

  private boolean isDurationReport(ReportDefinitionDto reportDefinition) {
    if (reportDefinition.getData() instanceof ProcessReportDataDto) {
      ProcessReportDataDto data = (ProcessReportDataDto) reportDefinition.getData();
      return data.getView().getProperty().equals(ViewProperty.DURATION);
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

  private String createViewLink(AlertDefinitionDto alert) {
    final Optional<String> containerAccessUrl = configurationService.getContainerAccessUrl();

    if (containerAccessUrl.isPresent()) {
      return containerAccessUrl.get() + createViewLinkFragment(alert);
    } else {
      Optional<Integer> containerHttpPort = configurationService.getContainerHttpPort();
      String httpPrefix = containerHttpPort.map(p -> HTTP_PREFIX).orElse(HTTPS_PREFIX);
      Integer port = containerHttpPort.orElse(configurationService.getContainerHttpsPort());
      return httpPrefix + configurationService.getContainerHost() + ":" + port + createViewLinkFragment(alert);
    }
  }

  private String createViewLinkFragment(final AlertDefinitionDto alert) {
    ReportDefinitionDto reportDefinition = reportReader.getReport(alert.getReportId())
      .orElseThrow(() -> new OptimizeRuntimeException("Was not able to retrieve report with id "
                                                        + "[" + alert.getReportId() + "]"
                                                        + "from Elasticsearch. Report does not exist."));

    String collectionId = reportDefinition.getCollectionId();
    if (collectionId != null) {
      return String.format(
        "/#/collection/%s/report/%s/",
        collectionId,
        alert.getReportId()
      );
    } else {
      return String.format(
        "/#/report/%s/",
        alert.getReportId()
      );
    }
  }
}

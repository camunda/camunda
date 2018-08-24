package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberSingleReportResultDto;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.PlainReportEvaluationHandler;
import org.camunda.optimize.service.es.writer.AlertWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AlertJob implements Job {
  private static final String HTTP_PREFIX = "http://";
  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private NotificationService notificationService;

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
    logger.debug("executing status check for alert [{}]", alertId);

    AlertDefinitionDto alert = alertReader.findAlert(alertId);

    try {
      ReportDefinitionDto reportDefinition = reportReader.getReport(alert.getReportId());
      NumberSingleReportResultDto result =
        (NumberSingleReportResultDto) reportEvaluator.evaluateReport(reportDefinition);

      AlertJobResult alertJobResult = null;
      if (thresholdExceeded(alert, result)) {
        alertJobResult = notifyIfNeeded(
            jobExecutionContext.getJobDetail().getKey(),
            alertId,
            alert,
            reportDefinition,
            result
        );
      } else if (alert.isTriggered()) {
        alert.setTriggered(false);

        alertJobResult = new AlertJobResult(alert);
        alertJobResult.setStatusChanged(true);

        alertWriter.writeAlertStatus(false, alertId);

        if (alert.isFixNotification()) {
          notificationService.notifyRecipient(
            composeFixText(alert, reportDefinition, result),
            alert.getEmail()
          );
        }
      }

      jobExecutionContext.setResult(alertJobResult);
    } catch (Exception e) {
      logger.error("error while processing alert [{}] for report [{}]", alertId, alert.getReportId(), e);
    }

  }

  private String composeFixText(AlertDefinitionDto alert, ReportDefinitionDto reportDefinition, NumberSingleReportResultDto result) {
    String statusText = alert.getThresholdOperator().equals(AlertDefinitionDto.LESS)
            ? "has been reached" : "is not exceeded anymore";
    String emailBody = "Camunda Optimize - Report Status\n" +
        "Alert name: " + alert.getName() + "\n" +
        "Report name: " + reportDefinition.getName() + "\n" +
        "Status: Given threshold [" +
        alert.getThreshold() +
        "] " + statusText +
        ". Current value: " +
        result.getResult() +
        ". Please check your Optimize report for more information! \n" +
        createViewLink(alert);
    return emailBody;
  }

  private String createViewLink(AlertDefinitionDto alert) {
    return HTTP_PREFIX + configurationService.getContainerHost() + ":" +
        configurationService.getContainerHttpPort() + "/report/" + alert.getReportId();
  }

  private AlertJobResult notifyIfNeeded(
      JobKey key, String alertId,
      AlertDefinitionDto alert,
      ReportDefinitionDto reportDefinition,
      NumberSingleReportResultDto result
  ) {
    boolean triggeredReminder = isReminder(key) && alert.isTriggered();
    boolean haveToNotify = triggeredReminder || !alert.isTriggered();
    if (haveToNotify) {
      alert.setTriggered(true);
    }

    AlertJobResult alertJobResult = new AlertJobResult(alert);

    if (haveToNotify) {
      logger.debug("Sending reminder notification!");
      alertWriter.writeAlertStatus(true, alertId);

      notificationService.notifyRecipient(
          composeAlertText(alert, reportDefinition, result),
          alert.getEmail()
      );

      alertJobResult.setStatusChanged(true);
    }

    return alertJobResult;
  }

  private boolean isReminder(JobKey key) {
    return key.getName().toLowerCase().contains("reminder");
  }

  private String composeAlertText(
      AlertDefinitionDto alert,
      ReportDefinitionDto reportDefinition,
      NumberSingleReportResultDto result
  ) {
    String statusText = alert.getThresholdOperator().equals(AlertDefinitionDto.LESS)
            ? "is not reached" : "was exceeded";
    String emailBody = "Camunda Optimize - Report Status\n" +
        "Alert name: " + alert.getName() + "\n" +
        "Report name: " + reportDefinition.getName() + "\n" +
        "Status: Given threshold [" +
        alert.getThreshold() +
        "] " + statusText +
        ". Current value: " +
        result.getResult() +
        ". Please check your Optimize report for more information!\n" +
        createViewLink(alert);
    return emailBody;
  }

  private boolean thresholdExceeded(AlertDefinitionDto alert, NumberSingleReportResultDto result) {
    boolean exceeded = false;
    if (AlertDefinitionDto.GREATER.equals(alert.getThresholdOperator())) {
      exceeded = result.getResult() > alert.getThreshold();
    } else if (AlertDefinitionDto.LESS.equals(alert.getThresholdOperator())) {
      exceeded = result.getResult() < alert.getThreshold();
    }
    return exceeded;
  }
}

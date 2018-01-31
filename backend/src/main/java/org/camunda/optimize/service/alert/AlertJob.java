package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.result.NumberReportResultDto;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.ReportEvaluator;
import org.camunda.optimize.service.es.writer.AlertWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author Askar Akhmerov
 */
@Component
public class AlertJob implements Job {
  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private NotificationService notificationService;

  @Autowired
  private AlertReader alertReader;

  @Autowired
  private ReportReader reportReader;

  @Autowired
  private AlertWriter alertWriter;

  @Autowired
  private ReportEvaluator reportEvaluator;

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
    String alertId = dataMap.getString("alertId");
    logger.debug("executing status check for alert [{}]", alertId);

    AlertDefinitionDto alert = alertReader.findAlert(alertId);

    ReportDefinitionDto reportDefinition;
    try {
      reportDefinition = reportReader.getReport(alert.getReportId());
      NumberReportResultDto result = (NumberReportResultDto) reportEvaluator.evaluate(reportDefinition);

      AlertJobResult alertJobResult = null;
      if (thresholdExceeded(alert, result)) {
        alertJobResult = notifyIfNeeded(alertId, alert, reportDefinition, result);

      } else if (alert.isTriggered()) {
        alertJobResult = new AlertJobResult(alert);
        alertWriter.writeAlertStatus(false, alertId);
        alertJobResult.setStatusChanged(true);
      }

      jobExecutionContext.setResult(alertJobResult);
    } catch (IOException e) {
      logger.error("error while processing alert of report [{}]", alertId, e);
    } catch (OptimizeException e) {
      logger.error("error while processing alert ofr report [{}]", alertId, e);
    }

  }

  private AlertJobResult notifyIfNeeded(
      String alertId,
      AlertDefinitionDto alert,
      ReportDefinitionDto reportDefinition,
      NumberReportResultDto result
  ) {
    AlertJobResult alertJobResult = new AlertJobResult(alert);
    boolean haveToNotify = false;
    if (!alert.isTriggered()) {
      haveToNotify = true;
    }

    if (haveToNotify) {
      alertWriter.writeAlertStatus(haveToNotify, alertId);

      notificationService.notifyRecipient(
          composeAlertText(alert, reportDefinition, result),
          alert.getEmail()
      );

      alertJobResult.setStatusChanged(true);
      alertJobResult.setTriggered(true);
    }

    return alertJobResult;
  }

  private String composeAlertText(
      AlertDefinitionDto alert,
      ReportDefinitionDto reportDefinition,
      NumberReportResultDto result
  ) {
    String emailBody = "Camunda Optimize - Report Status\n" +
        "Report name: " + reportDefinition.getName() + "\n" +
        "Status: Given threshold [" +
        alert.getThreshold() +
        "] was exceeded." +
        "Current value: " +
        result.getResult() +
        ". Please check your Optimize dashboard for more information!";
    return emailBody;
  }

  private boolean thresholdExceeded(AlertDefinitionDto alert, NumberReportResultDto result) {
    boolean exceeded = false;
    if (AlertDefinitionDto.GRATER.equals(alert.getThresholdOperator())) {
      exceeded = result.getResult() > alert.getThreshold();
    } else if (AlertDefinitionDto.LESS.equals(alert.getThresholdOperator())) {
      exceeded = result.getResult() < alert.getThreshold();
    }
    return exceeded;
  }
}

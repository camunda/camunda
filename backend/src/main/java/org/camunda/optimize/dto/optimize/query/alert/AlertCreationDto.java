package org.camunda.optimize.dto.optimize.query.alert;

/**
 * @author Askar Akhmerov
 */
public class AlertCreationDto {
  protected String name;
  protected AlertInterval checkInterval;
  protected String reportId;
  protected int threshold;
  protected String thresholdOperator;
  protected boolean fixNotification;
  protected AlertInterval reminder;
  protected String email;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public AlertInterval getCheckInterval() {
    return checkInterval;
  }

  public void setCheckInterval(AlertInterval checkInterval) {
    this.checkInterval = checkInterval;
  }

  public String getReportId() {
    return reportId;
  }

  public void setReportId(String reportId) {
    this.reportId = reportId;
  }

  public int getThreshold() {
    return threshold;
  }

  public void setThreshold(int threshold) {
    this.threshold = threshold;
  }

  public String getThresholdOperator() {
    return thresholdOperator;
  }

  public void setThresholdOperator(String thresholdOperator) {
    this.thresholdOperator = thresholdOperator;
  }

  public boolean isFixNotification() {
    return fixNotification;
  }

  public void setFixNotification(boolean fixNotification) {
    this.fixNotification = fixNotification;
  }

  public AlertInterval getReminder() {
    return reminder;
  }

  public void setReminder(AlertInterval reminder) {
    this.reminder = reminder;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}

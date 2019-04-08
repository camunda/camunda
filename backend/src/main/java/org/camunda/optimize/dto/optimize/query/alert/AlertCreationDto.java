/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.alert;


public class AlertCreationDto {
  protected String name;
  protected AlertInterval checkInterval;
  protected String reportId;
  protected long threshold;
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

  public long getThreshold() {
    return threshold;
  }

  public void setThreshold(long threshold) {
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

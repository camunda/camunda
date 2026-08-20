/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AlertCreationRequestDto {

  private String name;
  private AlertInterval checkInterval;
  private String reportId;
  private Double threshold;
  private AlertThresholdOperator thresholdOperator;
  private boolean fixNotification;
  private AlertInterval reminder;
  private List<String> emails = new ArrayList<>();

  public AlertCreationRequestDto() {}

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public AlertInterval getCheckInterval() {
    return checkInterval;
  }

  public void setCheckInterval(final AlertInterval checkInterval) {
    this.checkInterval = checkInterval;
  }

  public String getReportId() {
    return reportId;
  }

  public void setReportId(final String reportId) {
    this.reportId = reportId;
  }

  public Double getThreshold() {
    return threshold;
  }

  public void setThreshold(final Double threshold) {
    this.threshold = threshold;
  }

  public AlertThresholdOperator getThresholdOperator() {
    return thresholdOperator;
  }

  public void setThresholdOperator(final AlertThresholdOperator thresholdOperator) {
    this.thresholdOperator = thresholdOperator;
  }

  public boolean isFixNotification() {
    return fixNotification;
  }

  public void setFixNotification(final boolean fixNotification) {
    this.fixNotification = fixNotification;
  }

  public AlertInterval getReminder() {
    return reminder;
  }

  public void setReminder(final AlertInterval reminder) {
    this.reminder = reminder;
  }

  public List<String> getEmails() {
    return emails;
  }

  public void setEmails(final List<String> emails) {
    this.emails = emails;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AlertCreationRequestDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AlertCreationRequestDto that = (AlertCreationRequestDto) o;
    return fixNotification == that.fixNotification
        && Objects.equals(name, that.name)
        && Objects.equals(checkInterval, that.checkInterval)
        && Objects.equals(reportId, that.reportId)
        && Objects.equals(threshold, that.threshold)
        && thresholdOperator == that.thresholdOperator
        && Objects.equals(reminder, that.reminder)
        && Objects.equals(emails, that.emails);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name,
        checkInterval,
        reportId,
        threshold,
        thresholdOperator,
        fixNotification,
        reminder,
        emails);
  }

  @Override
  public String toString() {
    return "AlertCreationRequestDto(name="
        + getName()
        + ", checkInterval="
        + getCheckInterval()
        + ", reportId="
        + getReportId()
        + ", threshold="
        + getThreshold()
        + ", thresholdOperator="
        + getThresholdOperator()
        + ", fixNotification="
        + isFixNotification()
        + ", reminder="
        + getReminder()
        + ", emails="
        + getEmails()
        + ")";
  }

  // needed to allow inheritance of field name constants
  @SuppressWarnings("checkstyle:ConstantName")
  public static class Fields {

    public static final String name = "name";
    public static final String checkInterval = "checkInterval";
    public static final String reportId = "reportId";
    public static final String threshold = "threshold";
    public static final String thresholdOperator = "thresholdOperator";
    public static final String fixNotification = "fixNotification";
    public static final String reminder = "reminder";
    public static final String emails = "emails";

    protected Fields() {}
  }
}

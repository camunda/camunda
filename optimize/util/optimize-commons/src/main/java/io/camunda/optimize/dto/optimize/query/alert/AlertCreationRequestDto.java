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

public class AlertCreationRequestDto {

  private String name;
  private AlertInterval checkInterval;
  private String reportId;
  private Double threshold;
  private AlertThresholdOperator thresholdOperator;
  private boolean fixNotification;
  private AlertInterval reminder;
  private List<String> emails = new ArrayList<>();
  private String webhook;

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

  public String getWebhook() {
    return webhook;
  }

  public void setWebhook(final String webhook) {
    this.webhook = webhook;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AlertCreationRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $checkInterval = getCheckInterval();
    result = result * PRIME + ($checkInterval == null ? 43 : $checkInterval.hashCode());
    final Object $reportId = getReportId();
    result = result * PRIME + ($reportId == null ? 43 : $reportId.hashCode());
    final Object $threshold = getThreshold();
    result = result * PRIME + ($threshold == null ? 43 : $threshold.hashCode());
    final Object $thresholdOperator = getThresholdOperator();
    result = result * PRIME + ($thresholdOperator == null ? 43 : $thresholdOperator.hashCode());
    result = result * PRIME + (isFixNotification() ? 79 : 97);
    final Object $reminder = getReminder();
    result = result * PRIME + ($reminder == null ? 43 : $reminder.hashCode());
    final Object $emails = getEmails();
    result = result * PRIME + ($emails == null ? 43 : $emails.hashCode());
    final Object $webhook = getWebhook();
    result = result * PRIME + ($webhook == null ? 43 : $webhook.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AlertCreationRequestDto)) {
      return false;
    }
    final AlertCreationRequestDto other = (AlertCreationRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$checkInterval = getCheckInterval();
    final Object other$checkInterval = other.getCheckInterval();
    if (this$checkInterval == null
        ? other$checkInterval != null
        : !this$checkInterval.equals(other$checkInterval)) {
      return false;
    }
    final Object this$reportId = getReportId();
    final Object other$reportId = other.getReportId();
    if (this$reportId == null ? other$reportId != null : !this$reportId.equals(other$reportId)) {
      return false;
    }
    final Object this$threshold = getThreshold();
    final Object other$threshold = other.getThreshold();
    if (this$threshold == null
        ? other$threshold != null
        : !this$threshold.equals(other$threshold)) {
      return false;
    }
    final Object this$thresholdOperator = getThresholdOperator();
    final Object other$thresholdOperator = other.getThresholdOperator();
    if (this$thresholdOperator == null
        ? other$thresholdOperator != null
        : !this$thresholdOperator.equals(other$thresholdOperator)) {
      return false;
    }
    if (isFixNotification() != other.isFixNotification()) {
      return false;
    }
    final Object this$reminder = getReminder();
    final Object other$reminder = other.getReminder();
    if (this$reminder == null ? other$reminder != null : !this$reminder.equals(other$reminder)) {
      return false;
    }
    final Object this$emails = getEmails();
    final Object other$emails = other.getEmails();
    if (this$emails == null ? other$emails != null : !this$emails.equals(other$emails)) {
      return false;
    }
    final Object this$webhook = getWebhook();
    final Object other$webhook = other.getWebhook();
    if (this$webhook == null ? other$webhook != null : !this$webhook.equals(other$webhook)) {
      return false;
    }
    return true;
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
        + ", webhook="
        + getWebhook()
        + ")";
  }

  // needed to allow inheritance of field name constants
  public static class Fields {

    public static final String name = "name";
    public static final String checkInterval = "checkInterval";
    public static final String reportId = "reportId";
    public static final String threshold = "threshold";
    public static final String thresholdOperator = "thresholdOperator";
    public static final String fixNotification = "fixNotification";
    public static final String reminder = "reminder";
    public static final String emails = "emails";
    public static final String webhook = "webhook";

    protected Fields() {}
  }
}

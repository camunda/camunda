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
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

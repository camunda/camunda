/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.alert;

import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import java.util.Objects;

public class AlertNotificationDto {

  private final AlertDefinitionDto alert;
  private final Double currentValue;
  private final AlertNotificationType type;
  private final String alertMessage;
  private final String reportLink;

  public AlertNotificationDto(
      final AlertDefinitionDto alert,
      final Double currentValue,
      final AlertNotificationType type,
      final String alertMessage,
      final String reportLink) {
    this.alert = alert;
    this.currentValue = currentValue;
    this.type = type;
    this.alertMessage = alertMessage;
    this.reportLink = reportLink;
  }

  public AlertDefinitionDto getAlert() {
    return alert;
  }

  public Double getCurrentValue() {
    return currentValue;
  }

  public AlertNotificationType getType() {
    return type;
  }

  public String getAlertMessage() {
    return alertMessage;
  }

  public String getReportLink() {
    return reportLink;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AlertNotificationDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AlertNotificationDto that = (AlertNotificationDto) o;
    return Objects.equals(alert, that.alert)
        && Objects.equals(currentValue, that.currentValue)
        && type == that.type
        && Objects.equals(alertMessage, that.alertMessage)
        && Objects.equals(reportLink, that.reportLink);
  }

  @Override
  public int hashCode() {
    return Objects.hash(alert, currentValue, type, alertMessage, reportLink);
  }

  @Override
  public String toString() {
    return "AlertNotificationDto(alert="
        + getAlert()
        + ", currentValue="
        + getCurrentValue()
        + ", type="
        + getType()
        + ", alertMessage="
        + getAlertMessage()
        + ", reportLink="
        + getReportLink()
        + ")";
  }
}

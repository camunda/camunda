/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.alert;

import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;

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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $alert = getAlert();
    result = result * PRIME + ($alert == null ? 43 : $alert.hashCode());
    final Object $currentValue = getCurrentValue();
    result = result * PRIME + ($currentValue == null ? 43 : $currentValue.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $alertMessage = getAlertMessage();
    result = result * PRIME + ($alertMessage == null ? 43 : $alertMessage.hashCode());
    final Object $reportLink = getReportLink();
    result = result * PRIME + ($reportLink == null ? 43 : $reportLink.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AlertNotificationDto)) {
      return false;
    }
    final AlertNotificationDto other = (AlertNotificationDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$alert = getAlert();
    final Object other$alert = other.getAlert();
    if (this$alert == null ? other$alert != null : !this$alert.equals(other$alert)) {
      return false;
    }
    final Object this$currentValue = getCurrentValue();
    final Object other$currentValue = other.getCurrentValue();
    if (this$currentValue == null
        ? other$currentValue != null
        : !this$currentValue.equals(other$currentValue)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$alertMessage = getAlertMessage();
    final Object other$alertMessage = other.getAlertMessage();
    if (this$alertMessage == null
        ? other$alertMessage != null
        : !this$alertMessage.equals(other$alertMessage)) {
      return false;
    }
    final Object this$reportLink = getReportLink();
    final Object other$reportLink = other.getReportLink();
    if (this$reportLink == null
        ? other$reportLink != null
        : !this$reportLink.equals(other$reportLink)) {
      return false;
    }
    return true;
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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud.panelnotifications;

public class PanelNotificationRequestDto {

  private final PanelNotificationDataDto notification;

  private PanelNotificationRequestDto(final PanelNotificationDataDto notification) {
    this.notification = notification;
  }

  public PanelNotificationDataDto getNotification() {
    return notification;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PanelNotificationRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $notification = getNotification();
    result = result * PRIME + ($notification == null ? 43 : $notification.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PanelNotificationRequestDto)) {
      return false;
    }
    final PanelNotificationRequestDto other = (PanelNotificationRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$notification = getNotification();
    final Object other$notification = other.getNotification();
    if (this$notification == null
        ? other$notification != null
        : !this$notification.equals(other$notification)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "PanelNotificationRequestDto(notification=" + getNotification() + ")";
  }

  public static PanelNotificationRequestDtoBuilder builder() {
    return new PanelNotificationRequestDtoBuilder();
  }

  public static class PanelNotificationRequestDtoBuilder {

    private PanelNotificationDataDto notification;

    PanelNotificationRequestDtoBuilder() {}

    public PanelNotificationRequestDtoBuilder notification(
        final PanelNotificationDataDto notification) {
      this.notification = notification;
      return this;
    }

    public PanelNotificationRequestDto build() {
      return new PanelNotificationRequestDto(notification);
    }

    @Override
    public String toString() {
      return "PanelNotificationRequestDto.PanelNotificationRequestDtoBuilder(notification="
          + notification
          + ")";
    }
  }
}

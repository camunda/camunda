/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud.panelnotifications;

import java.util.Objects;

public final class PanelNotificationRequestDto {

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
    return Objects.hash(notification);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PanelNotificationRequestDto that = (PanelNotificationRequestDto) o;
    return Objects.equals(notification, that.notification);
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

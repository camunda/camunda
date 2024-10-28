/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud.panelnotifications;

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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import io.camunda.optimize.service.util.configuration.engine.EventIngestionConfiguration;
import java.util.List;

public class EventBasedProcessConfiguration {

  private List<String> authorizedUserIds;
  private List<String> authorizedGroupIds;
  private EventImportConfiguration eventImport;
  private EventIngestionConfiguration eventIngestion;
  private IndexRolloverConfiguration eventIndexRollover;

  public EventBasedProcessConfiguration(
      final List<String> authorizedUserIds,
      final List<String> authorizedGroupIds,
      final EventImportConfiguration eventImport,
      final EventIngestionConfiguration eventIngestion,
      final IndexRolloverConfiguration eventIndexRollover) {
    this.authorizedUserIds = authorizedUserIds;
    this.authorizedGroupIds = authorizedGroupIds;
    this.eventImport = eventImport;
    this.eventIngestion = eventIngestion;
    this.eventIndexRollover = eventIndexRollover;
  }

  protected EventBasedProcessConfiguration() {}

  public List<String> getAuthorizedUserIds() {
    return authorizedUserIds;
  }

  public void setAuthorizedUserIds(final List<String> authorizedUserIds) {
    this.authorizedUserIds = authorizedUserIds;
  }

  public List<String> getAuthorizedGroupIds() {
    return authorizedGroupIds;
  }

  public void setAuthorizedGroupIds(final List<String> authorizedGroupIds) {
    this.authorizedGroupIds = authorizedGroupIds;
  }

  public EventImportConfiguration getEventImport() {
    return eventImport;
  }

  public void setEventImport(final EventImportConfiguration eventImport) {
    this.eventImport = eventImport;
  }

  public EventIngestionConfiguration getEventIngestion() {
    return eventIngestion;
  }

  public void setEventIngestion(final EventIngestionConfiguration eventIngestion) {
    this.eventIngestion = eventIngestion;
  }

  public IndexRolloverConfiguration getEventIndexRollover() {
    return eventIndexRollover;
  }

  public void setEventIndexRollover(final IndexRolloverConfiguration eventIndexRollover) {
    this.eventIndexRollover = eventIndexRollover;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventBasedProcessConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $authorizedUserIds = getAuthorizedUserIds();
    result = result * PRIME + ($authorizedUserIds == null ? 43 : $authorizedUserIds.hashCode());
    final Object $authorizedGroupIds = getAuthorizedGroupIds();
    result = result * PRIME + ($authorizedGroupIds == null ? 43 : $authorizedGroupIds.hashCode());
    final Object $eventImport = getEventImport();
    result = result * PRIME + ($eventImport == null ? 43 : $eventImport.hashCode());
    final Object $eventIngestion = getEventIngestion();
    result = result * PRIME + ($eventIngestion == null ? 43 : $eventIngestion.hashCode());
    final Object $eventIndexRollover = getEventIndexRollover();
    result = result * PRIME + ($eventIndexRollover == null ? 43 : $eventIndexRollover.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventBasedProcessConfiguration)) {
      return false;
    }
    final EventBasedProcessConfiguration other = (EventBasedProcessConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$authorizedUserIds = getAuthorizedUserIds();
    final Object other$authorizedUserIds = other.getAuthorizedUserIds();
    if (this$authorizedUserIds == null
        ? other$authorizedUserIds != null
        : !this$authorizedUserIds.equals(other$authorizedUserIds)) {
      return false;
    }
    final Object this$authorizedGroupIds = getAuthorizedGroupIds();
    final Object other$authorizedGroupIds = other.getAuthorizedGroupIds();
    if (this$authorizedGroupIds == null
        ? other$authorizedGroupIds != null
        : !this$authorizedGroupIds.equals(other$authorizedGroupIds)) {
      return false;
    }
    final Object this$eventImport = getEventImport();
    final Object other$eventImport = other.getEventImport();
    if (this$eventImport == null
        ? other$eventImport != null
        : !this$eventImport.equals(other$eventImport)) {
      return false;
    }
    final Object this$eventIngestion = getEventIngestion();
    final Object other$eventIngestion = other.getEventIngestion();
    if (this$eventIngestion == null
        ? other$eventIngestion != null
        : !this$eventIngestion.equals(other$eventIngestion)) {
      return false;
    }
    final Object this$eventIndexRollover = getEventIndexRollover();
    final Object other$eventIndexRollover = other.getEventIndexRollover();
    if (this$eventIndexRollover == null
        ? other$eventIndexRollover != null
        : !this$eventIndexRollover.equals(other$eventIndexRollover)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventBasedProcessConfiguration(authorizedUserIds="
        + getAuthorizedUserIds()
        + ", authorizedGroupIds="
        + getAuthorizedGroupIds()
        + ", eventImport="
        + getEventImport()
        + ", eventIngestion="
        + getEventIngestion()
        + ", eventIndexRollover="
        + getEventIndexRollover()
        + ")";
  }
}

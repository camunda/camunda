/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import java.time.OffsetDateTime;
import java.util.Optional;

public class SettingsDto {

  private Boolean sharingEnabled;

  private OffsetDateTime lastModified;

  public SettingsDto(final Boolean sharingEnabled, final OffsetDateTime lastModified) {
    this.sharingEnabled = sharingEnabled;
    this.lastModified = lastModified;
  }

  private SettingsDto() {}

  public Optional<Boolean> getSharingEnabled() {
    return Optional.ofNullable(sharingEnabled);
  }

  public void setSharingEnabled(final Boolean sharingEnabled) {
    this.sharingEnabled = sharingEnabled;
  }

  public OffsetDateTime getLastModified() {
    return lastModified;
  }

  public void setLastModified(final OffsetDateTime lastModified) {
    this.lastModified = lastModified;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof SettingsDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $sharingEnabled = getSharingEnabled();
    result = result * PRIME + ($sharingEnabled == null ? 43 : $sharingEnabled.hashCode());
    final Object $lastModified = getLastModified();
    result = result * PRIME + ($lastModified == null ? 43 : $lastModified.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof SettingsDto)) {
      return false;
    }
    final SettingsDto other = (SettingsDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$sharingEnabled = getSharingEnabled();
    final Object other$sharingEnabled = other.getSharingEnabled();
    if (this$sharingEnabled == null
        ? other$sharingEnabled != null
        : !this$sharingEnabled.equals(other$sharingEnabled)) {
      return false;
    }
    final Object this$lastModified = getLastModified();
    final Object other$lastModified = other.getLastModified();
    if (this$lastModified == null
        ? other$lastModified != null
        : !this$lastModified.equals(other$lastModified)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "SettingsDto(sharingEnabled="
        + getSharingEnabled()
        + ", lastModified="
        + getLastModified()
        + ")";
  }

  public static SettingsDtoBuilder builder() {
    return new SettingsDtoBuilder();
  }

  public static class SettingsDtoBuilder {

    private Boolean sharingEnabled;
    private OffsetDateTime lastModified;

    SettingsDtoBuilder() {}

    public SettingsDtoBuilder sharingEnabled(final Boolean sharingEnabled) {
      this.sharingEnabled = sharingEnabled;
      return this;
    }

    public SettingsDtoBuilder lastModified(final OffsetDateTime lastModified) {
      this.lastModified = lastModified;
      return this;
    }

    public SettingsDto build() {
      return new SettingsDto(sharingEnabled, lastModified);
    }

    @Override
    public String toString() {
      return "SettingsDto.SettingsDtoBuilder(sharingEnabled="
          + sharingEnabled
          + ", lastModified="
          + lastModified
          + ")";
    }
  }

  public enum Fields {
    sharingEnabled,
    lastModified
  }
}

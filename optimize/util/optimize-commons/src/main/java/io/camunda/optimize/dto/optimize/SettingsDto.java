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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

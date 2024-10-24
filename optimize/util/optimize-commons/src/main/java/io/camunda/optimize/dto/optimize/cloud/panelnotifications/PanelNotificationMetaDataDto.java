/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud.panelnotifications;

public final class PanelNotificationMetaDataDto {

  private final String identifier;
  private final String[] permissions;
  private final String href;
  private final String label;

  private PanelNotificationMetaDataDto(
      final String identifier, final String[] permissions, final String href, final String label) {
    this.identifier = identifier;
    this.permissions = permissions;
    this.href = href;
    this.label = label;
  }

  public String getIdentifier() {
    return identifier;
  }

  public String[] getPermissions() {
    return permissions;
  }

  public String getHref() {
    return href;
  }

  public String getLabel() {
    return label;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PanelNotificationMetaDataDto;
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
    return "PanelNotificationMetaDataDto(identifier="
        + getIdentifier()
        + ", permissions="
        + java.util.Arrays.deepToString(getPermissions())
        + ", href="
        + getHref()
        + ", label="
        + getLabel()
        + ")";
  }

  public static PanelNotificationMetaDataDtoBuilder builder() {
    return new PanelNotificationMetaDataDtoBuilder();
  }

  public static class PanelNotificationMetaDataDtoBuilder {

    private String identifier;
    private String[] permissions;
    private String href;
    private String label;

    PanelNotificationMetaDataDtoBuilder() {}

    public PanelNotificationMetaDataDtoBuilder identifier(final String identifier) {
      this.identifier = identifier;
      return this;
    }

    public PanelNotificationMetaDataDtoBuilder permissions(final String[] permissions) {
      this.permissions = permissions;
      return this;
    }

    public PanelNotificationMetaDataDtoBuilder href(final String href) {
      this.href = href;
      return this;
    }

    public PanelNotificationMetaDataDtoBuilder label(final String label) {
      this.label = label;
      return this;
    }

    public PanelNotificationMetaDataDto build() {
      return new PanelNotificationMetaDataDto(identifier, permissions, href, label);
    }

    @Override
    public String toString() {
      return "PanelNotificationMetaDataDto.PanelNotificationMetaDataDtoBuilder(identifier="
          + identifier
          + ", permissions="
          + java.util.Arrays.deepToString(permissions)
          + ", href="
          + href
          + ", label="
          + label
          + ")";
    }
  }
}

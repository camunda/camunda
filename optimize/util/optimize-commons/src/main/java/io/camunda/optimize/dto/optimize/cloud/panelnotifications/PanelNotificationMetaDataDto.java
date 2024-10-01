/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud.panelnotifications;

public class PanelNotificationMetaDataDto {

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
    final int PRIME = 59;
    int result = 1;
    final Object $identifier = getIdentifier();
    result = result * PRIME + ($identifier == null ? 43 : $identifier.hashCode());
    result = result * PRIME + java.util.Arrays.deepHashCode(getPermissions());
    final Object $href = getHref();
    result = result * PRIME + ($href == null ? 43 : $href.hashCode());
    final Object $label = getLabel();
    result = result * PRIME + ($label == null ? 43 : $label.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PanelNotificationMetaDataDto)) {
      return false;
    }
    final PanelNotificationMetaDataDto other = (PanelNotificationMetaDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$identifier = getIdentifier();
    final Object other$identifier = other.getIdentifier();
    if (this$identifier == null
        ? other$identifier != null
        : !this$identifier.equals(other$identifier)) {
      return false;
    }
    if (!java.util.Arrays.deepEquals(getPermissions(), other.getPermissions())) {
      return false;
    }
    final Object this$href = getHref();
    final Object other$href = other.getHref();
    if (this$href == null ? other$href != null : !this$href.equals(other$href)) {
      return false;
    }
    final Object this$label = getLabel();
    final Object other$label = other.getLabel();
    if (this$label == null ? other$label != null : !this$label.equals(other$label)) {
      return false;
    }
    return true;
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

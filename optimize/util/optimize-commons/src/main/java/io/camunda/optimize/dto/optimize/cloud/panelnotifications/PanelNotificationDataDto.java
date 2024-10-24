/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud.panelnotifications;

public final class PanelNotificationDataDto {

  private final String uniqueId;
  private final String source;
  private final String type;
  private final String orgId;
  private final String title;
  private final String description;
  private final PanelNotificationMetaDataDto meta;

  private PanelNotificationDataDto(
      final String uniqueId,
      final String source,
      final String type,
      final String orgId,
      final String title,
      final String description,
      final PanelNotificationMetaDataDto meta) {
    this.uniqueId = uniqueId;
    this.source = source;
    this.type = type;
    this.orgId = orgId;
    this.title = title;
    this.description = description;
    this.meta = meta;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public String getSource() {
    return source;
  }

  public String getType() {
    return type;
  }

  public String getOrgId() {
    return orgId;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public PanelNotificationMetaDataDto getMeta() {
    return meta;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PanelNotificationDataDto;
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
    return "PanelNotificationDataDto(uniqueId="
        + getUniqueId()
        + ", source="
        + getSource()
        + ", type="
        + getType()
        + ", orgId="
        + getOrgId()
        + ", title="
        + getTitle()
        + ", description="
        + getDescription()
        + ", meta="
        + getMeta()
        + ")";
  }

  public static PanelNotificationDataDtoBuilder builder() {
    return new PanelNotificationDataDtoBuilder();
  }

  public static class PanelNotificationDataDtoBuilder {

    private String uniqueId;
    private String source;
    private String type;
    private String orgId;
    private String title;
    private String description;
    private PanelNotificationMetaDataDto meta;

    PanelNotificationDataDtoBuilder() {}

    public PanelNotificationDataDtoBuilder uniqueId(final String uniqueId) {
      this.uniqueId = uniqueId;
      return this;
    }

    public PanelNotificationDataDtoBuilder source(final String source) {
      this.source = source;
      return this;
    }

    public PanelNotificationDataDtoBuilder type(final String type) {
      this.type = type;
      return this;
    }

    public PanelNotificationDataDtoBuilder orgId(final String orgId) {
      this.orgId = orgId;
      return this;
    }

    public PanelNotificationDataDtoBuilder title(final String title) {
      this.title = title;
      return this;
    }

    public PanelNotificationDataDtoBuilder description(final String description) {
      this.description = description;
      return this;
    }

    public PanelNotificationDataDtoBuilder meta(final PanelNotificationMetaDataDto meta) {
      this.meta = meta;
      return this;
    }

    public PanelNotificationDataDto build() {
      return new PanelNotificationDataDto(uniqueId, source, type, orgId, title, description, meta);
    }

    @Override
    public String toString() {
      return "PanelNotificationDataDto.PanelNotificationDataDtoBuilder(uniqueId="
          + uniqueId
          + ", source="
          + source
          + ", type="
          + type
          + ", orgId="
          + orgId
          + ", title="
          + title
          + ", description="
          + description
          + ", meta="
          + meta
          + ")";
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud.panelnotifications;

public class PanelNotificationDataDto {

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
    final int PRIME = 59;
    int result = 1;
    final Object $uniqueId = getUniqueId();
    result = result * PRIME + ($uniqueId == null ? 43 : $uniqueId.hashCode());
    final Object $source = getSource();
    result = result * PRIME + ($source == null ? 43 : $source.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $orgId = getOrgId();
    result = result * PRIME + ($orgId == null ? 43 : $orgId.hashCode());
    final Object $title = getTitle();
    result = result * PRIME + ($title == null ? 43 : $title.hashCode());
    final Object $description = getDescription();
    result = result * PRIME + ($description == null ? 43 : $description.hashCode());
    final Object $meta = getMeta();
    result = result * PRIME + ($meta == null ? 43 : $meta.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PanelNotificationDataDto)) {
      return false;
    }
    final PanelNotificationDataDto other = (PanelNotificationDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$uniqueId = getUniqueId();
    final Object other$uniqueId = other.getUniqueId();
    if (this$uniqueId == null ? other$uniqueId != null : !this$uniqueId.equals(other$uniqueId)) {
      return false;
    }
    final Object this$source = getSource();
    final Object other$source = other.getSource();
    if (this$source == null ? other$source != null : !this$source.equals(other$source)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$orgId = getOrgId();
    final Object other$orgId = other.getOrgId();
    if (this$orgId == null ? other$orgId != null : !this$orgId.equals(other$orgId)) {
      return false;
    }
    final Object this$title = getTitle();
    final Object other$title = other.getTitle();
    if (this$title == null ? other$title != null : !this$title.equals(other$title)) {
      return false;
    }
    final Object this$description = getDescription();
    final Object other$description = other.getDescription();
    if (this$description == null
        ? other$description != null
        : !this$description.equals(other$description)) {
      return false;
    }
    final Object this$meta = getMeta();
    final Object other$meta = other.getMeta();
    if (this$meta == null ? other$meta != null : !this$meta.equals(other$meta)) {
      return false;
    }
    return true;
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

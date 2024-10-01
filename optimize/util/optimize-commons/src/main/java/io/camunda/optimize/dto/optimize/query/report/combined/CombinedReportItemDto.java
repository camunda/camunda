/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.combined;

import static io.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_CONFIGURATION_COLOR;

public class CombinedReportItemDto {

  private String id;
  private String color = DEFAULT_CONFIGURATION_COLOR;

  public CombinedReportItemDto(final String id) {
    this.id = id;
  }

  public CombinedReportItemDto(final String id, final String color) {
    this.id = id;
    this.color = color;
  }

  protected CombinedReportItemDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getColor() {
    return color;
  }

  public void setColor(final String color) {
    this.color = color;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CombinedReportItemDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $color = getColor();
    result = result * PRIME + ($color == null ? 43 : $color.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CombinedReportItemDto)) {
      return false;
    }
    final CombinedReportItemDto other = (CombinedReportItemDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$color = getColor();
    final Object other$color = other.getColor();
    if (this$color == null ? other$color != null : !this$color.equals(other$color)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CombinedReportItemDto(id=" + getId() + ", color=" + getColor() + ")";
  }

  private static String $default$color() {
    return DEFAULT_CONFIGURATION_COLOR;
  }

  public static CombinedReportItemDtoBuilder builder() {
    return new CombinedReportItemDtoBuilder();
  }

  public CombinedReportItemDtoBuilder toBuilder() {
    return new CombinedReportItemDtoBuilder().id(id).color(color);
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String color = "color";
  }

  public static class CombinedReportItemDtoBuilder {

    private String id;
    private String color$value;
    private boolean color$set;

    CombinedReportItemDtoBuilder() {}

    public CombinedReportItemDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public CombinedReportItemDtoBuilder color(final String color) {
      color$value = color;
      color$set = true;
      return this;
    }

    public CombinedReportItemDto build() {
      String color$value = this.color$value;
      if (!color$set) {
        color$value = CombinedReportItemDto.$default$color();
      }
      return new CombinedReportItemDto(id, color$value);
    }

    @Override
    public String toString() {
      return "CombinedReportItemDto.CombinedReportItemDtoBuilder(id="
          + id
          + ", color$value="
          + color$value
          + ")";
    }
  }
}

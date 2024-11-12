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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "CombinedReportItemDto(id=" + getId() + ", color=" + getColor() + ")";
  }

  private static String defaultColor() {
    return DEFAULT_CONFIGURATION_COLOR;
  }

  public static CombinedReportItemDtoBuilder builder() {
    return new CombinedReportItemDtoBuilder();
  }

  public CombinedReportItemDtoBuilder toBuilder() {
    return new CombinedReportItemDtoBuilder().id(id).color(color);
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String id = "id";
    public static final String color = "color";
  }

  public static class CombinedReportItemDtoBuilder {

    private String id;
    private String colorValue;
    private boolean colorSet;

    CombinedReportItemDtoBuilder() {}

    public CombinedReportItemDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public CombinedReportItemDtoBuilder color(final String color) {
      colorValue = color;
      colorSet = true;
      return this;
    }

    public CombinedReportItemDto build() {
      String colorValue = this.colorValue;
      if (!colorSet) {
        colorValue = CombinedReportItemDto.defaultColor();
      }
      return new CombinedReportItemDto(id, colorValue);
    }

    @Override
    public String toString() {
      return "CombinedReportItemDto.CombinedReportItemDtoBuilder(id="
          + id
          + ", colorValue="
          + colorValue
          + ")";
    }
  }
}

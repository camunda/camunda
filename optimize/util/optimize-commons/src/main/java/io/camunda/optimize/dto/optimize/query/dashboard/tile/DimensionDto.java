/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.tile;

import java.util.Objects;

public class DimensionDto {

  protected int width;
  protected int height;

  public DimensionDto(final int width, final int height) {
    this.width = width;
    this.height = height;
  }

  public DimensionDto() {}

  public int getWidth() {
    return width;
  }

  public void setWidth(final int width) {
    this.width = width;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(final int height) {
    this.height = height;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DimensionDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DimensionDto that = (DimensionDto) o;
    return width == that.width && height == that.height;
  }

  @Override
  public int hashCode() {
    return Objects.hash(width, height);
  }

  @Override
  public String toString() {
    return "DimensionDto(width=" + getWidth() + ", height=" + getHeight() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String width = "width";
    public static final String height = "height";
  }
}

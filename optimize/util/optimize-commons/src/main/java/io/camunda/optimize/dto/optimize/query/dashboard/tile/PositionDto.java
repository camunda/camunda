/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.tile;

public class PositionDto {

  protected int x;
  protected int y;

  public PositionDto(final int x, final int y) {
    this.x = x;
    this.y = y;
  }

  public PositionDto() {}

  public int getX() {
    return x;
  }

  public void setX(final int x) {
    this.x = x;
  }

  public int getY() {
    return y;
  }

  public void setY(final int y) {
    this.y = y;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PositionDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + getX();
    result = result * PRIME + getY();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PositionDto)) {
      return false;
    }
    final PositionDto other = (PositionDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getX() != other.getX()) {
      return false;
    }
    if (getY() != other.getY()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "PositionDto(x=" + getX() + ", y=" + getY() + ")";
  }

  public static final class Fields {

    public static final String x = "x";
    public static final String y = "y";
  }
}

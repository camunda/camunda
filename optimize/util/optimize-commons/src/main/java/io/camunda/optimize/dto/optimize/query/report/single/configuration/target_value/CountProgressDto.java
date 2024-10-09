/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import java.util.Objects;

public class CountProgressDto {

  private String baseline = "0";
  private String target = "100";
  private Boolean isBelow = false;

  @Override
  public int hashCode() {
    return Objects.hash(baseline, target);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final CountProgressDto that)) {
      return false;
    }
    return Objects.equals(baseline, that.baseline)
        && Objects.equals(isBelow, that.isBelow)
        && Objects.equals(target, that.target);
  }

  @Override
  public String toString() {
    return "CountProgressDto(baseline="
        + getBaseline()
        + ", target="
        + getTarget()
        + ", isBelow="
        + getIsBelow()
        + ")";
  }

  public String getBaseline() {
    return baseline;
  }

  public void setBaseline(final String baseline) {
    this.baseline = baseline;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(final String target) {
    this.target = target;
  }

  public Boolean getIsBelow() {
    return isBelow;
  }

  public void setIsBelow(final Boolean isBelow) {
    this.isBelow = isBelow;
  }

  public static final class Fields {

    public static final String baseline = "baseline";
    public static final String target = "target";
    public static final String isBelow = "isBelow";
  }
}

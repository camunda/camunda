/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

public class CountProgressDto {

  private String baseline = "0";
  private String target = "100";
  private Boolean isBelow = false;

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

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String baseline = "baseline";
    public static final String target = "target";
    public static final String isBelow = "isBelow";
  }
}

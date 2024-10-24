/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

public class DurationProgressDto {

  private BaseLineDto baseline = new BaseLineDto();
  private TargetDto target = new TargetDto();

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
    return "DurationProgressDto(baseline=" + getBaseline() + ", target=" + getTarget() + ")";
  }

  public BaseLineDto getBaseline() {
    return baseline;
  }

  public void setBaseline(final BaseLineDto baseline) {
    this.baseline = baseline;
  }

  public TargetDto getTarget() {
    return target;
  }

  public void setTarget(final TargetDto target) {
    this.target = target;
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String baseline = "baseline";
    public static final String target = "target";
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import java.util.Objects;

public class DurationProgressDto {

  private BaseLineDto baseline = new BaseLineDto();
  private TargetDto target = new TargetDto();

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DurationProgressDto that = (DurationProgressDto) o;
    return Objects.equals(baseline, that.baseline) && Objects.equals(target, that.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(baseline, target);
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

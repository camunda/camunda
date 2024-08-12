/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class DurationProgressDto {

  private BaseLineDto baseline = new BaseLineDto();
  private TargetDto target = new TargetDto();

  @Override
  public int hashCode() {
    return Objects.hash(baseline, target);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final DurationProgressDto that)) {
      return false;
    }
    return Objects.equals(baseline, that.baseline) && Objects.equals(target, that.target);
  }

  public static final class Fields {

    public static final String baseline = "baseline";
    public static final String target = "target";
  }
}

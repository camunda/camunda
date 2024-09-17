/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class RelativeDateFilterStartDto {

  protected Long value;
  protected DateUnit unit;

  public RelativeDateFilterStartDto(Long value, DateUnit unit) {
    this.value = value;
    this.unit = unit;
  }

  public RelativeDateFilterStartDto() {}

  public static final class Fields {

    public static final String value = "value";
    public static final String unit = "unit";
  }
}

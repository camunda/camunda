/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MinMaxStatDto {

  /// fieldCounts: amount of values that have been taken into account for min/max fields respectively
  private long minFieldCount;
  private long maxFieldCount;

  private double min;
  private double max;

  private String minAsString;
  private String maxAsString;

  public MinMaxStatDto(final long fieldCount, final double min, final double max) {
    this.minFieldCount = fieldCount;
    this.maxFieldCount = fieldCount;
    this.min = min;
    this.max = max;
    this.minAsString = Double.toString(min);
    this.maxAsString = Double.toString(max);
  }

  public MinMaxStatDto(final long minFieldCount, final long maxFieldCount, final double min, final double max) {
    this.minFieldCount = minFieldCount;
    this.maxFieldCount = maxFieldCount;
    this.min = min;
    this.max = max;
    this.minAsString = Double.toString(min);
    this.maxAsString = Double.toString(max);
  }

  public MinMaxStatDto(final long fieldCount, final double min, final double max,
                       final String minAsString, final String maxAsString) {
    this.minFieldCount = fieldCount;
    this.maxFieldCount = fieldCount;
    this.min = min;
    this.max = max;
    this.minAsString = minAsString;
    this.maxAsString = maxAsString;
  }

}

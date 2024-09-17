/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.tile;

import lombok.Data;

@Data
public class PositionDto {

  protected int x;
  protected int y;

  public PositionDto(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public PositionDto() {}

  public static final class Fields {

    public static final String x = "x";
    public static final String y = "y";
  }
}

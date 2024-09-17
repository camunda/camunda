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
public class DimensionDto {

  protected int width;
  protected int height;

  public DimensionDto(int width, int height) {
    this.width = width;
    this.height = height;
  }

  public DimensionDto() {}

  public static final class Fields {

    public static final String width = "width";
    public static final String height = "height";
  }
}

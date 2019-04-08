/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard;

public class ReportLocationDto {

  protected String id;
  protected PositionDto position;
  protected DimensionDto dimensions;
  protected Object configuration;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public PositionDto getPosition() {
    return position;
  }

  public void setPosition(PositionDto position) {
    this.position = position;
  }

  public DimensionDto getDimensions() {
    return dimensions;
  }

  public void setDimensions(DimensionDto dimensions) {
    this.dimensions = dimensions;
  }

  public Object getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Object configuration) {
    this.configuration = configuration;
  }
}

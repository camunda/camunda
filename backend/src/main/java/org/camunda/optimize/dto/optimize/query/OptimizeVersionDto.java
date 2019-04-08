/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;


public class OptimizeVersionDto implements OptimizeDto, Serializable {

  private String optimizeVersion;

  public OptimizeVersionDto() {}

  public OptimizeVersionDto(String optimizeVersion) {
    this.optimizeVersion = optimizeVersion;
  }

  public String getOptimizeVersion() {
    return optimizeVersion;
  }

  public void setOptimizeVersion(String optimizeVersion) {
    this.optimizeVersion = optimizeVersion;
  }
}

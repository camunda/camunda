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

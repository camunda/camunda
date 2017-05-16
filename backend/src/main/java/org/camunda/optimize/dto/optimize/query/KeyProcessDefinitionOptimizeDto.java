package org.camunda.optimize.dto.optimize.query;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;

public class KeyProcessDefinitionOptimizeDto implements Serializable, OptimizeDto {

  protected String key;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }
}

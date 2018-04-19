package org.camunda.optimize.dto.optimize.query.variable;

import org.camunda.optimize.dto.optimize.OptimizeDto;

public class ProcessInstanceId implements OptimizeDto {

  protected String id;

  public ProcessInstanceId(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}

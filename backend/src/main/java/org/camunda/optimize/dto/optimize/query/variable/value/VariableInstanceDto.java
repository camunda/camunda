package org.camunda.optimize.dto.optimize.query.variable.value;

import org.camunda.optimize.dto.optimize.OptimizeDto;

public abstract class VariableInstanceDto<T> implements OptimizeDto {

  private String id;
  private String name;
  private String type;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public abstract T getValue();

}

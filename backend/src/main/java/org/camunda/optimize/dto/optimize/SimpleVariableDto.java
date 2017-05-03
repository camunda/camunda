package org.camunda.optimize.dto.optimize;

public class SimpleVariableDto implements OptimizeDto {

  private String id;
  private String name;
  private String type;
  private VariableValueDto value;

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

  public VariableValueDto getValue() {
    return value;
  }

  public void setValue(VariableValueDto value) {
    this.value = value;
  }
}

package org.camunda.optimize.data.generation.generators.client.dto;

public class VariableValue {

  Object value;
  String type;

  public VariableValue(Object value, String type) {
    this.value = value;
    this.type = type;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }
}

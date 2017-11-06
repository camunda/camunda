package org.camunda.optimize.dto.optimize.query.report.result.raw;

public class RawDataVariableDto {

  protected String name;
  protected Object value;

  public RawDataVariableDto() {
    super();
  }

  public RawDataVariableDto(String name, Object value) {
    this.name = name;
    this.value = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }
}

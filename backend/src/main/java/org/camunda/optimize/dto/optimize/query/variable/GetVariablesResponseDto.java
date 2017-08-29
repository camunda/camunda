package org.camunda.optimize.dto.optimize.query.variable;

import java.util.List;

public class GetVariablesResponseDto {

  protected String name;
  protected String type;
  protected List<String> values;
  protected boolean valuesAreComplete;

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

  public List<String> getValues() {
    return values;
  }

  public void setValues(List<String> values) {
    this.values = values;
  }

  public boolean isValuesAreComplete() {
    return valuesAreComplete;
  }

  public void setValuesAreComplete(boolean valuesAreComplete) {
    this.valuesAreComplete = valuesAreComplete;
  }
}

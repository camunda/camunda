package org.camunda.optimize.dto.optimize.query.report;

public class ViewDto {

  protected String operation;
  protected String entity;
  protected String property;

  public ViewDto() {
    super();
  }

  public ViewDto(String operation) {
    this.operation = operation;
  }

  public String getOperation() {
    return operation;
  }

  public void setOperation(String operation) {
    this.operation = operation;
  }

  public String getEntity() {
    return entity;
  }

  public void setEntity(String entity) {
    this.entity = entity;
  }

  public String getProperty() {
    return property;
  }

  public void setProperty(String property) {
    this.property = property;
  }
}

package org.camunda.optimize.dto.optimize.query.report;

public class ViewDto {

  protected String operation;
  protected String entity;

  public ViewDto() {
    super();
  }

  public ViewDto(String operation, String entity) {
    this.operation = operation;
    this.entity = entity;
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
}

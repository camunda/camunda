package org.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

public class ViewDto implements Combinable {

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

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ViewDto)) {
      return false;
    }
    ViewDto viewDto = (ViewDto) o;
    // note: different view operations are okay, since users might want to
    // compare the results of those in a combined report.
    return Objects.equals(entity, viewDto.entity) &&
      Objects.equals(property, viewDto.property);
  }

  @JsonIgnore
  public String createCommandKey() {
    String separator = "-";
    return operation + separator + entity + separator + property;
  }

  @Override
  public String toString() {
    return "ViewDto{" +
      "operation='" + operation + '\'' +
      ", entity='" + entity + '\'' +
      ", property='" + property + '\'' +
      '}';
  }
}

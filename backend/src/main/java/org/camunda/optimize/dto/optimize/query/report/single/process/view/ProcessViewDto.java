package org.camunda.optimize.dto.optimize.query.report.single.process.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.optimize.dto.optimize.query.report.Combinable;

import java.util.Objects;

public class ProcessViewDto implements Combinable {

  protected ProcessViewOperation operation;
  protected ProcessViewEntity entity;
  protected ProcessViewProperty property;

  public ProcessViewDto() {
    super();
  }

  public ProcessViewDto(ProcessViewOperation operation) {
    this.operation = operation;
  }

  public ProcessViewOperation getOperation() {
    return operation;
  }

  public void setOperation(ProcessViewOperation operation) {
    this.operation = operation;
  }

  public ProcessViewEntity getEntity() {
    return entity;
  }

  public void setEntity(ProcessViewEntity entity) {
    this.entity = entity;
  }

  public ProcessViewProperty getProperty() {
    return property;
  }

  public void setProperty(ProcessViewProperty property) {
    this.property = property;
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessViewDto)) {
      return false;
    }
    ProcessViewDto viewDto = (ProcessViewDto) o;
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
    return "ProcessViewDto{" +
      "operation='" + operation + '\'' +
      ", entity='" + entity + '\'' +
      ", property='" + property + '\'' +
      '}';
  }
}

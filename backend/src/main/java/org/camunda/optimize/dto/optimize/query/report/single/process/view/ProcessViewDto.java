package org.camunda.optimize.dto.optimize.query.report.single.process.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.optimize.dto.optimize.query.report.Combinable;

import java.util.Objects;

public class ProcessViewDto implements Combinable {

  protected ProcessViewEntity entity;
  protected ProcessViewProperty property;

  public ProcessViewDto() {
    super();
  }

  public ProcessViewDto(ProcessViewProperty property) {
    this(null, property);
  }

  public ProcessViewDto(final ProcessViewEntity entity,
                        final ProcessViewProperty property) {
    this.entity = entity;
    this.property = property;
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
    return entity + separator + property;
  }

  @Override
  public String toString() {
    return "ProcessViewDto{" +
      ", entity='" + entity + '\'' +
      ", property='" + property + '\'' +
      '}';
  }
}

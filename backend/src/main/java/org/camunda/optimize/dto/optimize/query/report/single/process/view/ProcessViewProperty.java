package org.camunda.optimize.dto.optimize.query.report.single.process.view;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_FREQUENCY_PROPERTY;

public enum ProcessViewProperty {
  FREQUENCY(VIEW_FREQUENCY_PROPERTY),
  DURATION(VIEW_DURATION_PROPERTY),
  ;

  private final String id;

  ProcessViewProperty(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }
}

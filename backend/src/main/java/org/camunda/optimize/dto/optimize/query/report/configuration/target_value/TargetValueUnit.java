package org.camunda.optimize.dto.optimize.query.report.configuration.target_value;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TargetValueUnit {

  MILLIS("millis"),
  SECONDS("seconds"),
  MINUTES("minutes"),
  HOURS("hours"),
  DAYS("days"),
  WEEKS("weeks"),
  MONTHS("months"),
  YEARS("years"),
  ;

  private final String id;

  TargetValueUnit(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }
}

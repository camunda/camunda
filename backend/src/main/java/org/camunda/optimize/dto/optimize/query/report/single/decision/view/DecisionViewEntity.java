package org.camunda.optimize.dto.optimize.query.report.single.decision.view;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_DECISION_INSTANCE_ENTITY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_DECISION_MATCHED_RULE_ENTITY;

public enum DecisionViewEntity {
  DECISION_INSTANCE(VIEW_DECISION_INSTANCE_ENTITY),
  MATCHED_RULE(VIEW_DECISION_MATCHED_RULE_ENTITY),
  ;

  private final String id;

  DecisionViewEntity(final String id) {
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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.plan.decision;

import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.FREQUENCY;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;

public enum DecisionView {
  DECISION_VIEW_INSTANCE_FREQUENCY(new DecisionViewDto(FREQUENCY)),
  DECISION_VIEW_RAW_DATA(new DecisionViewDto(ViewProperty.RAW_DATA));

  private final DecisionViewDto decisionViewDto;

  private DecisionView(final DecisionViewDto decisionViewDto) {
    this.decisionViewDto = decisionViewDto;
  }

  public DecisionViewDto getDecisionViewDto() {
    return this.decisionViewDto;
  }
}

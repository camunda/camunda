/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.util;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;

public class DecisionViewDtoCreator {

  public static DecisionViewDto createDecisionRawDataView() {
    return new DecisionViewDto(ViewProperty.RAW_DATA);
  }

  public static DecisionViewDto createCountFrequencyView() {
    return new DecisionViewDto(ViewProperty.FREQUENCY);
  }
}

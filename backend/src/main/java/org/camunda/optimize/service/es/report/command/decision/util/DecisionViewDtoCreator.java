/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.decision.util;

import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;

public class DecisionViewDtoCreator {

  public static DecisionViewDto createDecisionRawDataView() {
    return new DecisionViewDto(ViewProperty.RAW_DATA);
  }

  public static DecisionViewDto createCountFrequencyView() {
    return new DecisionViewDto(ViewProperty.FREQUENCY);
  }

}

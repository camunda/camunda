/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.analysis;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class BranchAnalysisResponseDto {
  /**
   * The end event the branch analysis is referred to.
   */
  protected String endEvent;

  /**
   * The total amount of tokens that went from the gateway to the end event.
   */
  protected Long total;

  /**
   * All branch analysis information of the flow nodes from the gateway to the end event.
   */
  protected Map<String, BranchAnalysisOutcomeDto> followingNodes = new HashMap<>();
}

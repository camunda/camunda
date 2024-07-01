/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class BranchAnalysisResponseDto {
  /** The end event the branch analysis is referred to. */
  protected String endEvent;

  /** The total amount of tokens that went from the gateway to the end event. */
  protected Long total;

  /** All branch analysis information of the flow nodes from the gateway to the end event. */
  protected Map<String, BranchAnalysisOutcomeDto> followingNodes = new HashMap<>();
}

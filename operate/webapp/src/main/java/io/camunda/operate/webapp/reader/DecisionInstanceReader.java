/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.util.Tuple;
import io.camunda.operate.webapp.rest.dto.dmn.DRDDataEntryDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListResponseDto;
import java.util.List;
import java.util.Map;

public interface DecisionInstanceReader {
  DecisionInstanceDto getDecisionInstance(String decisionInstanceId);

  DecisionInstanceListResponseDto queryDecisionInstances(DecisionInstanceListRequestDto request);

  Map<String, List<DRDDataEntryDto>> getDecisionInstanceDRDData(String decisionInstanceId);

  Tuple<String, String> getCalledDecisionInstanceAndDefinitionByFlowNodeInstanceId(
      final String flowNodeInstanceId);
}

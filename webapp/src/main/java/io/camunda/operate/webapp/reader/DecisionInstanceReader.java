/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.reader;

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
}

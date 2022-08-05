/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.util.Map;

@Data
@AllArgsConstructor
public class LastKpiEvaluationResultsDto implements OptimizeDto {
  final Map<String, String> reportIdToValue;
}

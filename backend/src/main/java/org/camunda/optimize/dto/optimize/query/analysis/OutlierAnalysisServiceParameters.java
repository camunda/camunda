/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.analysis;

import java.time.ZoneId;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OutlierAnalysisServiceParameters<T extends ProcessDefinitionParametersDto> {

  private T processDefinitionParametersDto;
  private ZoneId zoneId;
  private String userId;
}

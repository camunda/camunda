/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import java.time.ZoneId;
import lombok.Data;

@Data
public class OutlierAnalysisServiceParameters<T extends ProcessDefinitionParametersDto> {

  private T processDefinitionParametersDto;
  private ZoneId zoneId;
  private String userId;

  public OutlierAnalysisServiceParameters(
      T processDefinitionParametersDto, ZoneId zoneId, String userId) {
    this.processDefinitionParametersDto = processDefinitionParametersDto;
    this.zoneId = zoneId;
    this.userId = userId;
  }
}

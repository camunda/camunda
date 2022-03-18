/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.OptimizeDto;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class AggregationDto implements OptimizeDto {

  public AggregationDto(final AggregationType aggregationType) {
    this.type = aggregationType;
  }

  AggregationType type;
  Double value;

}

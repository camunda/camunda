/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import lombok.Data;

import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;

@Data
public class ProcessVariableReportValuesRequestDto {

  private List<String> reportIds;
  private String name;
  private VariableType type;
  private String valueFilter;
  private Integer resultOffset = 0;
  private Integer numResults = MAX_RESPONSE_SIZE_LIMIT;

}

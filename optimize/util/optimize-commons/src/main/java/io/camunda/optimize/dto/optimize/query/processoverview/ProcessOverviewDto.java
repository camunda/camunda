/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessOverviewDto implements OptimizeDto {

  private String owner;
  private String processDefinitionKey;
  private ProcessDigestDto digest;
  private Map<String, String> lastKpiEvaluationResults;

  public static final class Fields {

    public static final String owner = "owner";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String digest = "digest";
    public static final String lastKpiEvaluationResults = "lastKpiEvaluationResults";
  }
}

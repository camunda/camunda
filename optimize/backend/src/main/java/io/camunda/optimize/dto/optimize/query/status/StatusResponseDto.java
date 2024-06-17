/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.status;

import java.util.Map;
import lombok.Data;

@Data
public class StatusResponseDto {
  protected Map<String, EngineStatusDto> engineStatus;

  /** True if Optimize is connected to the Elasticsearch, false otherwise. */
  protected boolean isConnectedToElasticsearch;
}

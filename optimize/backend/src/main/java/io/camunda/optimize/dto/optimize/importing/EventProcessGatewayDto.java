/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventProcessGatewayDto {

  private String id;
  private String type;
  private List<String> previousNodeIds;
  private List<String> nextNodeIds;
}

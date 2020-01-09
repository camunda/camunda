/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EventProcessGatewayDto {

  private String id;
  private String type;
  private List<String> previousNodeIds;
  private List<String> nextNodeIds;

}

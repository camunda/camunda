/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HiddenNodesDto {

  private boolean active = false;
  private List<String> keys = new ArrayList<>();
}

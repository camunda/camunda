/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.status;

import lombok.Data;

import java.util.Map;

@Data
public class StatusWithProgressDto {

  protected ConnectionStatusDto connectionStatus;
  protected Map<String, Boolean> isImporting;
}

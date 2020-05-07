/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class DeploymentEngineDto implements Serializable, EngineDto {

  private String id;
  private String name;
  private String source;
  private OffsetDateTime deploymentTime;
  private String tenantId;
}

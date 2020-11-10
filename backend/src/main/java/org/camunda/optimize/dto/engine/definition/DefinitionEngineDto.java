/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine.definition;

import lombok.Data;
import org.camunda.optimize.dto.engine.EngineDto;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class DefinitionEngineDto implements Serializable, EngineDto {

  protected String id;
  protected String deploymentId;

  /**
   * This property is not available directly from the engine. Instead, the deployment time
   * is fetched using the deployment id and then later on added to this dto. This allows
   * us to perform the process definition import based on the timestamp.
   */
  protected OffsetDateTime deploymentTime;
  /**
   * This property is not available directly from the engine. Instead, we determine whether it is
   * deleted based on whether or not another definition with a newer deployment time is imported
   * with the same key/version/tenant
   */
  protected boolean deleted;
}

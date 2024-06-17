/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine.definition;

import io.camunda.optimize.dto.engine.TenantSpecificEngineDto;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.Data;

@Data
public class DefinitionEngineDto implements Serializable, TenantSpecificEngineDto {

  protected String id;
  protected String deploymentId;
  protected String tenantId;

  /**
   * This property is not available directly from the engine. Instead, the deployment time is
   * fetched using the deployment id and then later on added to this dto. This allows us to perform
   * the process definition import based on the timestamp.
   */
  protected OffsetDateTime deploymentTime;

  /**
   * This property is not available directly from the engine. Instead, we determine whether it is
   * deleted based on whether or not another definition with a newer deployment time is imported
   * with the same key/version/tenant
   */
  protected boolean deleted;

  @Override
  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }
}

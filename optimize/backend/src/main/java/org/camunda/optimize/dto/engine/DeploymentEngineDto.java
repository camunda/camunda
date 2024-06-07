/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.engine;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.Data;

@Data
public class DeploymentEngineDto implements Serializable, TenantSpecificEngineDto {

  private String id;
  private String name;
  private String source;
  private OffsetDateTime deploymentTime;
  private String tenantId;

  @Override
  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }
}

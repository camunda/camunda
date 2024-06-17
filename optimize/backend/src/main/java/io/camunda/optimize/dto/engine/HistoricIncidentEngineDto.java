/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.Data;

@Data
public class HistoricIncidentEngineDto implements TenantSpecificEngineDto {

  protected String id;
  protected String processDefinitionKey;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected String executionId;
  protected String rootProcessInstanceId;
  protected OffsetDateTime createTime;
  protected OffsetDateTime endTime;
  protected OffsetDateTime removalTime;
  protected String incidentType;
  protected String activityId;
  protected String failedActivityId;
  protected String causeIncidentId;
  protected String rootCauseIncidentId;
  protected String incidentMessage;
  protected String tenantId;
  protected String jobDefinitionId;
  protected boolean open;
  protected boolean deleted;
  protected boolean resolved;

  @Override
  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }
}

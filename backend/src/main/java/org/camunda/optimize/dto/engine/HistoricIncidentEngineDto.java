/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.engine;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Optional;

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

  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }

}

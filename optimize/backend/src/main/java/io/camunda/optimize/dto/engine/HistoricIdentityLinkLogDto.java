/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class HistoricIdentityLinkLogDto implements TenantSpecificEngineDto {

  private String id;
  private OffsetDateTime time;
  private String type;
  private String userId;
  private String groupId;
  private String taskId; // equivalent to FlowNodeInstanceDto.userTaskInstanceId
  private String processDefinitionId;
  private String processDefinitionKey;
  private String processInstanceId;
  private String operationType;
  private String assignerId;
  private String tenantId;
  private OffsetDateTime removalTime;
  private String rootProcessInstanceId;

  @Override
  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }
}

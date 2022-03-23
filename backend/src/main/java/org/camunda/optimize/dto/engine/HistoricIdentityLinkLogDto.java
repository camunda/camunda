/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Optional;


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

  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }
}

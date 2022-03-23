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
public class HistoricProcessInstanceDto implements TenantSpecificEngineDto {
  protected String id;
  protected String businessKey;
  protected String processDefinitionId;
  protected String processDefinitionKey;
  protected Integer processDefinitionVersion;
  protected String processDefinitionName;
  protected OffsetDateTime startTime;
  protected OffsetDateTime endTime;
  protected Long durationInMillis;
  protected String startUserId;
  protected String startActivityId;
  protected String deleteReason;
  protected String superProcessInstanceId;
  protected String superCaseInstanceId;
  protected String caseInstanceId;
  protected String tenantId;
  protected String state;

  public String getProcessDefinitionVersionAsString() {
    return processDefinitionVersion != null ? processDefinitionVersion.toString() : null;
  }

  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }

}

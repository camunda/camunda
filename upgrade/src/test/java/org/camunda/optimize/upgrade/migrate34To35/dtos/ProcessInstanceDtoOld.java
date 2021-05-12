/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate34To35.dtos;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@FieldNameConstants
public class ProcessInstanceDtoOld implements OptimizeDto {
  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String processDefinitionId;
  private String processInstanceId;
  private String businessKey;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Long duration; // duration in ms
  private String state;
  @Builder.Default
  private List<FlowNodeInstanceDtoOld> events = new ArrayList<>();
  @Builder.Default
  private List<UserTaskInstanceDtoOld> userTasks = new ArrayList<>();
  @Builder.Default
  private List<SimpleProcessVariableDto> variables = new ArrayList<>();
  @Builder.Default
  private List<IncidentDto> incidents = new ArrayList<>();
  private String engine;
  private String tenantId;

}

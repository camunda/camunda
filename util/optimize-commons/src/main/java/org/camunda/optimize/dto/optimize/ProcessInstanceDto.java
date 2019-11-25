/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.event.SimpleEventDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
@FieldNameConstants
public class ProcessInstanceDto implements OptimizeDto {

  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String processDefinitionId;
  private String processInstanceId;
  private String businessKey;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Long duration; // duration in ms
  private String state;
  private List<SimpleEventDto> events = new ArrayList<>();
  private List<UserTaskInstanceDto> userTasks = new ArrayList<>();
  private List<SimpleProcessVariableDto> variables = new ArrayList<>();
  private String engine;
  private String tenantId;

  public ProcessInstanceDto(final String processDefinitionKey, final String processDefinitionVersion,
                            final String processDefinitionId, final String processInstanceId,
                            final String businessKey, final OffsetDateTime startDate, final OffsetDateTime endDate,
                            final Long duration, final String state, final String engine, final String tenantId) {
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionVersion = processDefinitionVersion;
    this.processDefinitionId = processDefinitionId;
    this.processInstanceId = processInstanceId;
    this.businessKey = businessKey;
    this.startDate = startDate;
    this.endDate = endDate;
    this.duration = duration;
    this.state = state;
    this.engine = engine;
    this.tenantId = tenantId;
  }
}